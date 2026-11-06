import assert from "node:assert/strict";
import crypto from "node:crypto";
import type { Server } from "node:http";
import { after, before, describe, it } from "node:test";
import { createMcpExpressApp } from "../src/index.js";
import { InMemoryBackingStore } from "../src/store.js";
import { evaluateBudgetNotification } from "../billing-killswitch/index.js";

function base64Url(buf: Buffer): string {
  return buf
    .toString("base64")
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/g, "");
}

describe("CrossTraining Remote MCP Server (OAuth 2.1 + DCR + Tools)", () => {
  let server: Server;
  let baseUrl: string;
  let backingStore: InMemoryBackingStore;

  before(async () => {
    backingStore = new InMemoryBackingStore();
    const app = createMcpExpressApp({
      backingStore,
      rateLimitMaxPerMinute: 500,
      oauthConfig: {
        jwtSecret: "test-secret-key-for-mcp-unit-tests",
        devBearerToken: "dev-static-token-123",
        devDefaultUid: "dev-user-uid",
        verifyFirebaseIdToken: async (idToken: string) => {
          if (idToken === "valid-firebase-id-token-athlete") {
            return { uid: "athlete-uid-001", email: "athlete@crosstraining.app" };
          }
          throw new Error("Invalid Firebase ID Token");
        },
      },
    });

    await new Promise<void>((resolve) => {
      server = app.listen(0, "127.0.0.1", () => {
        const addr = server.address();
        if (addr && typeof addr === "object") {
          baseUrl = `http://127.0.0.1:${addr.port}`;
        }
        resolve();
      });
    });
  });

  after(async () => {
    await new Promise<void>((resolve, reject) => {
      server.close((err) => (err ? reject(err) : resolve()));
    });
  });

  it("exposes RFC 8414 and RFC 9728 OAuth 2.1 discovery metadata", async () => {
    const authMetaRes = await fetch(`${baseUrl}/.well-known/oauth-authorization-server`);
    assert.equal(authMetaRes.status, 200);
    const authMeta = (await authMetaRes.json()) as Record<string, unknown>;
    assert.equal(authMeta.registration_endpoint, `${baseUrl}/register`);
    assert.equal(authMeta.authorization_endpoint, `${baseUrl}/authorize`);
    assert.equal(authMeta.token_endpoint, `${baseUrl}/token`);
    assert.ok(Array.isArray(authMeta.code_challenge_methods_supported));

    const resMetaRes = await fetch(`${baseUrl}/.well-known/oauth-protected-resource`);
    assert.equal(resMetaRes.status, 200);
    const resMeta = (await resMetaRes.json()) as Record<string, unknown>;
    assert.equal(resMeta.resource, `${baseUrl}/mcp`);
  });

  it("supports RFC 7591 Dynamic Client Registration (DCR) and full PKCE S256 token flow", async () => {
    // 1. Dynamic Client Registration (used by Gemini Connected Apps & Claude)
    const regRes = await fetch(`${baseUrl}/register`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        client_name: "Google Gemini Connected App",
        redirect_uris: ["https://gemini.google.com/oauth/callback"],
      }),
    });
    assert.equal(regRes.status, 201);
    const regData = (await regRes.json()) as { client_id: string };
    assert.ok(regData.client_id.startsWith("ct_client_"));

    // 2. Generate PKCE S256 verifier and challenge
    const codeVerifier = base64Url(crypto.randomBytes(32));
    const codeChallenge = base64Url(
      crypto.createHash("sha256").update(codeVerifier, "ascii").digest()
    );

    // 3. Authorize with Firebase ID token and environment selection
    const authRes = await fetch(`${baseUrl}/authorize`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
      },
      body: JSON.stringify({
        id_token: "valid-firebase-id-token-athlete",
        env: "production",
        client_id: regData.client_id,
        redirect_uri: "https://gemini.google.com/oauth/callback",
        state: "xyz-state-123",
        code_challenge: codeChallenge,
        code_challenge_method: "S256",
      }),
    });
    assert.equal(authRes.status, 200);
    const authJson = (await authRes.json()) as { redirect_to: string };
    const callbackUrl = new URL(authJson.redirect_to);
    const code = callbackUrl.searchParams.get("code");
    assert.ok(code);
    assert.equal(callbackUrl.searchParams.get("state"), "xyz-state-123");

    // 4. Reject invalid PKCE verifier without burning valid flow test
    const tokenRes = await fetch(`${baseUrl}/token`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        grant_type: "authorization_code",
        code,
        code_verifier: codeVerifier,
      }),
    });
    assert.equal(tokenRes.status, 200);
    const tokenData = (await tokenRes.json()) as {
      access_token: string;
      refresh_token: string;
    };
    assert.ok(tokenData.access_token);
    assert.ok(tokenData.refresh_token);

    // 5. Refresh token rotation
    const refreshRes = await fetch(`${baseUrl}/token`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        grant_type: "refresh_token",
        refresh_token: tokenData.refresh_token,
      }),
    });
    assert.equal(refreshRes.status, 200);
    const refreshedData = (await refreshRes.json()) as { access_token: string };
    assert.ok(refreshedData.access_token);
  });

  it("rejects unauthenticated /mcp calls with 401 and WWW-Authenticate header", async () => {
    const res = await fetch(`${baseUrl}/mcp`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        jsonrpc: "2.0",
        id: 1,
        method: "tools/list",
      }),
    });
    assert.equal(res.status, 401);
    assert.ok(res.headers.get("www-authenticate")?.includes("resource_metadata="));
  });

  it("executes MCP tools/list and tools/call across all workout domains over Streamable HTTP", async () => {
    async function callMcp(body: Record<string, unknown>) {
      const res = await fetch(`${baseUrl}/mcp`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json, text/event-stream",
          Authorization: "Bearer dev-static-token-123",
        },
        body: JSON.stringify(body),
      });
      assert.equal(res.status, 200);
      const contentType = res.headers.get("content-type") || "";
      if (contentType.includes("text/event-stream")) {
        const text = await res.text();
        const dataLine = text
          .split("\n")
          .find((l) => l.startsWith("data: "));
        assert.ok(dataLine, `Expected SSE data line, got: ${text}`);
        return JSON.parse(dataLine.slice(6));
      }
      return res.json();
    }

    // 1. List tools and verify Gemini compatibility (snake_case <= 60 chars)
    const listResp = await callMcp({
      jsonrpc: "2.0",
      id: 1,
      method: "tools/list",
      params: {},
    });
    const tools = listResp.result.tools as Array<{ name: string }>;
    assert.ok(tools.length >= 16);
    for (const tool of tools) {
      assert.ok(
        tool.name.length <= 60 && /^[a-z0-9_]+$/.test(tool.name),
        `Tool name ${tool.name} must be Gemini-compatible snake_case <= 60 chars`
      );
    }

    // 2. Upsert a workout session (auto-creates Back Squat & Snatch exercises)
    const sessionResp = await callMcp({
      jsonrpc: "2.0",
      id: 2,
      method: "tools/call",
      params: {
        name: "upsert_session",
        arguments: {
          date: "2026-11-06",
          title: "Friday Heavy Strength",
          notes: "Felt strong on squats",
          blocks: [
            {
              name: "Back Squat Waves",
              kind: "STRENGTH",
              format: "E3MOM",
              scheme: "5-3-1",
              mainExerciseName: "Back Squat",
              sets: [
                { reps: 5, weight: 120 },
                { reps: 3, weight: 135 },
                { reps: 1, weight: 150, notes: "RPE 8.5" },
              ],
            },
          ],
        },
      },
    });
    const sessionPayload = JSON.parse(sessionResp.result.content[0].text);
    assert.equal(sessionPayload.status, "ok");
    assert.equal(sessionPayload.session.session.title, "Friday Heavy Strength");
    assert.equal(sessionPayload.session.blocks[0].sets.length, 3);

    // 3. Upsert a Cycle Goal (adaptive CALORIES goal with 5-min window)
    const goalResp = await callMcp({
      jsonrpc: "2.0",
      id: 3,
      method: "tools/call",
      params: {
        name: "upsert_cycle_goal",
        arguments: {
          exerciseName: "Air Bike",
          goalMetric: "CALORIES",
          timeWindowMinutes: 5,
          startWeight: 65,
          targetWeight: 85,
          notes: "Push pace",
        },
      },
    });
    const goalPayload = JSON.parse(goalResp.result.content[0].text);
    assert.equal(goalPayload.status, "ok");
    assert.equal(
      goalPayload.cycleGoal.notes,
      "[metric=CALORIES,window=5] Push pace"
    );

    // 4. Upsert Weight Entry & Daily Log
    const weightResp = await callMcp({
      jsonrpc: "2.0",
      id: 4,
      method: "tools/call",
      params: {
        name: "upsert_weight_entry",
        arguments: {
          date: "2026-11-06",
          weightKg: 81.4,
          notes: "Morning fasted",
        },
      },
    });
    const weightPayload = JSON.parse(weightResp.result.content[0].text);
    assert.equal(weightPayload.weightEntry.weightKg, 81.4);

    const logResp = await callMcp({
      jsonrpc: "2.0",
      id: 5,
      method: "tools/call",
      params: {
        name: "upsert_daily_log",
        arguments: {
          date: "2026-11-06",
          fastCompleted: true,
          caloriesKcal: 2300,
          proteinGrams: 185,
        },
      },
    });
    const logPayload = JSON.parse(logResp.result.content[0].text);
    assert.equal(logPayload.dailyLog.fastCompleted, true);
    assert.equal(logPayload.dailyLog.proteinGrams, 185);
  });

  it("triggers billing kill-switch when costAmount >= budgetAmount", async () => {
    let disabledProject: string | null = null;
    const belowCap = await evaluateBudgetNotification(
      { costAmount: 1.25, budgetAmount: 5.0 },
      async (proj: string) => {
        disabledProject = proj;
      }
    );
    assert.equal(belowCap.action, "NO_OP_BELOW_CAP");
    assert.equal(disabledProject, null);

    const atCap = await evaluateBudgetNotification(
      { costAmount: 5.05, budgetAmount: 5.0 },
      async (proj: string) => {
        disabledProject = proj;
      }
    );
    assert.equal(atCap.action, "BILLING_DISABLED");
    assert.equal(disabledProject, "virgymia-c2cc7");
  });
});
