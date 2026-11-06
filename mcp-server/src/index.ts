import { fileURLToPath } from "node:url";
import cors from "cors";
import express, { type Express, type Request, type Response } from "express";
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
  rateLimitMaxPerMinute?: number;
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
        "Mcp-Session-Id",
        "Last-Event-ID",
        "X-App-Env",
      ],
      exposedHeaders: ["Mcp-Session-Id", "WWW-Authenticate"],
    })
  );

  // Layer 4 Anti-Bill Protection: In-Memory IP Rate Limiting
  const limiter = rateLimit({
    windowMs: 60 * 1000,
    max: options.rateLimitMaxPerMinute ?? 60,
    standardHeaders: true,
    legacyHeaders: false,
    message: {
      error: "too_many_requests",
      error_description: "Rate limit exceeded (max 60 requests/minute).",
    },
  });
  app.use(limiter);

  const domain = new WorkoutDomainService(options.backingStore);
  const oauth = new OAuthServer(options.oauthConfig);

  // Health check endpoint (unauthenticated for Cloud Run startup/liveness probes)
  app.get("/health", (_req, res) => {
    res.json({
      status: "ok",
      service: "crosstraining-mcp-server",
      version: "1.0.0",
    });
  });

  // Mount OAuth 2.1 + RFC 7591 DCR endpoints
  app.use(oauth.createRouter());

  // ==========================================================================
  // Streamable HTTP Transport (/mcp) — Used by Gemini, Claude, and ChatGPT
  // ==========================================================================

  app.post("/mcp", oauth.requireAuth, express.json(), async (req: Request, res: Response) => {
    const user = res.locals.user as UserContext;
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
  });

  app.head("/mcp", oauth.requireAuth, (_req: Request, res: Response) => {
    res.status(200).end();
  });

  app.get("/mcp", oauth.requireAuth, (_req: Request, res: Response) => {
    res.status(405).json({
      jsonrpc: "2.0",
      error: {
        code: -32000,
        message: "Method not allowed in stateless Streamable HTTP mode; use POST /mcp or GET /sse",
      },
      id: null,
    });
  });

  app.delete("/mcp", oauth.requireAuth, (_req: Request, res: Response) => {
    res.status(200).json({ status: "closed" });
  });

  // ==========================================================================
  // Legacy SSE Transport (/sse + /messages) — For SSE-based MCP Clients
  // ==========================================================================

  const sseTransports = new Map<
    string,
    { transport: SSEServerTransport; user: UserContext }
  >();

  app.get("/sse", oauth.requireAuth, async (_req: Request, res: Response) => {
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

  app.post("/messages", oauth.requireAuth, express.json(), async (req: Request, res: Response) => {
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
  });

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

  const port = Number(process.env.PORT || 8080);
  app.listen(port, () => {
    console.log(`CrossTraining Remote MCP Server listening on port ${port}`);
  });
}

const currentFilePath = fileURLToPath(import.meta.url);
if (process.argv[1] === currentFilePath) {
  main().catch((err) => {
    console.error("Fatal startup error:", err);
    process.exit(1);
  });
}
