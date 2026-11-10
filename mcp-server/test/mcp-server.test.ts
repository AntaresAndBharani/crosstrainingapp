import assert from "node:assert/strict";
import crypto from "node:crypto";
import type { Server } from "node:http";
import { after, before, describe, it } from "node:test";
import { createMcpExpressApp } from "../src/index.js";
import { computeWeightMomentumAnalytics } from "../src/schemas.js";
import {
  type CycleGoalRecord,
  InMemoryBackingStore,
  type RoutineWithBlocksRecord,
  type SessionWithBlocksRecord,
} from "../src/store.js";
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
        jwtSecret: "test-secret-key-for-mcp-unit-tests-at-least-32-chars",
        devBearerToken: "dev-static-token-123",
        devDefaultUid: "dev-user-uid",
        verifyFirebaseIdToken: async (idToken: string) => {
          if (idToken === "valid-firebase-id-token-athlete") {
            return { uid: "athlete-uid-001", email: "athlete@crosstraining.app" };
          }
          if (idToken === "valid-firebase-id-token-athlete-2") {
            return { uid: "athlete-uid-002", email: "athlete2@crosstraining.app" };
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

  async function issueAccessTokenForIdToken(idToken: string): Promise<string> {
    const regRes = await fetch(`${baseUrl}/register`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        client_name: "Gemini Test Client",
        redirect_uris: ["https://gemini.google.com/oauth/callback"],
      }),
    });
    const { client_id } = (await regRes.json()) as { client_id: string };
    const verifier = base64Url(crypto.randomBytes(32));
    const challenge = base64Url(
      crypto.createHash("sha256").update(verifier, "ascii").digest()
    );
    const authRes = await fetch(`${baseUrl}/authorize`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({
        id_token: idToken,
        env: "production",
        client_id,
        redirect_uri: "https://gemini.google.com/oauth/callback",
        state: "st-1",
        code_challenge: challenge,
        code_challenge_method: "S256",
      }),
    });
    const { redirect_to } = (await authRes.json()) as { redirect_to: string };
    const code = new URL(redirect_to).searchParams.get("code")!;
    const tokRes = await fetch(`${baseUrl}/token`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        grant_type: "authorization_code",
        code,
        code_verifier: verifier,
        client_id,
        redirect_uri: "https://gemini.google.com/oauth/callback",
      }),
    });
    const { access_token } = (await tokRes.json()) as { access_token: string };
    return access_token;
  }

  it("exposes RFC 8414 and RFC 9728 OAuth 2.1 discovery metadata and /health + /healthz", async () => {
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

    for (const healthPath of ["/health", "/healthz"]) {
      const hRes = await fetch(`${baseUrl}${healthPath}`);
      assert.equal(hRes.status, 200);
      const hJson = (await hRes.json()) as { status: string };
      assert.equal(hJson.status, "ok");
    }
  });

  it("supports RFC 7591 Dynamic Client Registration (DCR) and full PKCE S256 token flow", async () => {
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

    const codeVerifier = base64Url(crypto.randomBytes(32));
    const codeChallenge = base64Url(
      crypto.createHash("sha256").update(codeVerifier, "ascii").digest()
    );

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

    const tokenRes = await fetch(`${baseUrl}/token`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        grant_type: "authorization_code",
        code,
        code_verifier: codeVerifier,
        client_id: regData.client_id,
        redirect_uri: "https://gemini.google.com/oauth/callback",
      }),
    });
    assert.equal(tokenRes.status, 200);
    const tokenData = (await tokenRes.json()) as {
      access_token: string;
      refresh_token: string;
    };
    assert.ok(tokenData.access_token);
    assert.ok(tokenData.refresh_token);
    assert.ok(tokenData.access_token.length < 600, `access_token too large: ${tokenData.access_token.length}`);
    assert.ok(tokenData.refresh_token.length < 600, `refresh_token too large: ${tokenData.refresh_token.length}`);

    const refreshRes = await fetch(`${baseUrl}/token`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        grant_type: "refresh_token",
        refresh_token: tokenData.refresh_token,
        client_id: regData.client_id,
      }),
    });
    assert.equal(refreshRes.status, 200);
    const refreshedData = (await refreshRes.json()) as { access_token: string };
    assert.ok(refreshedData.access_token);

    const otherReg = (await (
      await fetch(`${baseUrl}/register`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ redirect_uris: ["https://claude.ai/api/mcp/auth_callback"] }),
      })
    ).json()) as { client_id: string };
    const crossClientRefresh = await fetch(`${baseUrl}/token`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        grant_type: "refresh_token",
        refresh_token: tokenData.refresh_token,
        client_id: otherReg.client_id,
      }),
    });
    assert.equal(crossClientRefresh.status, 401);

    const multiRedirects = Array.from(
      { length: 8 },
      (_, i) => `https://connector-${i}.example.com/oauth/callback/very/long/path/segment`
    );
    const multiReg = (await (
      await fetch(`${baseUrl}/register`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ client_name: "Multi-URI Connector", redirect_uris: multiRedirects }),
      })
    ).json()) as { client_id: string };
    assert.ok(multiReg.client_id.startsWith("ct_client_z_"), "Large client registrations must be compressed");
    const multiAuthRes = await fetch(`${baseUrl}/authorize`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({
        id_token: "valid-firebase-id-token-athlete",
        env: "production",
        client_id: multiReg.client_id,
        redirect_uri: multiRedirects[5],
        state: "multi-state",
        code_challenge: codeChallenge,
        code_challenge_method: "S256",
      }),
    });
    assert.equal(multiAuthRes.status, 200);
  });

  it("rejects unregistered clients, unregistered redirect_uris, missing/plain PKCE, and forged client_ids", async () => {
    const codeVerifier = base64Url(crypto.randomBytes(32));
    const codeChallenge = base64Url(
      crypto.createHash("sha256").update(codeVerifier, "ascii").digest()
    );

    for (const badUri of ["javascript:alert(1)", "http://evil.example.com/cb", "https://x.example/cb#frag"]) {
      const res = await fetch(`${baseUrl}/register`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ redirect_uris: [badUri] }),
      });
      assert.equal(res.status, 400, `expected ${badUri} to be rejected`);
    }

    const reg = (await (
      await fetch(`${baseUrl}/register`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          client_name: "Claude Code",
          redirect_uris: ["http://localhost:33418/callback"],
        }),
      })
    ).json()) as { client_id: string; client_secret?: string };
    assert.equal(reg.client_secret, undefined, "public clients must not receive a client_secret");

    async function postAuthorize(overrides: Record<string, unknown>) {
      return fetch(`${baseUrl}/authorize`, {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({
          id_token: "valid-firebase-id-token-athlete",
          env: "production",
          client_id: reg.client_id,
          redirect_uri: "http://localhost:33418/callback",
          code_challenge: codeChallenge,
          code_challenge_method: "S256",
          ...overrides,
        }),
      });
    }

    assert.equal((await postAuthorize({ redirect_uri: "https://attacker.example/steal" })).status, 400);
    assert.equal((await postAuthorize({ client_id: "default" })).status, 400);
    const forgedId = reg.client_id.replace(/\.[^.]+$/, ".AAAA");
    assert.equal((await postAuthorize({ client_id: forgedId })).status, 400);
    assert.equal((await postAuthorize({ code_challenge: "" })).status, 400);
    assert.equal((await postAuthorize({ code_challenge_method: "plain" })).status, 400);

    const badGet = await fetch(
      `${baseUrl}/authorize?client_id=${encodeURIComponent(reg.client_id)}` +
        `&redirect_uri=${encodeURIComponent("https://attacker.example/steal")}` +
        `&code_challenge=${codeChallenge}&code_challenge_method=S256&response_type=code`
    );
    assert.equal(badGet.status, 400);

    const state = `a&b="c"</script>`;
    const goodGet = await fetch(
      `${baseUrl}/authorize?client_id=${encodeURIComponent(reg.client_id)}` +
        `&redirect_uri=${encodeURIComponent("http://localhost:50123/callback")}` +
        `&state=${encodeURIComponent(state)}` +
        `&code_challenge=${codeChallenge}&code_challenge_method=S256&response_type=code`
    );
    assert.equal(goodGet.status, 200);
    const html = await goodGet.text();
    assert.ok(!html.includes("</script>\"") && !html.includes(`"c"</script>`), "state must not break out of the script");
    assert.ok(html.includes("Claude Code"));
    assert.ok(html.includes("localhost:50123"));

    const okAuth = await postAuthorize({ redirect_uri: "http://localhost:50123/callback" });
    assert.equal(okAuth.status, 200);
    const code = new URL(((await okAuth.json()) as { redirect_to: string }).redirect_to).searchParams.get("code");
    const wrongClient = await fetch(`${baseUrl}/token`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        grant_type: "authorization_code",
        code,
        code_verifier: codeVerifier,
        client_id: "ct_client_someone_else",
      }),
    });
    assert.equal(wrongClient.status, 401);
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

  it("executes MCP tools/list, ToolAnnotations, flat Gemini schemas, and tools/call across all workout domains", async () => {
    async function callMcp(body: Record<string, unknown>, extraHeaders: Record<string, string> = {}) {
      const res = await fetch(`${baseUrl}/mcp`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json, text/event-stream",
          Authorization: "Bearer dev-static-token-123",
          ...extraHeaders,
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

    // 0. Initialize returns batching + active-cycle server instructions
    const initResp = await callMcp({
      jsonrpc: "2.0",
      id: 0,
      method: "initialize",
      params: {
        protocolVersion: "2025-06-18",
        capabilities: {},
        clientInfo: { name: "gemini-test", version: "1.0.0" },
      },
    });
    assert.ok(
      initResp.result.instructions.includes("manage_cycle_goals"),
      "Server initialize instructions must direct LLMs to batch goal orders with manage_cycle_goals"
    );
    assert.ok(
      initResp.result.instructions.includes("get_active_cycle"),
      "Server initialize instructions must direct LLMs to resolve active cycle with get_active_cycle"
    );

    // 1. List tools and verify Gemini compatibility + ToolAnnotations on every tool
    const listResp = await callMcp({
      jsonrpc: "2.0",
      id: 1,
      method: "tools/list",
      params: {},
    });
    const tools = listResp.result.tools as Array<{
      name: string;
      annotations?: {
        readOnlyHint?: boolean;
        destructiveHint?: boolean;
        idempotentHint?: boolean;
        openWorldHint?: boolean;
      };
    }>;
    assert.equal(tools.length, 28, `Expected 28 registered tools, got ${tools.length}`);
    const toolsJson = JSON.stringify(tools);
    assert.equal(toolsJson.includes('"$ref"'), false, "Tool schemas must never contain $ref (rejected by Gemini)");
    assert.equal(
      toolsJson.includes('"exclusiveMinimum"'),
      false,
      "Tool schemas must never contain exclusiveMinimum (rejected by Gemini)"
    );

    const expectedReadOnlyTools = new Set([
      "get_active_cycle",
      "list_cycles",
      "get_workout_summary",
      "list_exercises",
      "list_routines",
      "list_sessions",
      "list_cycle_goals",
      "list_rep_maxes",
      "get_weight_momentum_analytics",
      "list_weight_entries",
      "list_daily_logs",
    ]);
    const expectedDeleteTools = new Set([
      "delete_routine",
      "delete_session",
      "delete_cycle_goal",
      "delete_weight_entry",
      "delete_daily_log",
    ]);

    for (const tool of tools) {
      assert.ok(
        tool.name.length <= 60 && /^[a-z0-9_]+$/.test(tool.name),
        `Tool name ${tool.name} must be Gemini-compatible snake_case <= 60 chars`
      );
      assert.ok(tool.annotations, `Tool ${tool.name} must declare ToolAnnotations`);
      assert.equal(tool.annotations.openWorldHint, false);
      assert.equal(tool.annotations.idempotentHint, true);
      if (expectedReadOnlyTools.has(tool.name)) {
        assert.equal(tool.annotations.readOnlyHint, true, `${tool.name} must have readOnlyHint=true`);
        assert.equal(tool.annotations.destructiveHint, false, `${tool.name} must have destructiveHint=false`);
      } else if (expectedDeleteTools.has(tool.name)) {
        assert.equal(tool.annotations.readOnlyHint, false, `${tool.name} must have readOnlyHint=false`);
        assert.equal(tool.annotations.destructiveHint, true, `${tool.name} must have destructiveHint=true`);
      } else {
        assert.equal(tool.annotations.readOnlyHint, false, `${tool.name} must have readOnlyHint=false`);
        assert.equal(tool.annotations.destructiveHint, false, `${tool.name} must have destructiveHint=false`);
      }
    }

    // Date validation is enforced at runtime even though the advertised schema has no `pattern`.
    const badDateResp = await callMcp({
      jsonrpc: "2.0",
      id: 99,
      method: "tools/call",
      params: {
        name: "upsert_session",
        arguments: { date: "2026-02-30", title: "Impossible Date", blocks: [] },
      },
    });
    assert.ok(
      badDateResp.error || badDateResp.result?.isError === true,
      `Invalid ISO date must be rejected, got: ${JSON.stringify(badDateResp)}`
    );

    // 2. Empty account defaults cleanly to Cycle 1 ("Active Cycle")
    const emptyActiveResp = await callMcp({
      jsonrpc: "2.0",
      id: 100,
      method: "tools/call",
      params: { name: "get_active_cycle", arguments: {} },
    });
    const emptyActivePayload = JSON.parse(emptyActiveResp.result.content[0].text);
    assert.equal(emptyActivePayload.activeCycle.cycleId, 1);
    assert.equal(emptyActivePayload.activeCycle.isActive, true);

    // 3. Upsert a workout session (auto-creates Back Squat)
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

    // 4. Upsert a Cycle Goal (adaptive CALORIES goal with 5-min window)
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

    // 5. Upsert Weight Entry & Daily Log + Momentum Analytics
    await callMcp({
      jsonrpc: "2.0",
      id: 6,
      method: "tools/call",
      params: {
        name: "batch_upsert_weight_entries",
        arguments: {
          entries: [
            { date: "2026-10-30", weightKg: 80.0 },
            { date: "2026-11-06", weightKg: 76.5, notes: "Morning fasted" },
          ],
        },
      },
    });

    const batchLogsResp = await callMcp({
      jsonrpc: "2.0",
      id: 5,
      method: "tools/call",
      params: {
        name: "batch_upsert_daily_logs",
        arguments: {
          logs: [
            {
              date: "2026-11-05",
              fastCompleted: false,
              isRestDay: true,
              caloriesKcal: 2100,
            },
            {
              date: "2026-11-06",
              fastCompleted: true,
              caloriesKcal: 2300,
              proteinGrams: 185,
            },
          ],
        },
      },
    });
    const batchLogsPayload = JSON.parse(batchLogsResp.result.content[0].text);
    assert.equal(batchLogsPayload.upsertedCount, 2);

    const delDailyResp = await callMcp({
      jsonrpc: "2.0",
      id: 51,
      method: "tools/call",
      params: {
        name: "delete_daily_log",
        arguments: { date: "2026-11-05" },
      },
    });
    assert.equal(JSON.parse(delDailyResp.result.content[0].text).status, "deleted");

    const momentumResp = await callMcp({
      jsonrpc: "2.0",
      id: 8,
      method: "tools/call",
      params: {
        name: "get_weight_momentum_analytics",
        arguments: {
          subcycleType: "WEIGHT_LOSS",
          cycleName: "Autumn Cut",
          cycleStartDate: "2026-10-30",
          cycleFinishDate: "2026-12-25",
          includeDailyTimeline: true,
        },
      },
    });
    const momentumPayload = JSON.parse(momentumResp.result.content[0].text);
    assert.equal(momentumPayload.cycle_metadata.cycle_name, "Autumn Cut");
    assert.equal(momentumPayload.cycle_metadata.subcycle_type, "FAT_LOSS");
    assert.equal(momentumPayload.daily_timeline.length, 8);
    assert.equal(momentumPayload.weekly_body_weight_composite.diagnostic_code, "POSSIBLE_MUSCLE_LOSS");

    // Verify SPEC-WEIGHT-CARD-02 strict inequality corridors
    const recompSlip = computeWeightMomentumAnalytics(
      [
        { isoDate: "2026-10-01", weightKg: 80.0 },
        { isoDate: "2026-10-08", weightKg: 79.3 },
      ],
      { subcycleType: "RECOMPOSITION" }
    );
    assert.equal(recompSlip.weekly_body_weight_composite.diagnostic_code, "WARNING_FAT_LOSS_PHASE");

    const muscleGainOptimal = computeWeightMomentumAnalytics(
      [
        { isoDate: "2026-10-01", weightKg: 80.0 },
        { isoDate: "2026-10-08", weightKg: 80.7 },
      ],
      { subcycleType: "MUSCLE_GAIN" }
    );
    assert.equal(muscleGainOptimal.weekly_body_weight_composite.diagnostic_code, "ON_TARGET");
  });

  it("replays the exact 6-order Gemini conversation (active cycle discovery + 2RM->3RM Snatch + 5 new goals) in 1 batch call", async () => {
    const athleteToken = await issueAccessTokenForIdToken("valid-firebase-id-token-athlete");
    const athleteCtx = { uid: "athlete-uid-001", env: "production" as const };

    // Seed multi-cycle history matching the reported user account (Cycles 1..4, Cycle 4 = Recomposition with 2RM Snatch)
    await backingStore.writeCollection<CycleGoalRecord>(athleteCtx, "cycle_goals", [
      {
        id: 1,
        cycleId: 1,
        cycleName: "Strength & Performance",
        exerciseId: 10,
        exerciseName: "Back Squat",
        targetReps: 1,
        startWeight: 120,
        targetWeight: 140,
        notes: "",
      },
      {
        id: 2,
        cycleId: 4,
        cycleName: "Recomposition",
        exerciseId: 11,
        exerciseName: "Snatch",
        targetReps: 2,
        startWeight: 35,
        targetWeight: 40,
        notes: "",
      },
    ]);

    async function callAthleteMcp(body: Record<string, unknown>) {
      const res = await fetch(`${baseUrl}/mcp`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json", // Intentionally omit text/event-stream to test Accept normalization!
          Authorization: `Bearer ${athleteToken}`,
        },
        body: JSON.stringify(body),
      });
      assert.equal(res.status, 200);
      return res.json();
    }

    // Turn 1: "Give me the active cycle" -> returns Cycle 4: Recomposition on the first try!
    const activeResp = await callAthleteMcp({
      jsonrpc: "2.0",
      id: 1,
      method: "tools/call",
      params: { name: "get_active_cycle", arguments: {} },
    });
    const activeData = JSON.parse(activeResp.result.content[0].text);
    assert.equal(activeData.activeCycle.cycleId, 4);
    assert.equal(activeData.activeCycle.cycleName, "Recomposition");
    assert.equal(activeData.activeCycle.isActive, true);
    assert.equal(activeData.goals.length, 1);
    assert.equal(activeData.goals[0].exerciseName, "Snatch");
    assert.equal(activeData.goals[0].targetReps, 2);

    // Turn 2: Execute all 6 user orders in ONE single manage_cycle_goals batch call (omitting cycleId!)
    const batchResp = await callAthleteMcp({
      jsonrpc: "2.0",
      id: 2,
      method: "tools/call",
      params: {
        name: "manage_cycle_goals",
        arguments: {
          upserts: [
            {
              exerciseName: "Snatch",
              previousTargetReps: 2,
              targetReps: 3,
              startWeight: 35,
              targetWeight: 40,
            },
            {
              exerciseName: "Incline Bench Press",
              targetReps: 3,
              startWeight: 50,
              targetWeight: 70,
            },
            {
              exerciseName: "Clean",
              targetReps: 3,
              startWeight: 55,
              targetWeight: 60,
            },
            {
              exerciseName: "Biceps Twist Curl",
              targetReps: 6,
              startWeight: 12.5,
              targetWeight: 20,
            },
            {
              exerciseName: "Cable Triceps Push Downs",
              targetReps: 6,
              targetWeight: 30,
            },
            {
              exerciseName: "Behind the Neck Cable Triceps",
              targetReps: 6,
              targetWeight: 25,
            },
          ],
        },
      },
    });
    const batchData = JSON.parse(batchResp.result.content[0].text);
    assert.equal(batchData.status, "ok");
    assert.equal(batchData.cycle.cycleId, 4);
    assert.equal(batchData.cycle.cycleName, "Recomposition");
    assert.equal(batchData.upsertedCount, 6);
    assert.equal(batchData.deletedCount, 1);
    assert.equal(batchData.deleted[0].exerciseName, "Snatch");
    assert.equal(batchData.deleted[0].targetReps, 2);
    assert.equal(batchData.deleted[0].deleted, true);

    // Verify list_cycle_goals with activeCycleOnly=true shows the 6 active Cycle 4 goals and hides tombstoned 2RM Snatch
    const goalsResp = await callAthleteMcp({
      jsonrpc: "2.0",
      id: 3,
      method: "tools/call",
      params: { name: "list_cycle_goals", arguments: { activeCycleOnly: true } },
    });
    const goalsData = JSON.parse(goalsResp.result.content[0].text);
    assert.equal(goalsData.activeCycleId, 4);
    assert.equal(goalsData.count, 6);
    assert.equal(
      goalsData.cycleGoals.some(
        (g: CycleGoalRecord) => g.exerciseName === "Snatch" && g.targetReps === 2
      ),
      false,
      "2RM Snatch must be hidden after conversion to 3RM"
    );
    assert.equal(
      goalsData.cycleGoals.some(
        (g: CycleGoalRecord) => g.exerciseName === "Snatch" && g.targetReps === 3 && g.targetWeight === 40
      ),
      true
    );

    // Verify raw Firestore backingStore retained a deletedAtMillis tombstone for 2RM Snatch (so Android Sync Now deletes it locally!)
    const rawStoredGoals = await backingStore.readCollection<CycleGoalRecord>(
      athleteCtx,
      "cycle_goals"
    );
    const snatch2RmTombstone = rawStoredGoals.find(
      (g) => g.cycleId === 4 && g.exerciseName === "Snatch" && g.targetReps === 2
    );
    assert.ok(snatch2RmTombstone, "Tombstone record for 2RM Snatch must remain in Firestore until compacted by mobile sync");
    assert.ok(
      typeof snatch2RmTombstone.deletedAtMillis === "number" &&
        snatch2RmTombstone.deletedAtMillis > 0
    );
    assert.ok(
      typeof snatch2RmTombstone.updatedAtMillis === "number" &&
        snatch2RmTombstone.updatedAtMillis > 0
    );

    // Verify explicit mobile isActiveCycle marker overrides higher cycleId heuristic
    await backingStore.writeCollection<CycleGoalRecord>(athleteCtx, "cycle_goals", [
      ...rawStoredGoals.map((g) => ({
        ...g,
        isActiveCycle: g.cycleId === 1,
        updatedAtMillis: Date.now() + 1000,
      })),
    ]);
    const switchedResp = await callAthleteMcp({
      jsonrpc: "2.0",
      id: 4,
      method: "tools/call",
      params: { name: "list_cycles", arguments: {} },
    });
    const switchedData = JSON.parse(switchedResp.result.content[0].text);
    assert.equal(
      switchedData.activeCycleId,
      1,
      "Explicit mobile isActiveCycle=true on Cycle 1 must take precedence over Cycle 4"
    );
  });

  it("preserves 100% of updates across 10 concurrent parallel mutation calls and writes tombstones for routine/session deletes", async () => {
    const athlete2Token = await issueAccessTokenForIdToken("valid-firebase-id-token-athlete-2");
    const athlete2Ctx = { uid: "athlete-uid-002", env: "production" as const };

    async function callAthlete2(id: number, name: string, args: Record<string, unknown>) {
      const res = await fetch(`${baseUrl}/mcp`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json, text/event-stream",
          Authorization: `Bearer ${athlete2Token}`,
        },
        body: JSON.stringify({
          jsonrpc: "2.0",
          id,
          method: "tools/call",
          params: { name, arguments: args },
        }),
      });
      assert.equal(res.status, 200);
      return res.json();
    }

    // Fire 10 parallel upsert_cycle_goal HTTP requests simultaneously
    await Promise.all(
      Array.from({ length: 10 }, (_, idx) =>
        callAthlete2(idx + 1, "upsert_cycle_goal", {
          exerciseName: `Parallel Lift ${idx + 1}`,
          targetReps: 5,
          targetWeight: 50 + idx * 5,
        })
      )
    );

    const listGoalsResp = await callAthlete2(20, "list_cycle_goals", {});
    const listGoalsData = JSON.parse(listGoalsResp.result.content[0].text);
    assert.equal(
      listGoalsData.count,
      10,
      `Expected all 10 concurrent goals to persist without lost updates, got ${listGoalsData.count}`
    );

    // Routine & Session tombstone deletions + revival
    await callAthlete2(21, "upsert_routine", {
      name: "Olympic Pulls",
      blocks: [{ name: "Clean Pulls 4x3", exerciseNames: ["Clean Pull"] }],
    });
    const delRout = await callAthlete2(22, "delete_routine", { name: "Olympic Pulls" });
    assert.equal(JSON.parse(delRout.result.content[0].text).status, "deleted");
    const delRoutAgain = await callAthlete2(23, "delete_routine", { name: "Olympic Pulls" });
    assert.equal(JSON.parse(delRoutAgain.result.content[0].text).status, "not_found");

    const listedRoutines = JSON.parse(
      (await callAthlete2(24, "list_routines", {})).result.content[0].text
    );
    assert.equal(listedRoutines.count, 0);
    const rawRoutines = await backingStore.readCollection<RoutineWithBlocksRecord>(
      athlete2Ctx,
      "routines"
    );
    assert.equal(rawRoutines.length, 1);
    assert.ok(rawRoutines[0].routine.deletedAtMillis && rawRoutines[0].routine.deletedAtMillis > 0);

    await callAthlete2(25, "upsert_session", {
      date: "2026-11-08",
      title: "Saturday Oly",
      blocks: [{ name: "Snatch Singles", mainExerciseName: "Snatch", sets: [{ reps: 1, weight: 80 }] }],
    });
    const delSess = await callAthlete2(26, "delete_session", {
      date: "2026-11-08",
      title: "Saturday Oly",
    });
    assert.equal(JSON.parse(delSess.result.content[0].text).status, "deleted");
    const listedSessions = JSON.parse(
      (await callAthlete2(27, "list_sessions", {})).result.content[0].text
    );
    assert.equal(listedSessions.count, 0);
    const rawSessions = await backingStore.readCollection<SessionWithBlocksRecord>(
      athlete2Ctx,
      "sessions"
    );
    assert.equal(rawSessions.length, 1);
    assert.ok(rawSessions[0].session.deletedAtMillis && rawSessions[0].session.deletedAtMillis > 0);

    // Batch exercises + batch rep maxes + workout summary
    const batchEx = await callAthlete2(28, "batch_upsert_exercises", {
      exercises: [
        { name: "Muscle Up", category: "GYMNASTICS", metricType: "REPS" },
        { name: "Row Erg", category: "MACHINE", metricType: "CALORIES" },
      ],
    });
    assert.equal(JSON.parse(batchEx.result.content[0].text).upsertedCount, 2);

    const batchPr = await callAthlete2(29, "batch_record_rep_maxes", {
      repMaxes: [
        { exerciseName: "Front Squat", reps: 1, weight: 140, date: "2026-11-08" },
        { exerciseName: "Front Squat", reps: 3, weight: 125, date: "2026-11-08" },
      ],
    });
    assert.equal(JSON.parse(batchPr.result.content[0].text).recordedCount, 2);

    const summary = JSON.parse(
      (await callAthlete2(30, "get_workout_summary", {})).result.content[0].text
    );
    assert.equal(summary.activeCycleGoalsCount, 10);
    assert.equal(summary.routinesCount, 0);
    assert.equal(summary.repMaxesCount, 2);
  });

  it("enforces Streamable HTTP header compliance and isolates per-user rate limits behind shared proxy IPs", async () => {
    const rlStore = new InMemoryBackingStore();
    const rlApp = createMcpExpressApp({
      backingStore: rlStore,
      rateLimitMaxPerMinute: 4,
      ipRateLimitMaxPerMinute: 50,
      oauthConfig: {
        jwtSecret: "rate-limit-test-secret-key-at-least-32-chars",
        verifyFirebaseIdToken: async (idToken: string) => {
          if (idToken === "tok-u1") return { uid: "u1", email: "u1@example.com" };
          if (idToken === "tok-u2") return { uid: "u2", email: "u2@example.com" };
          throw new Error("Bad token");
        },
      },
    });

    const rlServer = await new Promise<Server>((resolve) => {
      const s = rlApp.listen(0, "127.0.0.1", () => resolve(s));
    });
    const addr = rlServer.address() as { port: number };
    const rlUrl = `http://127.0.0.1:${addr.port}`;

    try {
      async function mintToken(idToken: string) {
        const reg = (await (
          await fetch(`${rlUrl}/register`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ redirect_uris: ["https://gemini.google.com/oauth/callback"] }),
          })
        ).json()) as { client_id: string };
        const verifier = base64Url(crypto.randomBytes(32));
        const challenge = base64Url(crypto.createHash("sha256").update(verifier, "ascii").digest());
        const auth = (await (
          await fetch(`${rlUrl}/authorize`, {
            method: "POST",
            headers: { "Content-Type": "application/json", Accept: "application/json" },
            body: JSON.stringify({
              id_token: idToken,
              env: "production",
              client_id: reg.client_id,
              redirect_uri: "https://gemini.google.com/oauth/callback",
              code_challenge: challenge,
              code_challenge_method: "S256",
            }),
          })
        ).json()) as { redirect_to: string };
        const code = new URL(auth.redirect_to).searchParams.get("code")!;
        const tok = (await (
          await fetch(`${rlUrl}/token`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              grant_type: "authorization_code",
              code,
              code_verifier: verifier,
              client_id: reg.client_id,
              redirect_uri: "https://gemini.google.com/oauth/callback",
            }),
          })
        ).json()) as { access_token: string };
        return tok.access_token;
      }

      const tokenU1 = await mintToken("tok-u1");
      const tokenU2 = await mintToken("tok-u2");

      // 1. CORS preflight allows & exposes Mcp-Protocol-Version
      const preflight = await fetch(`${rlUrl}/mcp`, {
        method: "OPTIONS",
        headers: {
          Origin: "https://gemini.google.com",
          "Access-Control-Request-Method": "POST",
          "Access-Control-Request-Headers": "authorization,content-type,mcp-protocol-version",
        },
      });
      assert.equal(preflight.status, 204);
      assert.ok(
        (preflight.headers.get("access-control-allow-headers") || "")
          .toLowerCase()
          .includes("mcp-protocol-version")
      );

      // 2. GET /mcp returns 405 with Allow: POST, DELETE; DELETE /mcp returns 204
      const getProbe = await fetch(`${rlUrl}/mcp`, {
        method: "GET",
        headers: {
          Authorization: `Bearer ${tokenU1}`,
          "X-Forwarded-For": "66.249.83.10",
        },
      });
      assert.equal(getProbe.status, 405);
      assert.equal(getProbe.headers.get("allow"), "POST, DELETE");

      const delProbe = await fetch(`${rlUrl}/mcp`, {
        method: "DELETE",
        headers: {
          Authorization: `Bearer ${tokenU1}`,
          "X-Forwarded-For": "66.249.83.10",
        },
      });
      assert.equal(delProbe.status, 204);

      // 3. Exhaust User 1's remaining quota (2 more calls -> 4 total) from shared proxy IP 66.249.83.10
      for (let i = 0; i < 2; i++) {
        const okRes = await fetch(`${rlUrl}/mcp`, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${tokenU1}`,
            "Mcp-Protocol-Version": "2025-06-18",
            "X-Forwarded-For": "66.249.83.10",
          },
          body: JSON.stringify({ jsonrpc: "2.0", id: i + 1, method: "tools/list", params: {} }),
        });
        assert.equal(okRes.status, 200);
        assert.equal(okRes.headers.get("mcp-protocol-version"), "2025-06-18");
      }

      // 5th call for User 1 hits 429 with Retry-After
      const throttledU1 = await fetch(`${rlUrl}/mcp`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${tokenU1}`,
          "X-Forwarded-For": "66.249.83.10",
        },
        body: JSON.stringify({ jsonrpc: "2.0", id: 99, method: "tools/list", params: {} }),
      });
      assert.equal(throttledU1.status, 429);
      assert.equal(throttledU1.headers.get("retry-after"), "60");

      // User 2 behind the EXACT SAME Google proxy IP (66.249.83.10) is completely unaffected!
      const okU2 = await fetch(`${rlUrl}/mcp`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${tokenU2}`,
          "X-Forwarded-For": "66.249.83.10",
        },
        body: JSON.stringify({ jsonrpc: "2.0", id: 1, method: "tools/list", params: {} }),
      });
      assert.equal(okU2.status, 200);
    } finally {
      await new Promise<void>((resolve, reject) =>
        rlServer.close((err) => (err ? reject(err) : resolve()))
      );
    }
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
