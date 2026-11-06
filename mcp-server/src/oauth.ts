import crypto from "node:crypto";
import type { Request, Response, NextFunction, Router } from "express";
import express from "express";
import type { AppEnv, UserContext } from "./store.js";

export interface OAuthConfig {
  /** Base public URL of the server (inferred from request headers if omitted) */
  issuerUrl?: string;
  /** Secret key used to sign stateless OAuth access and refresh tokens */
  jwtSecret: string;
  /** Firebase Web API Key for the /authorize login UI (defaults to virgymia-c2cc7 key) */
  firebaseApiKey?: string;
  /** Firebase Project ID (defaults to virgymia-c2cc7) */
  firebaseProjectId?: string;
  /** Optional static dev token for quick CLI / Antigravity testing */
  devBearerToken?: string;
  /** Default environment when using devBearerToken */
  devDefaultUid?: string;
  /** Pluggable Firebase ID token verifier (uses firebase-admin in prod, mockable in tests) */
  verifyFirebaseIdToken: (idToken: string) => Promise<{ uid: string; email?: string }>;
}

interface RegisteredClient {
  client_id: string;
  client_secret?: string;
  client_name?: string;
  redirect_uris: string[];
  grant_types: string[];
  response_types: string[];
  token_endpoint_auth_method: string;
  created_at: number;
}

interface AuthCodeRecord {
  code: string;
  clientId: string;
  redirectUri: string;
  uid: string;
  email?: string;
  env: AppEnv;
  codeChallenge?: string;
  codeChallengeMethod?: string;
  expiresAt: number;
}

interface SignedTokenPayload {
  sub: string;
  email?: string;
  env: AppEnv;
  type: "access" | "refresh";
  clientId: string;
  iat: number;
  exp: number;
}

function base64UrlEncode(buf: Buffer | string): string {
  const b = typeof buf === "string" ? Buffer.from(buf, "utf8") : buf;
  return b
    .toString("base64")
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/g, "");
}

function base64UrlDecode(str: string): Buffer {
  const padded = str.replace(/-/g, "+").replace(/_/g, "/");
  const padLen = (4 - (padded.length % 4)) % 4;
  return Buffer.from(padded + "=".repeat(padLen), "base64");
}

export function signStatelessToken(
  payload: SignedTokenPayload,
  secret: string
): string {
  const header = base64UrlEncode(JSON.stringify({ alg: "HS256", typ: "JWT" }));
  const body = base64UrlEncode(JSON.stringify(payload));
  const sig = crypto
    .createHmac("sha256", secret)
    .update(`${header}.${body}`)
    .digest();
  return `${header}.${body}.${base64UrlEncode(sig)}`;
}

