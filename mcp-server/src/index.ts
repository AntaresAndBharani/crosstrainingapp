import { fileURLToPath } from "node:url";
import cors from "cors";
import express, { type Express, type NextFunction, type Request, type Response } from "express";
import rateLimit from "express-rate-limit";
import { initializeApp, getApps } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { SSEServerTransport } from "@modelcontextprotocol/sdk/server/sse.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { createCrossTrainingMcpServer } from "./mcp.js";
import { OAuthServer, type OAuthConfig } from "./oauth.js";
import {
  FirestoreBackingStore,
  type RawDocumentBackingStore,
  type UserContext,
  WorkoutDomainService,
} from "./store.js";

export interface CreateAppOptions {
  backingStore: RawDocumentBackingStore;
  oauthConfig: OAuthConfig;
  /** Per-authenticated-user MCP request ceiling per minute (defaults to 180). */
  rateLimitMaxPerMinute?: number;
  /** Per-IP unauthenticated/proxy request ceiling per minute (defaults to max(rateLimitMaxPerMinute * 3, 300)). */
  ipRateLimitMaxPerMinute?: number;
}

/**
 * Normalizes `Accept` headers on stateless JSON-mode `POST /mcp` requests so MCP clients
 * that send `Accept: application/json` or wildcard Accept headers (omitting `text/event-stream`)
 * are not rejected with HTTP 406 by `@modelcontextprotocol/sdk`'s StreamableHTTPServerTransport.
 */
function normalizeMcpAcceptHeader(req: Request, _res: Response, next: NextFunction): void {
  const accept = req.headers.accept ?? "";
  const hasJson = accept.includes("application/json");
  const hasSse = accept.includes("text/event-stream");
  if (!hasJson || !hasSse) {
    req.headers.accept = "application/json, text/event-stream";
  }
  next();
}

export function createMcpExpressApp(options: CreateAppOptions): Express {
  const app = express();
  app.set("trust proxy", 1);

  app.use(
    cors({
      origin: "*",
      methods: ["GET", "HEAD", "POST", "DELETE", "OPTIONS"],
      allowedHeaders: [
        "Content-Type",
        "Authorization",
        "Accept",
        "Mcp-Session-Id",
        "Mcp-Protocol-Version",
        "Last-Event-ID",
        "X-App-Env",
      ],
      exposedHeaders: [
        "Mcp-Session-Id",
        "Mcp-Protocol-Version",
        "WWW-Authenticate",
        "Retry-After",
      ],
    })
  );

  const userMaxPerMin = options.rateLimitMaxPerMinute ?? 180;
  const ipMaxPerMin =
    options.ipRateLimitMaxPerMinute ?? Math.max(userMaxPerMin * 3, 300);

  // Layer 4a Anti-Bill Protection: High-ceiling IP guard for unauthenticated endpoints & DDoS safety,
  // sized so shared cloud AI egress proxies (e.g. Google 66.249.83.*) are not prematurely throttled.
  const ipLimiter = rateLimit({
    windowMs: 60 * 1000,
    max: ipMaxPerMin,
    standardHeaders: true,
    legacyHeaders: false,
    skip: (req) => req.path === "/health" || req.path === "/healthz",
    handler: (_req, res) => {
      res.setHeader("Retry-After", "60");
      res.status(429).json({
        error: "too_many_requests",
        error_description: `IP rate limit exceeded (max ${ipMaxPerMin} requests/minute).`,
      });
    },
  });
  app.use(ipLimiter);

  // Layer 4b Per-User Quota: Authenticated MCP traffic is isolated per `env:uid` rather than IP.
  const userLimiter = rateLimit({
    windowMs: 60 * 1000,
    max: userMaxPerMin,
    standardHeaders: true,
    legacyHeaders: false,
    keyGenerator: (req, res) => {
      const user = res.locals?.user as UserContext | undefined;
      if (user?.uid) {
        return `user:${user.env}:${user.uid}`;
      }
      return `ip:${req.ip ?? "unknown"}`;
    },
    handler: (_req, res) => {
      res.setHeader("Retry-After", "60");
      res.status(429).json({
        error: "too_many_requests",
        error_description: `User rate limit exceeded (max ${userMaxPerMin} requests/minute).`,
      });
    },
  });

  const domain = new WorkoutDomainService(options.backingStore);
  const oauth = new OAuthServer(options.oauthConfig);

  // Health check endpoints (unauthenticated for Cloud Run startup/liveness probes)
  app.get(["/health", "/healthz"], (_req, res) => {
    res.json({
      status: "ok",
      service: "crosstraining-mcp-server",
      version: "1.1.0",
    });
  });

  // Mount OAuth 2.1 + RFC 7591 DCR endpoints
  app.use(oauth.createRouter());

  // ==========================================================================
  // Streamable HTTP Transport (/mcp) — Used by Gemini, Claude, and ChatGPT
  // ==========================================================================

  app.post(
    "/mcp",
    oauth.requireAuth,
    userLimiter,
    normalizeMcpAcceptHeader,
    express.json({ limit: "1mb" }),
    async (req: Request, res: Response) => {
      const user = res.locals.user as UserContext;
      const protocolVersion = req.headers["mcp-protocol-version"];
      if (typeof protocolVersion === "string" && protocolVersion.length <= 32) {
        res.setHeader("Mcp-Protocol-Version", protocolVersion);
      }
      try {
        const server = createCrossTrainingMcpServer(domain, () => user);
        const transport = new StreamableHTTPServerTransport({
          sessionIdGenerator: undefined, // Stateless per-request JSON-RPC mode for Cloud Run scale-to-zero
          enableJsonResponse: true,
        });
        res.on("close", () => {
          void transport.close();
          void server.close();
        });
        await server.connect(transport);
        await transport.handleRequest(req, res, req.body);
      } catch (err) {
        if (!res.headersSent) {
          res.status(500).json({
            jsonrpc: "2.0",
            error: {
              code: -32603,
              message: err instanceof Error ? err.message : "Internal MCP error",
            },
            id: null,
          });
        }
      }
    }
  );

  app.head("/mcp", oauth.requireAuth, userLimiter, (_req: Request, res: Response) => {
    res.setHeader("Allow", "POST, DELETE");
    res.status(200).end();
  });

  app.get("/mcp", oauth.requireAuth, userLimiter, (_req: Request, res: Response) => {
    res.setHeader("Allow", "POST, DELETE");
    res.status(405).json({
      jsonrpc: "2.0",
      error: {
        code: -32000,
        message: "Method not allowed in stateless Streamable HTTP mode; use POST /mcp or GET /sse",
      },
      id: null,
    });
  });

  app.delete("/mcp", oauth.requireAuth, userLimiter, (_req: Request, res: Response) => {
    res.status(204).end();
  });

  // ==========================================================================
  // Legacy SSE Transport (/sse + /messages) — For SSE-based MCP Clients
  // ==========================================================================

  const sseTransports = new Map<
    string,
    { transport: SSEServerTransport; user: UserContext }
  >();

  app.get("/sse", oauth.requireAuth, userLimiter, async (_req: Request, res: Response) => {
    const user = res.locals.user as UserContext;
    const transport = new SSEServerTransport("/messages", res);
    const server = createCrossTrainingMcpServer(domain, () => user);

    sseTransports.set(transport.sessionId, { transport, user });
    res.on("close", () => {
      sseTransports.delete(transport.sessionId);
      void server.close();
    });

    await server.connect(transport);
  });

  app.post(
    "/messages",
    oauth.requireAuth,
    userLimiter,
    express.json({ limit: "1mb" }),
    async (req: Request, res: Response) => {
      const sessionId = String(req.query.sessionId || "");
      const entry = sseTransports.get(sessionId);
      if (!entry) {
        res.status(404).json({ error: "Unknown SSE sessionId" });
        return;
      }
      // Bind the SSE session to the user who opened it.
      const caller = res.locals.user as UserContext;
      if (caller.uid !== entry.user.uid || caller.env !== entry.user.env) {
        res.status(403).json({ error: "SSE session belongs to a different user" });
        return;
      }
      await entry.transport.handlePostMessage(req, res, req.body);
    }
  );

  return app;
}

async function main() {
  const projectId = process.env.FIREBASE_PROJECT_ID || "virgymia-c2cc7";
  if (getApps().length === 0) {
    initializeApp({ projectId });
  }
  const db = getFirestore();
  const backingStore = new FirestoreBackingStore(db);

  // Support local --stdio mode for Antigravity / Claude Code direct workspace mounting
  if (process.argv.includes("--stdio")) {
    const uid = process.env.MCP_DEFAULT_UID || "";
    const env = process.env.MCP_DEFAULT_ENV === "snapshot" ? "snapshot" : "production";
    if (!uid) {
      console.error("Error: MCP_DEFAULT_UID environment variable is required in --stdio mode.");
      process.exit(1);
    }
    const domain = new WorkoutDomainService(backingStore);
    const server = createCrossTrainingMcpServer(domain, () => ({ uid, env }));
    const transport = new StdioServerTransport();
    await server.connect(transport);
    return;
  }

  // The signing secret MUST come from the environment (Secret Manager on Cloud Run). Never derive it
  // from public values such as the project ID — anyone could then forge Bearer tokens for any uid.
  const jwtSecret = process.env.MCP_JWT_SECRET || "";
  if (jwtSecret.length < 32) {
    console.error(
      "Error: MCP_JWT_SECRET must be set to a random value of at least 32 characters " +
        "(e.g. `openssl rand -base64 48`)."
    );
    process.exit(1);
  }

  const app = createMcpExpressApp({
    backingStore,
    rateLimitMaxPerMinute: Number(process.env.MCP_RATE_LIMIT_PER_MIN || "180"),
    oauthConfig: {
      issuerUrl: process.env.MCP_ISSUER_URL,
      jwtSecret,
      firebaseApiKey: process.env.FIREBASE_WEB_API_KEY,
      firebaseProjectId: projectId,
      devBearerToken: process.env.MCP_DEV_BEARER_TOKEN,
      devDefaultUid: process.env.MCP_DEFAULT_UID,
      verifyFirebaseIdToken: async (idToken: string) => {
        const decoded = await getAuth().verifyIdToken(idToken);
        return { uid: decoded.uid, email: decoded.email };
      },
    },
  });

  const port = Number(process.env.PORT || "8080");
  app.listen(port, "0.0.0.0", () => {
    console.log(`CrossTraining Remote MCP Server listening on port ${port}`);
  });
}

const isDirectRun =
  process.argv[1] !== undefined &&
  fileURLToPath(import.meta.url) === process.argv[1];

if (isDirectRun) {
  main().catch((err) => {
    console.error("Fatal MCP server startup error:", err);
    process.exit(1);
  });
}