export function verifyStatelessToken(
  token: string,
  secret: string
): SignedTokenPayload | null {
  const parts = token.split(".");
  if (parts.length !== 3) return null;
  const [header, body, sig] = parts;
  const expectedSig = base64UrlEncode(
    crypto.createHmac("sha256", secret).update(`${header}.${body}`).digest()
  );
  const sigBuf = Buffer.from(sig);
  const expectedBuf = Buffer.from(expectedSig);
  if (
    sigBuf.length !== expectedBuf.length ||
    !crypto.timingSafeEqual(sigBuf, expectedBuf)
  ) {
    return null;
  }
  try {
    const parsed = JSON.parse(
      base64UrlDecode(body).toString("utf8")
    ) as SignedTokenPayload;
    if (Date.now() >= parsed.exp * 1000) {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

export function verifyPkceChallenge(
  codeVerifier: string,
  codeChallenge: string,
  method = "S256"
): boolean {
  if (method.toUpperCase() === "PLAIN") {
    return codeVerifier === codeChallenge;
  }
  const hash = crypto.createHash("sha256").update(codeVerifier, "ascii").digest();
  return base64UrlEncode(hash) === codeChallenge;
}

function resolveIssuer(req: Request, configuredIssuer?: string): string {
  if (configuredIssuer) {
    return configuredIssuer.replace(/\/+$/, "");
  }
  const proto = (req.headers["x-forwarded-proto"] as string) || req.protocol || "https";
  const host = (req.headers["x-forwarded-host"] as string) || req.get("host") || "localhost:8080";
  return `${proto}://${host}`;
}

export class OAuthServer {
  private readonly clients = new Map<string, RegisteredClient>();
  private readonly authCodes = new Map<string, AuthCodeRecord>();

  constructor(private readonly config: OAuthConfig) {}

  createRouter(): Router {
    const router = express.Router();

    // 1. OAuth 2.0 / 2.1 Authorization Server Metadata (RFC 8414)
    router.get("/.well-known/oauth-authorization-server", (req, res) => {
      const issuer = resolveIssuer(req, this.config.issuerUrl);
      res.json({
        issuer,
        authorization_endpoint: `${issuer}/authorize`,
        token_endpoint: `${issuer}/token`,
        registration_endpoint: `${issuer}/register`,
        response_types_supported: ["code"],
        grant_types_supported: ["authorization_code", "refresh_token"],
        token_endpoint_auth_methods_supported: [
          "none",
          "client_secret_post",
          "client_secret_basic",
        ],
        code_challenge_methods_supported: ["S256", "plain"],
        scopes_supported: ["mcp:tools", "profile", "email"],
      });
    });

    // 2. OAuth Protected Resource Metadata (RFC 9728)
    router.get("/.well-known/oauth-protected-resource", (req, res) => {
      const issuer = resolveIssuer(req, this.config.issuerUrl);
      res.json({
        resource: `${issuer}/mcp`,
        authorization_servers: [issuer],
        bearer_methods_supported: ["header"],
        scopes_supported: ["mcp:tools"],
      });
    });

    // 3. RFC 7591 Dynamic Client Registration (DCR) — required by Gemini Connected Apps & Claude
    router.post("/register", express.json(), (req, res) => {
      const body = req.body ?? {};
      const redirectUris = Array.isArray(body.redirect_uris)
        ? body.redirect_uris.map(String)
        : [];

      const clientId = `ct_client_${crypto.randomBytes(12).toString("hex")}`;
      const clientSecret = crypto.randomBytes(24).toString("hex");
      const nowSec = Math.floor(Date.now() / 1000);

      const record: RegisteredClient = {
        client_id: clientId,
        client_secret: clientSecret,
        client_name: String(body.client_name || "MCP AI Assistant"),
        redirect_uris: redirectUris,
        grant_types: Array.isArray(body.grant_types)
          ? body.grant_types
          : ["authorization_code", "refresh_token"],
        response_types: Array.isArray(body.response_types)
          ? body.response_types
          : ["code"],
        token_endpoint_auth_method: String(
          body.token_endpoint_auth_method || "none"
        ),
        created_at: nowSec,
      };

      this.clients.set(clientId, record);

      res.status(201).json({
        ...record,
        client_id_issued_at: nowSec,
        client_secret_expires_at: 0,
      });
    });

    // 4. GET /authorize — Render Firebase Auth Login + Environment Selector Page
    router.get("/authorize", (req, res) => {
      const clientId = String(req.query.client_id || "");
      const redirectUri = String(req.query.redirect_uri || "");
      const state = String(req.query.state || "");
      const codeChallenge = String(req.query.code_challenge || "");
      const codeChallengeMethod = String(req.query.code_challenge_method || "S256");

      if (!redirectUri) {
        res.status(400).send("Missing required parameter: redirect_uri");
        return;
      }

      const apiKey =
        this.config.firebaseApiKey || "AIzaSyCnhmJWei5bO3yaebw-Gqthqynk5VCJu9s";
      const projectId = this.config.firebaseProjectId || "virgymia-c2cc7";

      res.setHeader("Content-Type", "text/html; charset=utf-8");
      res.send(
        renderConsentPageHtml({
          clientId,
          redirectUri,
          state,
          codeChallenge,
          codeChallengeMethod,
          apiKey,
          projectId,
        })
      );
    });

    // 5. POST /authorize — Verify Firebase ID token and issue OAuth 2.1 authorization code
    router.post(
      "/authorize",
      express.urlencoded({ extended: false }),
      express.json(),
      async (req, res) => {
        try {
          const {
            id_token,
            env,
            client_id,
            redirect_uri,
            state,
            code_challenge,
            code_challenge_method,
          } = req.body ?? {};

          if (!id_token || !redirect_uri) {
            res
              .status(400)
              .json({ error: "invalid_request", error_description: "Missing id_token or redirect_uri" });
            return;
          }

          const verified = await this.config.verifyFirebaseIdToken(String(id_token));
          const targetEnv: AppEnv =
            env === "snapshot" ? "snapshot" : "production";

          const code = base64UrlEncode(crypto.randomBytes(24));
          this.authCodes.set(code, {
            code,
            clientId: String(client_id || "default"),
            redirectUri: String(redirect_uri),
            uid: verified.uid,
            email: verified.email,
            env: targetEnv,
            codeChallenge: code_challenge ? String(code_challenge) : undefined,
            codeChallengeMethod: code_challenge_method
              ? String(code_challenge_method)
              : "S256",
            expiresAt: Date.now() + 5 * 60 * 1000, // 5 minutes
          });

          const redirectUrl = new URL(String(redirect_uri));
          redirectUrl.searchParams.set("code", code);
          if (state) {
            redirectUrl.searchParams.set("state", String(state));
          }

          if (req.headers.accept?.includes("application/json")) {
            res.json({ redirect_to: redirectUrl.toString() });
          } else {
            res.redirect(302, redirectUrl.toString());
          }
        } catch (err) {
          const msg = err instanceof Error ? err.message : "Authentication failed";
          res.status(401).json({ error: "access_denied", error_description: msg });
        }
      }
    );

    // 6. POST /token — Exchange code or refresh_token for stateless JWT Bearer token
    router.post(
      "/token",
      express.urlencoded({ extended: false }),
      express.json(),
      (req, res) => {
        const grantType = String(req.body?.grant_type || "");

        if (grantType === "authorization_code") {
          const code = String(req.body?.code || "");
          const codeVerifier = String(req.body?.code_verifier || "");
          const record = this.authCodes.get(code);

          if (!record || Date.now() > record.expiresAt) {
            this.authCodes.delete(code);
            res.status(400).json({
              error: "invalid_grant",
              error_description: "Authorization code is invalid or expired",
            });
            return;
          }

          // Single-use code
          this.authCodes.delete(code);

          if (record.codeChallenge) {
            if (
              !codeVerifier ||
              !verifyPkceChallenge(
                codeVerifier,
                record.codeChallenge,
                record.codeChallengeMethod
              )
            ) {
              res.status(400).json({
                error: "invalid_grant",
                error_description: "PKCE code_verifier validation failed",
              });
              return;
            }
          }

          const nowSec = Math.floor(Date.now() / 1000);
          const accessExp = nowSec + 3600 * 24 * 7; // 7 days
          const refreshExp = nowSec + 3600 * 24 * 90; // 90 days

          const accessToken = signStatelessToken(
            {
              sub: record.uid,
              email: record.email,
              env: record.env,
              type: "access",
              clientId: record.clientId,
              iat: nowSec,
              exp: accessExp,
            },
            this.config.jwtSecret
          );

          const refreshToken = signStatelessToken(
            {
              sub: record.uid,
              email: record.email,
              env: record.env,
              type: "refresh",
              clientId: record.clientId,
              iat: nowSec,
              exp: refreshExp,
            },
            this.config.jwtSecret
          );

          res.json({
            access_token: accessToken,
            token_type: "Bearer",
            expires_in: 3600 * 24 * 7,
            refresh_token: refreshToken,
            scope: "mcp:tools",
          });
          return;
        }

        if (grantType === "refresh_token") {
          const refreshToken = String(req.body?.refresh_token || "");
          const verified = verifyStatelessToken(
            refreshToken,
            this.config.jwtSecret
          );
          if (!verified || verified.type !== "refresh") {
            res.status(400).json({
              error: "invalid_grant",
              error_description: "Invalid or expired refresh_token",
            });
            return;
          }

          const nowSec = Math.floor(Date.now() / 1000);
          const accessToken = signStatelessToken(
            {
              sub: verified.sub,
              email: verified.email,
              env: verified.env,
              type: "access",
              clientId: verified.clientId,
              iat: nowSec,
              exp: nowSec + 3600 * 24 * 7,
            },
            this.config.jwtSecret
          );

          res.json({
            access_token: accessToken,
            token_type: "Bearer",
            expires_in: 3600 * 24 * 7,
            refresh_token: refreshToken,
            scope: "mcp:tools",
          });
          return;
        }

        res.status(400).json({
          error: "unsupported_grant_type",
          error_description: `Unsupported grant_type: ${grantType}`,
        });
      }
    );

    return router;
  }

  /**
   * Express middleware that authenticates Bearer tokens and attaches UserContext to res.locals.user
   */
  requireAuth = (req: Request, res: Response, next: NextFunction): void => {
    const authHeader = req.headers.authorization || "";
    const match = /^Bearer\s+(.+)$/i.exec(authHeader.trim());

    if (!match) {
      const issuer = resolveIssuer(req, this.config.issuerUrl);
      res.setHeader(
        "WWW-Authenticate",
        `Bearer resource_metadata="${issuer}/.well-known/oauth-protected-resource"`
      );
      res.status(401).json({
        error: "unauthorized",
        error_description: "Missing Bearer token",
      });
      return;
    }

    const rawToken = match[1].trim();

    // Check optional dev bearer token for local CLI / Antigravity usage
    if (
      this.config.devBearerToken &&
      rawToken === this.config.devBearerToken &&
      this.config.devDefaultUid
    ) {
      const envHeader = req.headers["x-app-env"];
      const env: AppEnv = envHeader === "snapshot" ? "snapshot" : "production";
      res.locals.user = {
        uid: this.config.devDefaultUid,
        env,
      } satisfies UserContext;
      next();
      return;
    }

    const verified = verifyStatelessToken(rawToken, this.config.jwtSecret);
    if (!verified || verified.type !== "access") {
      const issuer = resolveIssuer(req, this.config.issuerUrl);
      res.setHeader(
        "WWW-Authenticate",
        `Bearer error="invalid_token", resource_metadata="${issuer}/.well-known/oauth-protected-resource"`
      );
      res.status(401).json({
        error: "invalid_token",
        error_description: "Invalid or expired Bearer token",
      });
      return;
    }

    res.locals.user = {
      uid: verified.sub,
      email: verified.email,
      env: verified.env,
    } satisfies UserContext;
    next();
  };
}

function escapeHtml(s: string): string {
  return s
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function renderConsentPageHtml(params: {
  clientId: string;
  redirectUri: string;
  state: string;
  codeChallenge: string;
  codeChallengeMethod: string;
  apiKey: string;
  projectId: string;
}): string {
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Connect CrossTraining to AI Assistant</title>
  <style>
    :root {
      color-scheme: dark;
      --bg: #0f172a;
      --card: #1e293b;
      --accent: #38bdf8;
      --text: #f8fafc;
      --muted: #94a3b8;
      --border: #334155;
    }
    body {
      margin: 0;
      font-family: system-ui, -apple-system, sans-serif;
      background: var(--bg);
      color: var(--text);
      display: flex;
      min-height: 100vh;
      align-items: center;
      justify-content: center;
      padding: 16px;
    }
    .card {
      background: var(--card);
      border: 1px solid var(--border);
      border-radius: 16px;
      max-width: 420px;
      width: 100%;
      padding: 28px;
      box-shadow: 0 20px 40px rgba(0,0,0,0.4);
    }
    h1 { font-size: 1.35rem; margin: 0 0 8px; }
    p { color: var(--muted); font-size: 0.92rem; margin: 0 0 20px; line-height: 1.45; }
    label { display: block; font-size: 0.82rem; color: var(--muted); margin-bottom: 6px; font-weight: 600; }
    input, select {
      width: 100%;
      box-sizing: border-box;
      padding: 10px 12px;
      margin-bottom: 14px;
      border-radius: 8px;
      border: 1px solid var(--border);
      background: #0f172a;
      color: var(--text);
      font-size: 0.95rem;
    }
    button {
      width: 100%;
      padding: 12px;
      border-radius: 8px;
      border: none;
      font-weight: 600;
      font-size: 0.95rem;
      cursor: pointer;
      margin-bottom: 10px;
    }
    .btn-primary { background: var(--accent); color: #0f172a; }
    .btn-google { background: #ffffff; color: #0f172a; }
    .divider { text-align: center; color: var(--muted); font-size: 0.8rem; margin: 12px 0; }
    .error { color: #f87171; font-size: 0.85rem; margin-top: 8px; display: none; }
  </style>
</head>
<body>
  <div class="card">
    <h1>Connect CrossTraining</h1>
    <p>Sign in with your CrossTraining account to allow your AI assistant (Gemini, Claude, or ChatGPT) to view and update your workouts, plans, and logs.</p>

    <label for="env">Target App Environment</label>
    <select id="env">
      <option value="production" selected>Production (Release App)</option>
      <option value="snapshot">Snapshot (Debug / Dev App)</option>
    </select>

    <button type="button" class="btn-google" id="googleBtn">Continue with Google</button>
    <div class="divider">or sign in with email</div>

    <input type="email" id="email" placeholder="Email address" autocomplete="username" />
    <input type="password" id="password" placeholder="Password" autocomplete="current-password" />
    <button type="button" class="btn-primary" id="emailBtn">Sign In &amp; Authorize</button>
    <div class="error" id="errBox"></div>
  </div>

  <script type="module">
    import { initializeApp } from "https://www.gstatic.com/firebasejs/11.0.1/firebase-app.js";
    import { getAuth, signInWithEmailAndPassword, GoogleAuthProvider, signInWithPopup } from "https://www.gstatic.com/firebasejs/11.0.1/firebase-auth.js";

    const firebaseConfig = {
      apiKey: "${escapeHtml(params.apiKey)}",
      authDomain: "${escapeHtml(params.projectId)}.firebaseapp.com",
      projectId: "${escapeHtml(params.projectId)}"
    };

    const app = initializeApp(firebaseConfig);
    const auth = getAuth(app);
    const errBox = document.getElementById("errBox");

    function showError(msg) {
      errBox.textContent = msg;
      errBox.style.display = "block";
    }

    async function completeOAuth(user) {
      const idToken = await user.getIdToken();
      const env = document.getElementById("env").value;
      const resp = await fetch("/authorize", {
        method: "POST",
        headers: { "Content-Type": "application/json", "Accept": "application/json" },
        body: JSON.stringify({
          id_token: idToken,
          env,
          client_id: "${escapeHtml(params.clientId)}",
          redirect_uri: "${escapeHtml(params.redirectUri)}",
          state: "${escapeHtml(params.state)}",
          code_challenge: "${escapeHtml(params.codeChallenge)}",
          code_challenge_method: "${escapeHtml(params.codeChallengeMethod)}"
        })
      });
      const data = await resp.json();
      if (!resp.ok || !data.redirect_to) {
        throw new Error(data.error_description || "Authorization failed");
      }
      window.location.href = data.redirect_to;
    }

    document.getElementById("googleBtn").addEventListener("click", async () => {
      try {
        errBox.style.display = "none";
        const result = await signInWithPopup(auth, new GoogleAuthProvider());
        await completeOAuth(result.user);
      } catch (e) {
        showError(e.message || "Google sign-in failed");
      }
    });

    document.getElementById("emailBtn").addEventListener("click", async () => {
      try {
        errBox.style.display = "none";
        let email = document.getElementById("email").value.trim().toLowerCase();
        if (email === "jangelpv") email = "jangelpv@crosstraining.app";
        if (email === "coach") email = "coach@crosstraining.app";
        if (email === "athlete") email = "athlete@crosstraining.app";
        const pass = document.getElementById("password").value;
        const result = await signInWithEmailAndPassword(auth, email, pass);
        await completeOAuth(result.user);
      } catch (e) {
        showError(e.message || "Email sign-in failed");
      }
    });
  </script>
</body>
</html>`;
}
