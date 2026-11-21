import assert from "node:assert/strict";
import crypto from "node:crypto";
import type { Server } from "node:http";
import { after, before, describe, it } from "node:test";
import { createMcpExpressApp } from "../src/index.js";
import {
  AVAILABLE_BLOCK_FORMAT,
  computeWeightMomentumAnalytics,
  decodeAvailableBlockTrackedPrs,
  decodeLegacyRoutineSlots,
  decodeLogMetricHeader,
  decodeRoutineHeaderSlots,
  defaultSetsForRoutineFormat,
  encodeAvailableBlockTrackedPrs,
  encodeNotesWithLogMetric,
  extractCleanRoutineNotes,
  formatRoutineBlockTargetSummary,
  replaceAvailableBlockTrackedPrHeader,
  stripAvailableBlockTrackedPrHeaders,
} from "../src/schemas.js";
import {
  type CycleGoalRecord,
  type DailyLogRecord,
  type ExerciseRecord,
  InMemoryBackingStore,
  isoDateToEpochDay,
  type RepMaxRecord,
  type RoutineWithBlocksRecord,
  type SessionWithBlocksRecord,
  type WeightEntryRecord,
  WorkoutDomainService,
} from "../src/store.js";
import { buildSheetsExport, type SheetsExportPayload } from "../src/sheets-export.js";
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
    assert.equal(tools.length, 45, `Expected 45 registered tools, got ${tools.length}`);
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
      "list_complexes",
      "list_available_blocks",
      "list_daily_routines",
      "get_daily_routine_progress",
    ]);
    const expectedDeleteTools = new Set([
      "delete_routine",
      "delete_session",
      "delete_cycle_goal",
      "delete_weight_entry",
      "delete_daily_log",
      "delete_complex",
      "delete_available_block",
      "delete_daily_routine",
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

    // Verify SPEC-020 dual Momentum & Pace end-of-cycle estimated finish weights
    const calibrating = computeWeightMomentumAnalytics(
      [
        { isoDate: "2026-10-01", weightKg: 80.0 },
        { isoDate: "2026-10-05", weightKg: 79.6 },
      ],
      {
        subcycleType: "FAT_LOSS",
        cycleStartDate: "2026-10-01",
        cycleFinishDate: "2026-11-26",
      }
    );
    assert.equal(calibrating.weekly_body_weight_composite.estimated_finish_weight_momentum_kg, null);
    assert.equal(calibrating.weekly_body_weight_composite.estimated_finish_weight_pace_kg, null);

    const calibrated = computeWeightMomentumAnalytics(
      [
        { isoDate: "2026-10-01", weightKg: 80.0 },
        { isoDate: "2026-10-08", weightKg: 79.3 },
      ],
      {
        subcycleType: "FAT_LOSS",
        cycleStartDate: "2026-10-01",
        cycleFinishDate: "2026-11-26",
      }
    );
    assert.ok(calibrated.weekly_body_weight_composite.estimated_finish_weight_momentum_kg !== null);
    assert.ok(calibrated.weekly_body_weight_composite.estimated_finish_weight_pace_kg !== null);
    assert.ok(
      calibrated.weekly_body_weight_composite.estimated_finish_weight_momentum_kg! <
        calibrated.weekly_body_weight_composite.current_kg!
    );
    assert.ok(
      calibrated.weekly_body_weight_composite.estimated_finish_weight_pace_kg! <
        calibrated.weekly_body_weight_composite.current_kg!
    );

    const completed = computeWeightMomentumAnalytics(
      [
        { isoDate: "2026-10-01", weightKg: 80.0 },
        { isoDate: "2026-10-08", weightKg: 79.3 },
      ],
      {
        subcycleType: "FAT_LOSS",
        cycleStartDate: "2026-10-01",
        cycleFinishDate: "2026-10-08",
        asOfDate: "2026-10-10",
      }
    );
    assert.equal(
      completed.weekly_body_weight_composite.estimated_finish_weight_momentum_kg,
      Math.round(completed.weekly_body_weight_composite.current_kg! * 10) / 10
    );
    assert.equal(
      completed.weekly_body_weight_composite.estimated_finish_weight_pace_kg,
      Math.round(completed.weekly_body_weight_composite.current_kg! * 10) / 10
    );
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

  it("never writes undefined fields on explicit-cycle rep maxes and never renames a known cycle via cycleName", async () => {
    const store = new InMemoryBackingStore();
    const domain = new WorkoutDomainService(store);
    const ctx = { uid: "cycle-name-guard-uid", env: "production" as const };
    await store.writeCollection<CycleGoalRecord>(ctx, "cycle_goals", [
      { id: 1, cycleId: 2, cycleName: "Foundation", isActiveCycle: false, exerciseId: 1, exerciseName: "Snatch", targetReps: 1, startWeight: 60, targetWeight: 70, notes: "" },
      { id: 2, cycleId: 4, cycleName: "Recomposition", isActiveCycle: true, exerciseId: 1, exerciseName: "Snatch", targetReps: 3, startWeight: 65, targetWeight: 75, notes: "" },
    ]);

    // Explicit non-active cycleId: Firestore Admin rejects `undefined`, so every field must be defined.
    const [explicitRm, defaultRm] = await domain.batchRecordRepMaxes(ctx, [
      { exerciseName: "Snatch", reps: 1, weight: 72, date: "2026-11-01", cycleId: 2 },
      { exerciseName: "Snatch", reps: 3, weight: 70, date: "2026-11-02" },
    ]);
    for (const rm of [explicitRm, defaultRm]) {
      for (const [key, value] of Object.entries(rm)) {
        assert.notEqual(value, undefined, `rep max field '${key}' must not be undefined`);
      }
    }
    assert.equal(explicitRm.cycleId, 2);
    assert.equal(explicitRm.cycleName, "Foundation");
    assert.equal(explicitRm.isActiveCycle, false);
    assert.equal(defaultRm.cycleId, 4);
    assert.equal(defaultRm.cycleName, "Recomposition");
    assert.equal(defaultRm.isActiveCycle, true);
    const storedRms = await store.readCollection<RepMaxRecord>(ctx, "rep_maxes");
    assert.equal(storedRms.length, 2);

    // An unknown cycleName targets the active cycle without renaming it.
    const res = await domain.manageCycleGoals(ctx, {
      cycleName: "Recompositon Typo",
      upserts: [{ exerciseName: "Clean", targetReps: 2, targetWeight: 90 }],
    });
    assert.equal(res.cycle.cycleId, 4);
    assert.equal(res.cycle.cycleName, "Recomposition");
    const cycles = await domain.listCycles(ctx);
    assert.equal(cycles.activeCycleId, 4);
    assert.equal(cycles.activeCycleName, "Recomposition");

    // A mismatched name on an explicit known cycleId also keeps the synced name.
    const explicit = await domain.manageCycleGoals(ctx, {
      cycleId: 2,
      cycleName: "Renamed Foundation",
      upserts: [{ exerciseName: "Clean", targetReps: 1, targetWeight: 95 }],
    });
    assert.equal(explicit.cycle.cycleName, "Foundation");
  });

  it("supports Routine Builder v2 mixed metrics, 0 (Skip) per-set targets, sequence repeats, all-zero validation, and legacy compatibility", async () => {
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
        const dataLine = text.split("\n").find((l) => l.startsWith("data: "));
        assert.ok(dataLine);
        return JSON.parse(dataLine.slice(6));
      }
      return res.json();
    }

    // 1. Upsert structured v2 routine with GYMNASTICS + METCON, mixed metrics, 0 (Skip), and repeated sequence exercises
    const upsertResp = await callMcp({
      jsonrpc: "2.0",
      id: 701,
      method: "tools/call",
      params: {
        name: "upsert_routine",
        arguments: {
          name: "Thursday Gymnastics & Mixed Engine",
          description: "Skill capacity + mixed modal intervals",
          blocks: [
            {
              name: "Gymnastics Skill EMOM",
              kind: "GYMNASTICS",
              format: "EMOM",
              setsCount: 4,
              notes: "Crisp hollow positions",
              exercises: [
                {
                  name: "Ring Muscle-Up",
                  category: "GYMNASTICS",
                  metric: "REPS",
                  mode: "PER_SET",
                  perSetValues: [5, 0, 4, 0],
                },
                {
                  name: "L-Sit Hold",
                  category: "GYMNASTICS",
                  metric: "TIME_SECONDS",
                  mode: "PER_SET",
                  perSetValues: [0, 30, 0, 30],
                },
              ],
            },
            {
              name: "Shuttle Sandwich",
              kind: "METCON",
              format: "E3MOM",
              setsCount: 3,
              notes: "Negative split last round",
              exercises: [
                {
                  name: "Shuttle Run",
                  metric: "METERS",
                  mode: "FIXED",
                  fixedValue: 200,
                },
                {
                  name: "SkiErg",
                  metric: "CALORIES",
                  mode: "PER_SET",
                  perSetValues: [18, 15, 12],
                },
                {
                  name: "Shuttle Run",
                  metric: "METERS",
                  mode: "FIXED",
                  fixedValue: 100,
                },
              ],
            },
          ],
        },
      },
    });

    const upsertPayload = JSON.parse(upsertResp.result.content[0].text);
    assert.equal(upsertPayload.status, "ok");
    const savedRoutine = upsertPayload.routine as RoutineWithBlocksRecord;
    assert.equal(savedRoutine.blocks.length, 2);

    const b0 = savedRoutine.blocks[0];
    assert.equal(b0.kind, "GYMNASTICS");
    assert.equal(b0.cleanNotes, "Crisp hollow positions");
    assert.ok(b0.notes.startsWith("[rb_v2=REPS:PER_SET:5:5.0.4.0;TIME_SECONDS:PER_SET:30:0.30.0.30]"));
    assert.equal(b0.exercises?.length, 2);
    assert.deepEqual(b0.exercises?.[0]?.perSetValues, [5, 0, 4, 0]);
    assert.equal(b0.exercises?.[0]?.summary, "5 • 0 • 4 • 0 reps");
    assert.deepEqual(b0.exercises?.[1]?.perSetValues, [0, 30, 0, 30]);
    assert.equal(b0.exercises?.[1]?.summary, "0 • 30 • 0 • 30s");

    const b1 = savedRoutine.blocks[1];
    assert.equal(b1.exerciseNamesCsv, "Shuttle Run,SkiErg,Shuttle Run");
    assert.equal(b1.exercises?.length, 3);
    assert.equal(b1.exercises?.[0]?.fixedValue, 200);
    assert.equal(b1.exercises?.[1]?.summary, "18 • 15 • 12 cal");
    assert.equal(b1.exercises?.[2]?.fixedValue, 100);

    // Verify auto-created exercises received inferred metricType and zero undefined fields in Firestore
    const rawRoutines = await backingStore.readCollection<RoutineWithBlocksRecord>(
      { uid: "dev-user-uid", env: "production" },
      "routines"
    );
    const persisted = rawRoutines.find((r) => r.routine.name === "Thursday Gymnastics & Mixed Engine");
    assert.ok(persisted);
    for (const rawBlk of persisted.blocks) {
      for (const [k, v] of Object.entries(rawBlk)) {
        assert.notEqual(v, undefined, `RoutineBlock field '${k}' must not be undefined`);
      }
    }

    const listExResp = await callMcp({
      jsonrpc: "2.0",
      id: 702,
      method: "tools/call",
      params: { name: "list_exercises", arguments: { search: "SkiErg" } },
    });
    const skiErgEx = JSON.parse(listExResp.result.content[0].text).exercises[0];
    assert.equal(skiErgEx.metricType, "CALORIES");
    assert.equal(skiErgEx.category, "MACHINE");

    // 2. Reject all-zero exercise targets (`fixedValue: 0` or `perSetValues: [0, 0, 0]`)
    const allZeroResp = await callMcp({
      jsonrpc: "2.0",
      id: 703,
      method: "tools/call",
      params: {
        name: "upsert_routine",
        arguments: {
          name: "Invalid All Zero Routine",
          blocks: [
            {
              name: "Broken Block",
              setsCount: 3,
              exercises: [
                {
                  name: "SkiErg",
                  metric: "CALORIES",
                  mode: "PER_SET",
                  perSetValues: [0, 0, 0],
                },
              ],
            },
          ],
        },
      },
    });
    assert.equal(allZeroResp.result?.isError, true, "All-zero perSetValues must be rejected");

    // 3. Legacy `exerciseNames` + `targetRepsScheme` remains 100% compatible and returns decoded exercises
    await callMcp({
      jsonrpc: "2.0",
      id: 704,
      method: "tools/call",
      params: {
        name: "upsert_routine",
        arguments: {
          name: "Legacy Fran Template",
          blocks: [
            {
              name: "Fran",
              kind: "METCON",
              format: "For Time",
              setsCount: 3,
              targetRepsScheme: "21-15-9",
              exerciseNames: ["Thruster", "Pull-Up"],
              notes: "Unbroken round 1",
            },
          ],
        },
      },
    });

    const listRoutinesResp = await callMcp({
      jsonrpc: "2.0",
      id: 705,
      method: "tools/call",
      params: { name: "list_routines", arguments: {} },
    });
    const allRoutines = JSON.parse(listRoutinesResp.result.content[0].text)
      .routines as RoutineWithBlocksRecord[];
    const legacyFran = allRoutines.find((r) => r.routine.name === "Legacy Fran Template");
    assert.ok(legacyFran);
    assert.equal(legacyFran.blocks[0].cleanNotes, "Unbroken round 1");
    assert.equal(legacyFran.blocks[0].exercises?.length, 2);
    assert.deepEqual(legacyFran.blocks[0].exercises?.[0]?.perSetValues, [21, 15, 9]);

    // 4. Available Blocks CRUD + log_block_session (weight-only + unloaded + PR attribution)
    const upsertBlockResp = await callMcp({
      jsonrpc: "2.0",
      id: 706,
      method: "tools/call",
      params: {
        name: "upsert_available_block",
        arguments: {
          title: "Strength Wave A",
          instructions: "Build across 3 working sets",
          exercises: ["Back Squat", "Box Jump"],
          trackedPrs: [{ movementName: "Back Squat", rmReps: 3 }],
        },
      },
    });
    const upsertedBlockPayload = JSON.parse(upsertBlockResp.result.content[0].text);
    assert.equal(upsertedBlockPayload.status, "ok");
    assert.equal(upsertedBlockPayload.availableBlock.title, "Strength Wave A");
    assert.equal(upsertedBlockPayload.availableBlock.isAvailableBlockFormat, true);
    assert.equal(upsertedBlockPayload.availableBlock.trackedPrs.length, 1);

    const batchUpsertBlockResp = await callMcp({
      jsonrpc: "2.0",
      id: 707,
      method: "tools/call",
      params: {
        name: "batch_upsert_available_blocks",
        arguments: {
          blocks: [
            {
              title: "Olympic Complex B",
              instructions: "1 Power Clean + 1 Front Squat",
              exercises: ["Power Clean", "Front Squat"],
            },
            {
              title: "Bodyweight Finisher",
              instructions: "3 rounds max unbroken",
              exercises: ["Pull-Up", "Burpee"],
            },
          ],
        },
      },
    });
    assert.equal(JSON.parse(batchUpsertBlockResp.result.content[0].text).upsertedCount, 2);

    const logBlockResp = await callMcp({
      jsonrpc: "2.0",
      id: 708,
      method: "tools/call",
      params: {
        name: "log_block_session",
        arguments: {
          date: "2026-11-12",
          blockTitle: "Strength Wave A",
          exerciseLogs: [
            { exerciseName: "Back Squat", weights: [100, 110, 122.5] },
            { exerciseName: "Box Jump", weights: [], setCountForEmpty: 3 },
          ],
          recordedPrs: [
            { movementName: "Back Squat", rmReps: 1, weight: 122.5 },
          ],
        },
      },
    });
    const logBlockPayload = JSON.parse(logBlockResp.result.content[0].text);
    assert.equal(logBlockPayload.status, "ok");
    assert.equal(logBlockPayload.session.blocks.length, 2);
    assert.deepEqual(
      logBlockPayload.session.blocks[0].sets.map((s: { reps: number; weight: number }) => [s.reps, s.weight]),
      [
        [1, 100],
        [1, 110],
        [1, 122.5],
      ]
    );
    assert.equal(logBlockPayload.session.blocks[1].sets.length, 3);
    assert.equal(logBlockPayload.session.blocks[1].sets[0].weight, 0);
    assert.equal(logBlockPayload.recordedPrsCount, 1);
    assert.equal(logBlockPayload.recordedPrs[0].sourceBlockTitle, "Strength Wave A");
    assert.ok(logBlockPayload.recordedPrs[0].blockId > 0);
    // Auto-inherited 1RM PR link alongside existing 3RM
    assert.equal(logBlockPayload.updatedTrackedPrs.length, 2);

    const listBlocksResp = await callMcp({
      jsonrpc: "2.0",
      id: 709,
      method: "tools/call",
      params: {
        name: "list_available_blocks",
        arguments: { query: "Strength Wave", includeLastSession: true },
      },
    });
    const listBlocksPayload = JSON.parse(listBlocksResp.result.content[0].text);
    assert.equal(listBlocksPayload.count, 1);
    assert.equal(listBlocksPayload.availableBlocks[0].lastSession.date, "2026-11-12");
    assert.equal(listBlocksPayload.availableBlocks[0].lastSession.topWeight, 122.5);
    assert.equal(listBlocksPayload.availableBlocks[0].trackedPrs.length, 2);

    const summaryAfterBlocksResp = await callMcp({
      jsonrpc: "2.0",
      id: 710,
      method: "tools/call",
      params: { name: "get_workout_summary", arguments: {} },
    });
    const summaryAfterBlocks = JSON.parse(summaryAfterBlocksResp.result.content[0].text);
    assert.ok(summaryAfterBlocks.availableBlockCount >= 3);

    const deleteBlockResp = await callMcp({
      jsonrpc: "2.0",
      id: 711,
      method: "tools/call",
      params: {
        name: "delete_available_block",
        arguments: { title: "Strength Wave A" },
      },
    });
    const deleteBlockPayload = JSON.parse(deleteBlockResp.result.content[0].text);
    assert.equal(deleteBlockPayload.status, "deleted");

    // Historical sessions remain intact after Available Block deletion
    const sessionsAfterDeleteResp = await callMcp({
      jsonrpc: "2.0",
      id: 712,
      method: "tools/call",
      params: { name: "list_sessions", arguments: { startDate: "2026-11-12", endDate: "2026-11-12" } },
    });
    const sessionsAfterDelete = JSON.parse(sessionsAfterDeleteResp.result.content[0].text);
    assert.ok(
      sessionsAfterDelete.sessions.some(
        (s: { session: { title: string } }) => s.session.title === "Strength Wave A"
      ),
      "Historical session must remain preserved after block template deletion"
    );

    // 5. Logging a PR against a legacy structured daily routine only swaps the `[ab_prs=...]` header and keeps
    //    the routine's own block notes, `[rb_v2=...]` targets, and secondary blocks intact.
    const legacyLogResp = await callMcp({
      jsonrpc: "2.0",
      id: 713,
      method: "tools/call",
      params: {
        name: "log_block_session",
        arguments: {
          date: "2026-11-13",
          blockTitle: "Thursday Gymnastics & Mixed Engine",
          exerciseLogs: [{ exerciseName: "Ring Muscle-Up", weights: [] }],
          recordedPrs: [{ movementName: "Ring Muscle-Up", rmReps: 1, weight: 10 }],
        },
      },
    });
    const legacyLogPayload = JSON.parse(legacyLogResp.result.content[0].text);
    assert.equal(legacyLogPayload.updatedTrackedPrs.length, 1);

    const routinesAfterLegacyLogResp = await callMcp({
      jsonrpc: "2.0",
      id: 714,
      method: "tools/call",
      params: { name: "list_routines", arguments: {} },
    });
    const legacyAfterLog = (
      JSON.parse(routinesAfterLegacyLogResp.result.content[0].text).routines as RoutineWithBlocksRecord[]
    ).find((r) => r.routine.name === "Thursday Gymnastics & Mixed Engine");
    assert.ok(legacyAfterLog);
    const legacyB0 = legacyAfterLog.blocks[0];
    assert.ok(legacyB0.notes.startsWith("[rb_v2=REPS:PER_SET:5:5.0.4.0;TIME_SECONDS:PER_SET:30:0.30.0.30]"));
    assert.equal(legacyB0.cleanNotes, "Crisp hollow positions");
    assert.deepEqual(legacyB0.exercises?.[0]?.perSetValues, [5, 0, 4, 0]);
    assert.deepEqual(decodeAvailableBlockTrackedPrs(legacyB0.notes), [
      { movementName: "Ring Muscle-Up", rmReps: 1 },
    ]);
    assert.equal(legacyAfterLog.blocks[1].cleanNotes, "Negative split last round");
  });

  it("keeps Available Blocks [ab_prs=...] codec in parity with Android AvailableBlockCodec", () => {
    const encoded = encodeAvailableBlockTrackedPrs("EMOM 10\nOdd: 3 Back Squat", [
      { movementName: "Back Squat", rmReps: 3 },
      { movementName: "Clean & Jerk", rmReps: 1 },
      { movementName: "back squat", rmReps: 3 },
    ]);
    assert.ok(encoded.startsWith("[ab_prs="));
    assert.equal(AVAILABLE_BLOCK_FORMAT, "AVAILABLE_BLOCK");
    assert.equal(extractCleanRoutineNotes(encoded), "EMOM 10\nOdd: 3 Back Squat");
    const decoded = decodeAvailableBlockTrackedPrs(encoded);
    assert.deepEqual(decoded, [
      { movementName: "Back Squat", rmReps: 3 },
      { movementName: "Clean & Jerk", rmReps: 1 },
    ]);
    const androidFormatted = "[ab_prs=Back+Squat:5;Clean+%26+Jerk:2]\nBuild heavy";
    assert.deepEqual(decodeAvailableBlockTrackedPrs(androidFormatted), [
      { movementName: "Back Squat", rmReps: 5 },
      { movementName: "Clean & Jerk", rmReps: 2 },
    ]);
    assert.equal(extractCleanRoutineNotes(androidFormatted), "Build heavy");

    // Replacing tracked PRs keeps a leading `[rb_v2=...]` header first and drops stale `[ab_prs=...]` lines.
    const legacyNotes = "[rb_v2=REPS:PER_SET:10:21.15.9]\n[ab_prs=Thruster:3]\nUnbroken round 1";
    const replaced = replaceAvailableBlockTrackedPrHeader(legacyNotes, [
      { movementName: "Thruster", rmReps: 1 },
    ]);
    assert.equal(
      replaced,
      "[rb_v2=REPS:PER_SET:10:21.15.9]\n[ab_prs=Thruster:1]\nUnbroken round 1"
    );
    assert.equal(extractCleanRoutineNotes(replaced), "Unbroken round 1");
    assert.equal(
      stripAvailableBlockTrackedPrHeaders(legacyNotes),
      "[rb_v2=REPS:PER_SET:10:21.15.9]\nUnbroken round 1"
    );
    assert.equal(replaceAvailableBlockTrackedPrHeader("Plain notes", []), "Plain notes");
  });

  it("keeps Routine Builder v2 codec helpers in parity with Android RoutineTargetCodec", () => {
    for (const fmt of ["AMRAP", "For Time", "Chipper", "Death by", "Time cap"]) {
      assert.equal(defaultSetsForRoutineFormat(fmt), 1, `Expected 1 set for ${fmt}`);
    }
    assert.equal(defaultSetsForRoutineFormat("Max Effort"), 4);
    assert.equal(defaultSetsForRoutineFormat("Tabata"), 8);
    assert.equal(defaultSetsForRoutineFormat("EMOM"), 4);

    assert.equal(
      formatRoutineBlockTargetSummary(
        [
          { metric: "TIME_SECONDS", mode: "FIXED", fixedValue: 120, perSetValues: [120, 120] },
          { metric: "METERS", mode: "FIXED", fixedValue: 1000, perSetValues: [1000, 1000] },
          { metric: "REPS", mode: "FIXED", fixedValue: 1, perSetValues: [1, 1] },
        ],
        2
      ),
      "2m + 1km + 1 rep"
    );

    assert.deepEqual(decodeLegacyRoutineSlots("5,5,3", 3, ["REPS"])[0].perSetValues, [5, 5, 3]);
    assert.deepEqual(decodeLegacyRoutineSlots("3-2-1-3-2-1", 4, ["REPS"])[0].perSetValues, [3, 2, 1, 3, 2, 1]);
    assert.deepEqual(decodeLegacyRoutineSlots("21-15-9", 1, ["REPS"])[0].perSetValues, [21, 15, 9]);
    assert.deepEqual(decodeLegacyRoutineSlots("5x3", 1, ["REPS"])[0], {
      metric: "REPS",
      mode: "FIXED",
      fixedValue: 3,
      perSetValues: [3, 3, 3, 3, 3],
    });
    const complex = decodeLegacyRoutineSlots("1 / 2", 5, ["REPS", "TIME_SECONDS"]);
    assert.deepEqual(complex.map((s) => s.fixedValue), [1, 2]);
    assert.equal(complex[1].metric, "TIME_SECONDS");
    assert.equal(decodeLegacyRoutineSlots("TRISET_1", 4, ["CALORIES"])[0].fixedValue, 15);
  });

  it("update_block_session_log reweights sets, reconciles/tombstones rep_maxes, and inherits new tracked PRs", async () => {
    let msgId = 900;
    const callTool = async (name: string, args: Record<string, unknown>) => {
      const id = msgId++;
      const res = await fetch(`${baseUrl}/mcp`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json, text/event-stream",
          Authorization: "Bearer dev-static-token-123",
        },
        body: JSON.stringify({
          jsonrpc: "2.0",
          id,
          method: "tools/call",
          params: { name, arguments: args },
        }),
      });
      assert.equal(res.status, 200);
      const contentType = res.headers.get("content-type") || "";
      if (contentType.includes("text/event-stream")) {
        const text = await res.text();
        const dataLine = text.split("\n").find((l) => l.startsWith("data: "));
        assert.ok(dataLine);
        return JSON.parse(dataLine.slice(6));
      }
      return res.json();
    };

    const blockResp = await callTool("upsert_available_block", {
      title: "Hang Snatch Wave",
      instructions: "Wave 3RM",
      exercises: ["Hang Snatch"],
      trackedPrs: [{ movementName: "Hang Snatch", rmReps: 3 }],
    });
    const blockId = JSON.parse(blockResp.result.content[0].text).availableBlock.id as number;

    const logResp = await callTool("log_block_session", {
      date: "2026-11-14",
      blockId,
      exerciseLogs: [{ exerciseName: "Hang Snatch", weights: [32.5, 35] }],
      recordedPrs: [{ movementName: "Hang Snatch", rmReps: 3, weight: 35 }],
    });
    const logged = JSON.parse(logResp.result.content[0].text);
    const sessionId = logged.session.session.id as number;
    const initial3RmId = logged.recordedPrs[0].id as number;

    const summaryBeforeResp = await callTool("get_workout_summary", {});
    const summaryBefore = JSON.parse(summaryBeforeResp.result.content[0].text);
    const baselineRepMaxCount = summaryBefore.repMaxesCount as number;

    // 1. Reweight sets + 3RM PR (35 -> 37.5) and add new 1RM PR (42.5)
    const editResp = await callTool("update_block_session_log", {
      sessionId,
      blockId,
      exercises: [{ exerciseName: "Hang Snatch", weights: [32.5, 37.5, 42.5] }],
      trackedPrs: [
        { movementName: "Hang Snatch", rmReps: 3, weight: 37.5 },
        { movementName: "Hang Snatch", rmReps: 1, weight: 42.5 },
      ],
    });
    const editPayload = JSON.parse(editResp.result.content[0].text);
    assert.equal(editPayload.status, "ok");
    assert.equal(editPayload.reconciledPrsCount, 2);
    assert.ok(editPayload.tombstonedRepMaxIds.includes(initial3RmId));

    // Active rep max list hides tombstoned 35 kg record and returns 37.5 & 42.5
    const activeRmsResp = await callTool("list_rep_maxes", { exerciseName: "Hang Snatch" });
    const activeRms = JSON.parse(activeRmsResp.result.content[0].text).repMaxes as Array<{
      id: number;
      reps: number;
      weight: number;
    }>;
    assert.deepEqual(
      activeRms.map((r) => [r.reps, r.weight]).sort((a, b) => a[0] - b[0]),
      [
        [1, 42.5],
        [3, 37.5],
      ]
    );

    // Summary repMaxesCount reflects +1 net active PR (35 tombstoned, 37.5 + 42.5 active)
    const summaryAfterEditResp = await callTool("get_workout_summary", {});
    const summaryAfterEdit = JSON.parse(summaryAfterEditResp.result.content[0].text);
    assert.equal(summaryAfterEdit.repMaxesCount, baselineRepMaxCount + 1);

    // Raw backing storage retains the tombstoned 35 kg row so Android sync deletes it cleanly
    const [prodRms, snapRms] = await Promise.all([
      backingStore.readCollection<{ id: number; weight: number; deletedAtMillis?: number | null }>(
        { uid: "dev-user-uid", env: "production" },
        "rep_maxes"
      ),
      backingStore.readCollection<{ id: number; weight: number; deletedAtMillis?: number | null }>(
        { uid: "dev-user-uid", env: "snapshot" },
        "rep_maxes"
      ),
    ]);
    const rawRms = [...prodRms, ...snapRms];
    const tombstonedRow = rawRms.find((r) => r.id === initial3RmId);
    assert.ok(tombstonedRow && (tombstonedRow.deletedAtMillis ?? 0) > 0);

    // Parent Available Block inherited the new 1RM slot while preserving instructions
    const blocksResp = await callTool("list_available_blocks", { query: "Hang Snatch Wave" });
    const updatedBlock = JSON.parse(blocksResp.result.content[0].text).availableBlocks[0];
    assert.deepEqual(
      updatedBlock.trackedPrs.map((s: { rmReps: number }) => s.rmReps),
      [3, 1]
    );
    assert.equal(updatedBlock.instructions, "Wave 3RM");

    // 2. Explicitly remove 1RM PR and verify it is tombstoned & excluded, then revivable via record_rep_max
    const removePrResp = await callTool("update_block_session_log", {
      sessionId,
      blockId,
      trackedPrs: [
        { movementName: "Hang Snatch", rmReps: 3, weight: 37.5 },
        { movementName: "Hang Snatch", rmReps: 1, remove: true },
      ],
    });
    const removePrPayload = JSON.parse(removePrResp.result.content[0].text);
    assert.equal(removePrPayload.reconciledPrsCount, 1);

    const activeAfterRemoveResp = await callTool("list_rep_maxes", { exerciseName: "Hang Snatch" });
    const activeAfterRemove = JSON.parse(activeAfterRemoveResp.result.content[0].text).repMaxes as Array<{
      reps: number;
      weight: number;
    }>;
    assert.deepEqual(
      activeAfterRemove.map((r) => [r.reps, r.weight]),
      [[3, 37.5]]
    );

    // Reviving the exact tombstoned natural key via record_rep_max clears deletedAtMillis
    await callTool("record_rep_max", {
      exerciseName: "Hang Snatch",
      reps: 1,
      weight: 42.5,
      date: "2026-11-14",
    });
    const activeAfterReviveResp = await callTool("list_rep_maxes", { exerciseName: "Hang Snatch" });
    const activeAfterRevive = JSON.parse(activeAfterReviveResp.result.content[0].text).repMaxes as Array<{
      reps: number;
      weight: number;
      deletedAtMillis?: number | null;
    }>;
    assert.equal(activeAfterRevive.length, 2);
    assert.equal(activeAfterRevive.find((r) => r.reps === 1)?.deletedAtMillis ?? null, null);
  });

  it("Daily Routines MCP parity: CRUD, dr: vs ab: isolation, log_daily_routine_session with template sync & get_daily_routine_progress", async () => {
    let msgId = 950;
    const callTool = async (name: string, argumentsObj: Record<string, unknown>) => {
      const id = msgId++;
      const res = await fetch(`${baseUrl}/mcp`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json, text/event-stream",
          Authorization: "Bearer dev-static-token-123",
        },
        body: JSON.stringify({
          jsonrpc: "2.0",
          id,
          method: "tools/call",
          params: { name, arguments: argumentsObj },
        }),
      });
      assert.equal(res.status, 200);
      const contentType = res.headers.get("content-type") || "";
      const resp = contentType.includes("text/event-stream")
        ? JSON.parse(
            (await res.text())
              .split("\n")
              .find((l) => l.startsWith("data: "))!
              .slice(6)
          )
        : await res.json();
      assert.ok(!resp.error, `Tool ${name} returned JSON-RPC error: ${JSON.stringify(resp.error)}`);
      assert.notEqual(
        resp.result?.isError,
        true,
        `Tool ${name} returned error result: ${JSON.stringify(resp.result)}`
      );
      return JSON.parse(resp.result.content[0].text);
    };

    // 1. Seed a Saved Available Block named "Lower Power"
    const blockPayload = await callTool("upsert_available_block", {
      title: "Lower Power",
      instructions: "Warm up + 3x5 Heavy",
      exercises: ["Back Squat"],
      trackedPrs: [{ movementName: "Back Squat", rmReps: 5 }],
    });
    const savedBlockId = blockPayload.availableBlock.id as number;
    assert.ok(savedBlockId > 0);

    // 2. Create a Daily Routine with the EXACT SAME title "Lower Power" (dr: vs ab: isolation)
    const routineUpsert = await callTool("upsert_daily_routine", {
      title: "Lower Power",
      notes: "Primary leg day",
      items: [
        { item_type: "BLOCK", block_id: savedBlockId },
        {
          item_type: "EXERCISE",
          exercise_name: "Romanian Deadlift",
          note: "3s eccentric · RIR 2",
          default_tracked_prs: [{ movementName: "Romanian Deadlift", rmReps: 8 }],
        },
        {
          item_type: "EXERCISE",
          exercise_name: "Seated Calf Raise",
          note: "Full stretch",
        },
      ],
    });
    const dailyRoutine = routineUpsert.dailyRoutine;
    assert.ok(dailyRoutine.id > 0);
    assert.notEqual(dailyRoutine.id, savedBlockId, "Daily Routine must not overwrite same-named Available Block");
    assert.equal(dailyRoutine.blockCount, 1);
    assert.equal(dailyRoutine.exerciseCount, 2);
    assert.equal(dailyRoutine.lastSessionDate, null);

    // 3. list_available_blocks excludes DAILY_ROUTINE, and get_workout_summary reports dailyRoutineCount
    const blocksList = await callTool("list_available_blocks", {});
    assert.equal(
      blocksList.availableBlocks.filter((b: { title: string }) => b.title === "Lower Power").length,
      1,
      "Only the Available Block should appear in list_available_blocks"
    );
    const routinesList = await callTool("list_daily_routines", { query: "Lower Power" });
    assert.equal(routinesList.count, 1);
    assert.equal(routinesList.dailyRoutines[0].items.length, 3);

    const summaryBefore = await callTool("get_workout_summary", {});
    assert.ok(summaryBefore.dailyRoutineCount >= 1);

    // 4. Log Session #1 from the Daily Routine with an extra exercise + update_routine_template=true + achieved PR
    const log1 = await callTool("log_daily_routine_session", {
      date: "2026-11-10",
      routine_id: dailyRoutine.id,
      update_routine_template: true,
      logged_items: [
        {
          item_type: "BLOCK",
          block_id: savedBlockId,
          exercise_logs: [{ exerciseName: "Back Squat", weights: [110, 115, 120] }],
          achieved_prs: [{ movement_name: "Back Squat", rm_reps: 5, weight_kg: 120 }],
        },
        {
          item_type: "EXERCISE",
          exercise_name: "Romanian Deadlift",
          note: "3s eccentric · RIR 2",
          set_weights_kg: [90, 95, 95],
        },
        {
          item_type: "EXERCISE",
          exercise_name: "Weighted Plank",
          note: "3x45s",
          set_weights_kg: [20, 20],
        },
      ],
    });
    assert.equal(log1.recordedPrsCount, 1);
    assert.equal(log1.session.blocks.length, 3);
    assert.equal(log1.session.blocks[0].block.routineId, savedBlockId);
    assert.equal(log1.session.blocks[1].block.format, "STANDALONE_EXERCISE");
    assert.equal(log1.session.blocks[1].block.routineId, null);
    assert.equal(log1.session.blocks[1].block.description, "3s eccentric · RIR 2");
    assert.ok(log1.updatedRoutineTemplate);
    assert.equal(log1.updatedRoutineTemplate.items.length, 3);
    assert.equal(log1.updatedRoutineTemplate.items[2].exerciseName, "Weighted Plank");

    // 5. Log an ad-hoc standalone session for Romanian Deadlift on 2026-11-15 to test cross-app progress
    await callTool("log_daily_routine_session", {
      date: "2026-11-15",
      session_title: "Quick Pull",
      logged_items: [
        {
          item_type: "EXERCISE",
          exercise_name: "Romanian Deadlift",
          set_weights_kg: [100, 102.5],
        },
      ],
    });

    // 6. get_daily_routine_progress unifies history across routine and standalone sessions
    const progress = await callTool("get_daily_routine_progress", { routine_id: dailyRoutine.id });
    assert.equal(progress.dailyRoutine.title, "Lower Power");
    assert.equal(progress.items.length, 3);
    const squatProg = progress.items[0];
    assert.equal(squatProg.itemType, "COMPLEX");
    assert.equal(squatProg.complexId, savedBlockId);
    assert.equal(squatProg.blockId, savedBlockId);
    assert.equal(squatProg.allTimeTopKg, 120);
    const rdlProg = progress.items[1];
    assert.equal(rdlProg.itemType, "EXERCISE");
    assert.equal(rdlProg.allTimeTopKg, 102.5);
    assert.equal(rdlProg.lastTopKg, 102.5);
    assert.equal(rdlProg.topDeltaKg, 7.5);
    assert.equal(rdlProg.chartPoints.length, 2);

    // 7. Deleting the Daily Routine leaves both the same-named Available Block and historical sessions intact
    const delResp = await callTool("delete_daily_routine", { routine_id: dailyRoutine.id });
    assert.equal(delResp.status, "deleted");
    assert.deepEqual(delResp.deletedTitles, ["Lower Power"]);

    const remainingBlocks = await callTool("list_available_blocks", { query: "Lower Power" });
    assert.equal(remainingBlocks.count, 1);
    assert.equal(remainingBlocks.availableBlocks[0].id, savedBlockId);

    const remainingRoutines = await callTool("list_daily_routines", { query: "Lower Power" });
    assert.equal(remainingRoutines.count, 0);
  });

  it("SPEC-026 multi-metric codec & MCP parity: optional Daily Routine targets, all-zero rejection, multi-metric session logging/editing & progress", async () => {
    // 1. Header order scanning & [log_metric=...] codec parity with Android RoutineTargetCodec
    const rbFirst = "[rb_v2=CALORIES:PER_SET:15:15.0.20]\n[dr_item=EXERCISE:id=42:name=SkiErg]\nSprint crisp";
    const drFirst = "[dr_item=EXERCISE:id=42:name=SkiErg]\n[rb_v2=CALORIES:PER_SET:15:15.0.20]\nSprint crisp";
    assert.equal(extractCleanRoutineNotes(rbFirst), "Sprint crisp");
    assert.equal(extractCleanRoutineNotes(drFirst), "Sprint crisp");
    assert.deepEqual(decodeRoutineHeaderSlots(rbFirst, 3), decodeRoutineHeaderSlots(drFirst, 3));
    const withLogMetric = encodeNotesWithLogMetric("[log_metric=TIME_SECONDS]\nHold hollow", "CALORIES");
    assert.equal(withLogMetric, "[log_metric=CALORIES]\nHold hollow");
    assert.equal(decodeLogMetricHeader(withLogMetric), "CALORIES");
    assert.equal(extractCleanRoutineNotes(withLogMetric), "Hold hollow");

    let msgId = 1100;
    const callToolRaw = async (name: string, argumentsObj: Record<string, unknown>) => {
      const id = msgId++;
      const res = await fetch(`${baseUrl}/mcp`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json, text/event-stream",
          Authorization: "Bearer dev-static-token-123",
        },
        body: JSON.stringify({
          jsonrpc: "2.0",
          id,
          method: "tools/call",
          params: { name, arguments: argumentsObj },
        }),
      });
      assert.equal(res.status, 200);
      const contentType = res.headers.get("content-type") || "";
      return contentType.includes("text/event-stream")
        ? JSON.parse(
            (await res.text())
              .split("\n")
              .find((l) => l.startsWith("data: "))!
              .slice(6)
          )
        : await res.json();
    };
    const callTool = async (name: string, argumentsObj: Record<string, unknown>) => {
      const resp = await callToolRaw(name, argumentsObj);
      assert.ok(!resp.error, `Tool ${name} returned JSON-RPC error: ${JSON.stringify(resp.error)}`);
      assert.notEqual(resp.result?.isError, true, `Tool ${name} error: ${JSON.stringify(resp.result)}`);
      return JSON.parse(resp.result.content[0].text);
    };

    // 2. Seed lightweight Available Block (no targets stored on Available Block itself)
    const abPayload = await callTool("upsert_available_block", {
      title: "Engine Couple 026",
      instructions: "3 rounds alternating",
      exercises: ["Row Erg", "Ring Dip"],
    });
    const abId = abPayload.availableBlock.id as number;

    // 3. Reject all-zero Per-Set or 0 Fixed target in upsert_daily_routine
    const invalidFixedResp = await callToolRaw("upsert_daily_routine", {
      title: "Invalid Target Routine",
      items: [
        {
          item_type: "EXERCISE",
          exercise_name: "Burpees",
          sets_count: 3,
          target_metric: "REPS",
          target_mode: "FIXED",
          fixed_value: 0,
        },
      ],
    });
    assert.equal(invalidFixedResp.result?.isError, true);

    const invalidPerSetResp = await callToolRaw("upsert_daily_routine", {
      title: "Invalid PerSet Routine",
      items: [
        {
          item_type: "BLOCK",
          block_id: abId,
          sets_count: 3,
          exercise_targets: [
            {
              exercise_name: "Row Erg",
              target_metric: "METERS",
              target_mode: "PER_SET",
              per_set_values: [0, 0, 0],
            },
          ],
        },
      ],
    });
    assert.equal(invalidPerSetResp.result?.isError, true);

    // 4. Create composite Daily Routine with optional targets + one unset item
    const upsertRes = await callTool("upsert_daily_routine", {
      title: "Conditioning + BW Mix 026",
      notes: "Metric targets test",
      items: [
        {
          item_type: "BLOCK",
          block_id: abId,
          sets_count: 3,
          exercise_targets: [
            {
              exercise_name: "Row Erg",
              target_metric: "CALORIES",
              target_mode: "FIXED",
              fixed_value: 20,
            },
            {
              exercise_name: "Ring Dip",
              target_metric: "REPS",
              target_mode: "PER_SET",
              per_set_values: [8, 0, 10],
            },
          ],
        },
        {
          item_type: "EXERCISE",
          exercise_name: "Strict Pull-Up 026",
          note: "Dead hang start",
          sets_count: 3,
          target_metric: "REPS",
          target_mode: "FIXED",
          fixed_value: 10,
        },
        {
          item_type: "EXERCISE",
          exercise_name: "Shuttle Run 026",
          note: "Per-round shuttle",
          sets_count: 3,
          target_metric: "METERS",
          target_mode: "PER_SET",
          per_set_values: [250, 0, 500],
        },
        {
          item_type: "EXERCISE",
          exercise_name: "Unset Mobility 026",
          note: "2 min per side",
        },
      ],
    });

    const dr = upsertRes.dailyRoutine;
    assert.equal(dr.items.length, 4);
    assert.equal(dr.items[0].setsCount, 3);
    assert.equal(dr.items[0].exerciseTargets.length, 2);
    assert.equal(dr.items[0].exerciseTargets[0].target.metric, "CALORIES");
    assert.equal(dr.items[0].exerciseTargets[0].target.fixedValue, 20);
    assert.deepEqual(dr.items[0].exerciseTargets[1].target.perSetValues, [8, 0, 10]);

    assert.equal(dr.items[1].setsCount, 3);
    assert.equal(dr.items[1].targetSpec.metric, "REPS");
    assert.equal(dr.items[1].targetSpec.fixedValue, 10);

    assert.equal(dr.items[2].setsCount, 3);
    assert.equal(dr.items[2].targetSpec.metric, "METERS");
    assert.deepEqual(dr.items[2].targetSpec.perSetValues, [250, 0, 500]);

    assert.equal(dr.items[3].setsCount, null);
    assert.equal(dr.items[3].targetSpec, null);

    // Underlying Available Block stays clean & unmodified
    const abAfter = await callTool("list_available_blocks", { query: "Engine Couple 026" });
    assert.equal(abAfter.availableBlocks[0].instructions, "3 rounds alternating");

    // 5. Log Daily Routine session with multi-metric sets + update_routine_template=true preserving targets
    const logRes = await callTool("log_daily_routine_session", {
      date: "2026-11-18",
      routine_id: dr.id,
      update_routine_template: true,
      logged_items: [
        {
          item_type: "BLOCK",
          block_id: abId,
          exercise_logs: [
            {
              exercise_name: "Row Erg",
              metric: "CALORIES",
              sets: [{ metric_value: 20 }, { metric_value: 25 }],
            },
            {
              exercise_name: "Ring Dip",
              metric: "REPS",
              sets: [
                { reps: 8, group_index: 1 },
                { reps: 10, group_index: 3 },
              ],
            },
          ],
        },
        {
          item_type: "EXERCISE",
          exercise_name: "Strict Pull-Up 026",
          note: "Dead hang start",
          metric: "REPS",
          sets: [
            { reps: 12, group_index: 1 },
            { reps: 10, group_index: 3 },
          ],
        },
        {
          item_type: "EXERCISE",
          exercise_name: "Shuttle Run 026",
          note: "Per-round shuttle",
          metric: "METERS",
          sets: [
            { metric_value: 250, group_index: 1 },
            { metric_value: 500, group_index: 3 },
          ],
        },
      ],
    });

    assert.equal(logRes.session.blocks[0].block.notes, "[log_metric=CALORIES]");
    assert.equal(logRes.session.blocks[0].sets[1].metricValue, 25);
    assert.equal(logRes.session.blocks[1].block.notes, "[log_metric=REPS]");
    assert.equal(logRes.session.blocks[1].sets[1].groupIndex, 3);
    assert.equal(logRes.session.blocks[2].sets[0].reps, 12);
    assert.equal(logRes.session.blocks[3].block.notes, "[log_metric=METERS]");
    assert.equal(logRes.session.blocks[3].sets[1].metricValue, 500);

    // Template sync preserved configured targets on matched items
    assert.equal(logRes.updatedRoutineTemplate.items[0].setsCount, 3);
    assert.equal(logRes.updatedRoutineTemplate.items[0].exerciseTargets.length, 2);
    assert.equal(logRes.updatedRoutineTemplate.items[1].targetSpec.fixedValue, 10);
    assert.deepEqual(logRes.updatedRoutineTemplate.items[2].targetSpec.perSetValues, [250, 0, 500]);

    // 6. get_daily_routine_progress computes unit-aware top/average/volume/recentSessions
    const prog = await callTool("get_daily_routine_progress", { routine_id: dr.id });
    const pullUpProg = prog.items.find((i: { title: string }) => i.title === "Strict Pull-Up 026");
    assert.equal(pullUpProg.unit, "reps");
    assert.equal(pullUpProg.allTimeTop, 12);
    assert.equal(pullUpProg.lastVolume, 22);
    assert.deepEqual(pullUpProg.lastSets, [12, 10]);

    const shuttleProg = prog.items.find((i: { title: string }) => i.title === "Shuttle Run 026");
    assert.equal(shuttleProg.unit, "m");
    assert.equal(shuttleProg.allTimeTop, 500);
    assert.equal(shuttleProg.lastVolume, 750);

    // 7. log_block_session & update_block_session_log support multi-metric sets
    const calBlockLog = await callTool("log_block_session", {
      date: "2026-11-19",
      blockId: abId,
      exerciseLogs: [
        {
          exerciseName: "Row Erg",
          metric: "CALORIES",
          sets: [{ metric_value: 18 }, { metric_value: 20 }],
        },
      ],
    });
    const calSessionId = calBlockLog.session.session.id as number;
    assert.equal(calBlockLog.session.blocks[0].block.notes, "[log_metric=CALORIES]");

    const calBlockEdit = await callTool("update_block_session_log", {
      sessionId: calSessionId,
      blockId: abId,
      exercises: [
        {
          exerciseName: "Row Erg",
          metric: "TIME_SECONDS",
          sets: [{ metric_value: 45, group_index: 1 }, { metric_value: 60, group_index: 2 }],
        },
      ],
    });
    assert.equal(calBlockEdit.session.blocks[0].block.notes, "[log_metric=TIME_SECONDS]");
    assert.equal(calBlockEdit.session.blocks[0].sets[1].metricValue, 60);
    assert.equal(calBlockEdit.session.blocks[0].sets[1].groupIndex, 2);
  });

  it("SPEC-027 Complex-first MCP tools, Daily Routine item_type='COMPLEX' + precedence, and legacy Block compatibility", async () => {
    let msgId = 1300;
    const callToolRaw = async (name: string, argumentsObj: Record<string, unknown>) => {
      const id = msgId++;
      const res = await fetch(`${baseUrl}/mcp`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json, text/event-stream",
          Authorization: "Bearer dev-static-token-123",
        },
        body: JSON.stringify({
          jsonrpc: "2.0",
          id,
          method: "tools/call",
          params: { name, arguments: argumentsObj },
        }),
      });
      assert.equal(res.status, 200);
      const contentType = res.headers.get("content-type") || "";
      return contentType.includes("text/event-stream")
        ? JSON.parse(
            (await res.text())
              .split("\n")
              .find((l) => l.startsWith("data: "))!
              .slice(6)
          )
        : await res.json();
    };
    const callTool = async (name: string, argumentsObj: Record<string, unknown>) => {
      const resp = await callToolRaw(name, argumentsObj);
      assert.ok(!resp.error, `Tool ${name} returned JSON-RPC error: ${JSON.stringify(resp.error)}`);
      assert.notEqual(resp.result?.isError, true, `Tool ${name} error: ${JSON.stringify(resp.result)}`);
      return JSON.parse(resp.result.content[0].text);
    };

    // 1. Upsert & batch upsert via Complex-first tools
    const c1 = await callTool("upsert_complex", {
      title: "Clean + Hang Clean + Front Squat Wave",
      instructions: "E3MOM x 6 sets",
      exercises: ["Clean", "Hang Clean", "Front Squat"],
      trackedPrs: [{ movementName: "Clean", rmReps: 1 }],
    });
    const cleanComplexId = c1.complex.id as number;
    assert.ok(cleanComplexId > 0);
    assert.equal(c1.complex.isAvailableBlockFormat, true);

    const batchRes = await callTool("batch_upsert_complexes", {
      complexes: [
        {
          title: "Snatch + Hang Snatch + Snatch Balance",
          instructions: "E4MOM x 6 sets",
          exercises: ["Snatch", "Hang Snatch", "Snatch Balance"],
        },
        {
          title: "Barbell Overhead Complex",
          instructions: "3 rounds",
          exercises: ["Push Press"],
        },
      ],
    });
    assert.equal(batchRes.upsertedCount, 2);
    const snatchComplexId = batchRes.complexes[0].id as number;

    // Validation: empty batch_upsert_complexes fails gracefully
    const emptyBatchResp = await callToolRaw("batch_upsert_complexes", {});
    assert.equal(emptyBatchResp.result?.isError, true);

    // 2. list_complexes & get_workout_summary expose complexes alongside legacy fields
    const listComplexesRes = await callTool("list_complexes", { query: "Clean + Hang Clean" });
    assert.equal(listComplexesRes.count, 1);
    assert.equal(listComplexesRes.complexes[0].id, cleanComplexId);
    const legacyListRes = await callTool("list_available_blocks", { query: "Clean + Hang Clean" });
    assert.equal(legacyListRes.availableBlocks[0].id, cleanComplexId);

    const summaryRes = await callTool("get_workout_summary", {});
    assert.equal(summaryRes.complexesCount, summaryRes.availableBlockCount);

    // 3. Log & edit session via Complex-first tools + precedence check (complex_id > block_id)
    const logged = await callTool("log_complex_session", {
      date: "2026-11-20",
      complex_id: cleanComplexId,
      blockId: snatchComplexId,
      exerciseLogs: [{ exerciseName: "Clean", weights: [80, 85, 90] }],
      recordedPrs: [{ movementName: "Clean", rmReps: 1, weight: 90 }],
    });
    const sessionId = logged.session.session.id as number;
    assert.equal(logged.session.blocks[0].block.routineId, cleanComplexId);

    const edited = await callTool("update_complex_session_log", {
      sessionId,
      complex_id: cleanComplexId,
      complex_notes: "E3MOM x 6 sets - felt crisp",
      sync_parent_complex_default_prs: false,
      exercises: [{ exerciseName: "Clean", weights: [85, 90, 95] }],
      trackedPrs: [{ movementName: "Clean", rmReps: 1, weight: 95 }],
    });
    assert.equal(edited.session.blocks[0].block.description, "E3MOM x 6 sets - felt crisp");
    assert.equal(edited.reconciledPrs[0].weight, 95);
    assert.equal(edited.updatedTrackedPrs, null);

    // 4. Daily Routine with item_type: "COMPLEX" and complex_id precedence over block_id
    const routineRes = await callTool("upsert_daily_routine", {
      title: "Olympic Complex Day",
      items: [
        {
          item_type: "COMPLEX",
          complex_id: snatchComplexId,
          block_id: cleanComplexId,
        },
        {
          item_type: "EXERCISE",
          exercise_name: "Lateral Raises",
          sets_count: 2,
          target_metric: "REPS",
          target_mode: "PER_SET",
          per_set_values: [8, 12],
        },
      ],
    });
    const dr = routineRes.dailyRoutine;
    assert.equal(dr.complexCount, 1);
    assert.equal(dr.blockCount, 1);
    assert.equal(dr.items[0].itemType, "COMPLEX");
    assert.equal(dr.items[0].complexId, snatchComplexId);
    assert.equal(dr.items[0].blockId, snatchComplexId);
    assert.equal(dr.items[0].complexTitle, "Snatch + Hang Snatch + Snatch Balance");

    // 5. Log Daily Routine session with item_type: "COMPLEX" + verify get_daily_routine_progress
    await callTool("log_daily_routine_session", {
      date: "2026-11-21",
      routine_id: dr.id,
      logged_items: [
        {
          item_type: "COMPLEX",
          complex_id: snatchComplexId,
          exercise_logs: [{ exerciseName: "Snatch", weights: [70, 72.5] }],
        },
        {
          item_type: "EXERCISE",
          exercise_name: "Lateral Raises",
          set_weights_kg: [12, 12],
        },
      ],
    });
    const drProgress = await callTool("get_daily_routine_progress", { routine_id: dr.id });
    assert.equal(drProgress.items[0].itemType, "COMPLEX");
    assert.equal(drProgress.items[0].complexId, snatchComplexId);
    assert.equal(drProgress.items[0].allTimeTop, 72.5);

    // 6. delete_complex tombstones the complex cleanly while preserving session logs
    const delComplex = await callTool("delete_complex", { complex_title: "Barbell Overhead Complex" });
    assert.equal(delComplex.status, "deleted");
    assert.deepEqual(delComplex.deletedTitles, ["Barbell Overhead Complex"]);

    // 7. Compact Complex Logging parity: shared_set_weights_kg + 1-2-3-1-2-3 skip patterns + mixed modalities
    await callTool("upsert_exercise", {
      name: "Air Bike",
      category: "MACHINE",
      metricType: "CALORIES",
      unit: "cal",
      tracksRepMax: false,
    });
    const mixedComplex = await callTool("upsert_complex", {
      title: "Oly + Engine",
      instructions: "E4MOM · 1-2-3-1-2-3",
      exercises: ["Snatch", "Hang Snatch", "Snatch Balance", "Air Bike"],
    });
    const mixedComplexId = mixedComplex.complex.id as number;

    const sharedLogged = await callTool("log_complex_session", {
      date: "2026-11-22",
      complex_id: mixedComplexId,
      shared_set_weights_kg: [40, 42.5, 45, 47.5, 47.5, 50],
      exercise_targets: [
        { exercise_name: "Snatch", target_metric: "REPS", target_mode: "FIXED", fixed_value: 1 },
        {
          exercise_name: "Hang Snatch",
          target_metric: "REPS",
          target_mode: "PER_SET",
          per_set_values: [0, 1, 1, 0, 1, 1],
        },
        {
          exercise_name: "Snatch Balance",
          target_metric: "REPS",
          target_mode: "PER_SET",
          per_set_values: [0, 0, 1, 0, 0, 1],
        },
        { exercise_name: "Air Bike", target_metric: "CALORIES", target_mode: "FIXED", fixed_value: 10 },
      ],
    });
    const sharedBlocks = sharedLogged.session.blocks as Array<any>;
    assert.equal(sharedBlocks.length, 4);
    assert.deepEqual(
      sharedBlocks[0].sets.map((s: any) => s.weight),
      [40, 42.5, 45, 47.5, 47.5, 50]
    );
    assert.deepEqual(
      sharedBlocks[0].sets.map((s: any) => s.groupIndex),
      [1, 2, 3, 4, 5, 6]
    );
    assert.deepEqual(
      sharedBlocks[1].sets.map((s: any) => s.weight),
      [42.5, 45, 47.5, 50]
    );
    assert.deepEqual(
      sharedBlocks[1].sets.map((s: any) => s.groupIndex),
      [2, 3, 5, 6]
    );
    assert.deepEqual(
      sharedBlocks[2].sets.map((s: any) => s.weight),
      [45, 50]
    );
    assert.deepEqual(
      sharedBlocks[2].sets.map((s: any) => s.groupIndex),
      [3, 6]
    );
    assert.equal(sharedBlocks[3].sets.length, 6);
    assert.ok(sharedBlocks[3].sets.every((s: any) => s.weight === 0 && s.metricValue === 10));

    // Update with shared_set_weights_kg WITHOUT exercise_targets preserves existing active groupIndex masks
    const sharedEdited = await callTool("update_complex_session_log", {
      sessionId: sharedLogged.session.session.id,
      complex_id: mixedComplexId,
      shared_set_weights_kg: [45, 47.5, 50, 52.5, 52.5, 55],
    });
    const editedBlocks = sharedEdited.session.blocks as Array<any>;
    assert.deepEqual(
      editedBlocks[1].sets.map((s: any) => s.weight),
      [47.5, 50, 52.5, 55]
    );
    assert.deepEqual(
      editedBlocks[1].sets.map((s: any) => s.groupIndex),
      [2, 3, 5, 6]
    );
    assert.deepEqual(
      editedBlocks[2].sets.map((s: any) => s.weight),
      [50, 55]
    );
    assert.deepEqual(
      editedBlocks[2].sets.map((s: any) => s.groupIndex),
      [3, 6]
    );

    // Daily Routine with saved 1-2-3-1-2-3 targets logged via shared_set_weights_kg + partial override
    const sparseRoutine = await callTool("upsert_daily_routine", {
      title: "Sparse Oly Routine",
      items: [
        {
          item_type: "COMPLEX",
          complex_id: snatchComplexId,
          sets_count: 6,
          exercise_targets: [
            { exercise_name: "Snatch", target_metric: "REPS", target_mode: "FIXED", fixed_value: 1 },
            {
              exercise_name: "Hang Snatch",
              target_metric: "REPS",
              target_mode: "PER_SET",
              per_set_values: [0, 1, 1, 0, 1, 1],
            },
            {
              exercise_name: "Snatch Balance",
              target_metric: "REPS",
              target_mode: "PER_SET",
              per_set_values: [0, 0, 1, 0, 0, 1],
            },
          ],
        },
      ],
    });
    const sparseDrLog = await callTool("log_daily_routine_session", {
      date: "2026-11-23",
      routine_id: sparseRoutine.dailyRoutine.id,
      logged_items: [
        {
          item_type: "COMPLEX",
          complex_id: snatchComplexId,
          shared_set_weights_kg: [40, 42.5, 45, 47.5, 47.5, 50],
          exercise_logs: [
            {
              exercise_name: "Snatch Balance",
              sets: [
                { reps: 1, weight_kg: 40, group_index: 3 },
                { reps: 1, weight_kg: 45, group_index: 6 },
              ],
            },
          ],
        },
      ],
    });
    const sparseDrBlocks = sparseDrLog.session.blocks as Array<any>;
    assert.deepEqual(
      sparseDrBlocks[0].sets.map((s: any) => s.weight),
      [40, 42.5, 45, 47.5, 47.5, 50]
    );
    assert.deepEqual(
      sparseDrBlocks[1].sets.map((s: any) => s.groupIndex),
      [2, 3, 5, 6]
    );
    assert.deepEqual(
      sparseDrBlocks[2].sets.map((s: any) => s.weight),
      [40, 45]
    );
  });
});

describe("GET /export/sheets (Read-Only Google Sheets Bridge)", () => {
  const SHEETS_TOKEN = "sheets-export-secret-token-0123456789abcdef-valid";
  const TARGET_UID = "athlete-sheets-uid";

  async function startTestServer(options: {
    sheetsToken?: string;
    sheetsUid?: string;
    backingStore?: InMemoryBackingStore;
  }) {
    const store = options.backingStore ?? new InMemoryBackingStore();
    const app = createMcpExpressApp({
      backingStore: store,
      rateLimitMaxPerMinute: 500,
      oauthConfig: {
        jwtSecret: "test-secret-key-for-mcp-unit-tests-at-least-32-chars",
        devBearerToken: "mcp-dev-token-unrelated-987654",
        devDefaultUid: TARGET_UID,
        verifyFirebaseIdToken: async () => ({ uid: TARGET_UID, email: "athlete@crosstraining.app" }),
      },
      ...(options.sheetsToken !== undefined || options.sheetsUid !== undefined
        ? {
            sheetsExportConfig: {
              token: options.sheetsToken,
              defaultUid: options.sheetsUid,
              defaultEnv: "production",
            },
          }
        : {}),
    });

    let srv!: Server;
    let url = "";
    await new Promise<void>((resolve) => {
      srv = app.listen(0, "127.0.0.1", () => {
        const addr = srv.address();
        if (addr && typeof addr === "object") {
          url = `http://127.0.0.1:${addr.port}`;
        }
        resolve();
      });
    });
    return {
      url,
      store,
      close: () =>
        new Promise<void>((resolve, reject) => {
          srv.close((err) => (err ? reject(err) : resolve()));
        }),
    };
  }

  it("returns 503 when unconfigured or configured with an insecure short token", async () => {
    const unconfigured = await startTestServer({});
    try {
      const res = await fetch(`${unconfigured.url}/export/sheets`);
      assert.equal(res.status, 503);
      assert.equal(res.headers.get("cache-control"), "no-store");
    } finally {
      await unconfigured.close();
    }

    const shortTok = await startTestServer({
      sheetsToken: "too-short",
      sheetsUid: TARGET_UID,
    });
    try {
      const res = await fetch(`${shortTok.url}/export/sheets`, {
        headers: { Authorization: "Bearer too-short" },
      });
      assert.equal(res.status, 503);
    } finally {
      await shortTok.close();
    }
  });

  it("enforces strict Bearer token auth, constant-time mismatch rejection, and MCP/Sheets scope isolation", async () => {
    const srv = await startTestServer({
      sheetsToken: SHEETS_TOKEN,
      sheetsUid: TARGET_UID,
    });
    try {
      const missingRes = await fetch(`${srv.url}/export/sheets`);
      assert.equal(missingRes.status, 401);

      const wrongRes = await fetch(`${srv.url}/export/sheets`, {
        headers: { Authorization: "Bearer wrong-token-0123456789abcdef0123456789" },
      });
      assert.equal(wrongRes.status, 401);

      // MCP dev token must NOT access /export/sheets
      const mcpTokenOnSheets = await fetch(`${srv.url}/export/sheets`, {
        headers: { Authorization: "Bearer mcp-dev-token-unrelated-987654" },
      });
      assert.equal(mcpTokenOnSheets.status, 401);

      // Sheets export token must NOT access POST /mcp
      const sheetsTokenOnMcp = await fetch(`${srv.url}/mcp`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${SHEETS_TOKEN}`,
          "Content-Type": "application/json",
          Accept: "application/json, text/event-stream",
        },
        body: JSON.stringify({ jsonrpc: "2.0", id: 1, method: "tools/list", params: {} }),
      });
      assert.equal(sheetsTokenOnMcp.status, 401);

      // Query validation -> 400
      const badEnv = await fetch(`${srv.url}/export/sheets?env=staging`, {
        headers: { Authorization: `Bearer ${SHEETS_TOKEN}` },
      });
      assert.equal(badEnv.status, 400);

      const badSub = await fetch(`${srv.url}/export/sheets?subcycleType=INVALID`, {
        headers: { Authorization: `Bearer ${SHEETS_TOKEN}` },
      });
      assert.equal(badSub.status, 400);

      for (const p of ["cycleStartDate", "cycleFinishDate", "asOfDate"]) {
        const badDate = await fetch(`${srv.url}/export/sheets?${p}=2026-02-30`, {
          headers: { Authorization: `Bearer ${SHEETS_TOKEN}` },
        });
        assert.equal(badDate.status, 400);
      }
    } finally {
      await srv.close();
    }
  });

  it("exports flattened weights, sessions_sets, prs, cycles, cycle_goals, and daily_logs strictly read-only", async () => {
    const backing = new InMemoryBackingStore();
    const ctx = { uid: TARGET_UID, env: "production" as const };
    const srv = await startTestServer({
      backingStore: backing,
      sheetsToken: SHEETS_TOKEN,
      sheetsUid: TARGET_UID,
    });

    try {
      const exercises: ExerciseRecord[] = [
        { id: 1, name: "Snatch", category: "BARBELL", metricType: "WEIGHT", unit: "kg", tracksRepMax: true },
        { id: 2, name: "Hang Snatch", category: "BARBELL", metricType: "WEIGHT", unit: "kg", tracksRepMax: true },
        { id: 3, name: "Air Bike", category: "ACCESSORY", metricType: "CALORIES", unit: "cal", tracksRepMax: false },
        { id: 4, name: "Back Squat", category: "BARBELL", metricType: "WEIGHT", unit: "kg", tracksRepMax: true },
      ];
      await backing.writeCollection(ctx, "exercises", exercises);

      const routines: RoutineWithBlocksRecord[] = [
        {
          routine: {
            id: 10,
            name: "Snatch Balance Complex",
            description: "[ab_prs=Snatch:1]\nBuild cleanly",
            mainExerciseId: null,
            defaultFormat: AVAILABLE_BLOCK_FORMAT,
          },
          blocks: [],
        },
        {
          routine: {
            id: 11,
            name: "WL - Thursday Clean",
            description: "Technique + strength",
            mainExerciseId: null,
            defaultFormat: "DAILY_ROUTINE",
          },
          blocks: [],
        },
      ];
      await backing.writeCollection(ctx, "routines", routines);

      const sessions: SessionWithBlocksRecord[] = [
        {
          session: {
            id: 100,
            cycleId: 1,
            cycleName: "Strength & Performance",
            isActiveCycle: false,
            date: "2026-10-01",
            title: "Old Deleted Session",
            notes: "",
            deletedAtMillis: 1700000000000,
          },
          blocks: [],
        },
        {
          session: {
            id: 101,
            cycleId: 4,
            cycleName: "Recomposition",
            isActiveCycle: true,
            date: "2026-11-12",
            title: "WL - Thursday Clean",
            notes: "",
          },
          blocks: [
            {
              block: {
                id: 201,
                sessionId: 101,
                position: 0,
                name: "Hang Snatch",
                kind: "COMPLEX",
                format: "",
                scheme: "",
                mainExerciseId: 2,
                mainExerciseName: "Hang Snatch",
                routineId: 10,
                routineName: "Snatch Balance Complex",
                description: "",
                resultText: "",
                resultValue: null,
                notes: "",
                subBlock: "Snatch Balance Complex",
                isCompleted: true,
              },
              sets: [
                { id: 1, blockId: 201, position: 0, groupIndex: 2, reps: 1, weight: 65, metricValue: 0, isWarmup: false, isFailed: false, notes: "" },
                { id: 2, blockId: 201, position: 1, groupIndex: 4, reps: 1, weight: 70, metricValue: 0, isWarmup: false, isFailed: false, notes: "" },
              ],
            },
            {
              block: {
                id: 202,
                sessionId: 101,
                position: 1,
                name: "Air Bike",
                kind: "METABOLIC",
                format: "STANDALONE_EXERCISE",
                scheme: "",
                mainExerciseId: 3,
                mainExerciseName: "Air Bike",
                routineId: null,
                routineName: null,
                description: "",
                resultText: "",
                resultValue: null,
                notes: "[log_metric=CALORIES]",
                subBlock: "",
                isCompleted: true,
              },
              sets: [
                { id: 3, blockId: 202, position: 0, groupIndex: 0, reps: 1, weight: 0, metricValue: 18, isWarmup: false, isFailed: false, notes: "" },
              ],
            },
            {
              block: {
                id: 203,
                sessionId: 101,
                position: 2,
                name: "Back Squat",
                kind: "STRENGTH",
                format: "STANDALONE_EXERCISE",
                scheme: "",
                mainExerciseId: 4,
                mainExerciseName: "Back Squat",
                routineId: null,
                routineName: null,
                description: "",
                resultText: "",
                resultValue: null,
                notes: "",
                subBlock: "",
                isCompleted: true,
              },
              // Single blank placeholder set should be skipped
              sets: [
                { id: 4, blockId: 203, position: 0, groupIndex: 0, reps: 1, weight: 0, metricValue: 0, isWarmup: false, isFailed: false, notes: "" },
              ],
            },
          ],
        },
      ];
      await backing.writeCollection(ctx, "sessions", sessions);

      const repMaxes: RepMaxRecord[] = [
        {
          id: 1,
          exerciseId: 1,
          exerciseName: "Snatch",
          reps: 1,
          weight: 85,
          date: "2026-11-12",
          cycleId: 4,
          cycleName: "Recomposition",
          isActiveCycle: true,
          sessionId: 101,
          blockId: 201,
        },
        {
          id: 2,
          exerciseId: 4,
          exerciseName: "Back Squat",
          reps: 3,
          weight: 140,
          date: "2026-11-10",
          cycleId: 4,
          cycleName: "Recomposition",
          isActiveCycle: true,
          deletedAtMillis: 1700000000000,
        },
      ];
      await backing.writeCollection(ctx, "rep_maxes", repMaxes);

      const cycleGoals: CycleGoalRecord[] = [
        {
          id: 1,
          cycleId: 4,
          cycleName: "Recomposition",
          isActiveCycle: true,
          exerciseId: 1,
          exerciseName: "Snatch",
          targetReps: 1,
          startWeight: 80,
          targetWeight: 90,
          notes: "Hit smooth 90kg",
        },
        {
          id: 2,
          cycleId: 4,
          cycleName: "Recomposition",
          isActiveCycle: true,
          exerciseId: 3,
          exerciseName: "Air Bike",
          targetReps: 1,
          startWeight: 20,
          targetWeight: 28,
          notes: "[metric=CALORIES,window=1] Sprint",
        },
      ];
      await backing.writeCollection(ctx, "cycle_goals", cycleGoals);

      // Weight entries with a 2-day gap between Nov 02 and Nov 04, spanning >= 8 timeline days
      const weightEntries: WeightEntryRecord[] = [
        { date: isoDateToEpochDay("2026-11-01"), weightKg: 76.0, notes: "Morning", updatedAtMillis: 1, deletedAtMillis: null },
        { date: isoDateToEpochDay("2026-11-02"), weightKg: 75.8, notes: "", updatedAtMillis: 1, deletedAtMillis: null },
        { date: isoDateToEpochDay("2026-11-04"), weightKg: 75.4, notes: "Post-gap", updatedAtMillis: 1, deletedAtMillis: null },
        { date: isoDateToEpochDay("2026-11-05"), weightKg: 75.5, notes: "", updatedAtMillis: 1, deletedAtMillis: null },
        { date: isoDateToEpochDay("2026-11-06"), weightKg: 75.3, notes: "", updatedAtMillis: 1, deletedAtMillis: null },
        { date: isoDateToEpochDay("2026-11-07"), weightKg: 75.2, notes: "", updatedAtMillis: 1, deletedAtMillis: null },
        { date: isoDateToEpochDay("2026-11-08"), weightKg: 75.1, notes: "", updatedAtMillis: 1, deletedAtMillis: null },
        { date: isoDateToEpochDay("2026-11-09"), weightKg: 75.0, notes: "", updatedAtMillis: 1, deletedAtMillis: null },
        { date: isoDateToEpochDay("2026-11-10"), weightKg: 74.9, notes: "", updatedAtMillis: 1, deletedAtMillis: 1700000000000 },
      ];
      await backing.writeCollection(ctx, "weight_entries", weightEntries);

      const dailyLogs: DailyLogRecord[] = [
        { date: isoDateToEpochDay("2026-11-09"), fastCompleted: true, isRestDay: false, caloriesKcal: 2600, proteinGrams: 185, carbsGrams: 290, fatGrams: 75, notes: "Solid", updatedAtMillis: 1, deletedAtMillis: null },
        { date: isoDateToEpochDay("2026-11-08"), fastCompleted: false, isRestDay: true, caloriesKcal: 2200, proteinGrams: 170, carbsGrams: 210, fatGrams: 70, notes: "Deleted", updatedAtMillis: 1, deletedAtMillis: 1700000000000 },
      ];
      await backing.writeCollection(ctx, "daily_logs", dailyLogs);

      // Assert strictly read-only (0 writeCollection calls during GET /export/sheets)
      let writeCalls = 0;
      const origWrite = backing.writeCollection.bind(backing);
      backing.writeCollection = async (...args) => {
        writeCalls += 1;
        return origWrite(...args);
      };

      const res = await fetch(`${srv.url}/export/sheets`, {
        headers: { Authorization: `Bearer ${SHEETS_TOKEN}` },
      });
      assert.equal(res.status, 200);
      assert.equal(res.headers.get("cache-control"), "no-store");
      assert.equal(writeCalls, 0);

      const payload = (await res.json()) as SheetsExportPayload;
      assert.equal(payload.env, "production");
      assert.equal(payload.active_cycle.cycle_name, "Recomposition");
      assert.equal(payload.active_cycle.subcycle_type, "RECOMPOSITION");
      assert.equal(payload.active_cycle.target_min_pct_wk, -0.25);
      assert.equal(payload.active_cycle.target_max_pct_wk, 0.25);

      // Weights: 2026-11-01..2026-11-09 = 9 days (Nov 10 tombstoned, Nov 03 interpolated)
      assert.equal(payload.weights.length, 9);
      const nov03 = payload.weights.find((w) => w.date === "2026-11-03")!;
      assert.equal(nov03.real_weight_kg, null);
      assert.equal(nov03.is_interpolated, true);
      assert.equal(nov03.entry_type, "THEORETICAL");
      assert.equal(nov03.interpolated_weight_kg, 75.6);

      const expectedAnalytics = computeWeightMomentumAnalytics(
        weightEntries
          .filter((w) => !w.deletedAtMillis)
          .map((w) => ({
            isoDate: new Date(w.date * 86_400_000).toISOString().slice(0, 10),
            weightKg: w.weightKg,
          })),
        { subcycleType: "RECOMPOSITION" }
      );
      const lastWeightRow = payload.weights[payload.weights.length - 1];
      assert.equal(
        lastWeightRow.weekly_body_weight_kg,
        expectedAnalytics.weekly_body_weight_composite.current_kg
      );
      assert.equal(
        lastWeightRow.momentum_pct,
        expectedAnalytics.weekly_body_weight_composite.weekly_momentum_pct
      );
      assert.equal(
        lastWeightRow.status,
        expectedAnalytics.weekly_body_weight_composite.status
      );
      assert.equal(
        lastWeightRow.avg_7d,
        expectedAnalytics.horizons["7d"].avg_kg
      );
      assert.equal(
        lastWeightRow.avg_28d,
        expectedAnalytics.horizons["28d"].avg_kg
      );

      // Sessions_Sets: 2 complex sets (groupIndex 2, 4) + 1 Air Bike set (cal, weight_kg=null); placeholder skipped; deleted session skipped
      assert.equal(payload.sessions_sets.length, 3);
      assert.equal(payload.sessions_sets[0].item_type, "COMPLEX");
      assert.equal(payload.sessions_sets[0].item_title, "Snatch Balance Complex");
      assert.equal(payload.sessions_sets[0].routine_title, "WL - Thursday Clean");
      assert.equal(payload.sessions_sets[0].set_index, 2);
      assert.equal(payload.sessions_sets[0].weight_kg, 65);
      assert.equal(payload.sessions_sets[1].set_index, 4);
      assert.equal(payload.sessions_sets[2].item_type, "EXERCISE");
      assert.equal(payload.sessions_sets[2].weight_kg, null);
      assert.equal(payload.sessions_sets[2].metric_value, 18);
      assert.equal(payload.sessions_sets[2].unit, "cal");

      // PRs: Back Squat tombstoned; Snatch resolves source_complex_title
      assert.equal(payload.prs.length, 1);
      assert.equal(payload.prs[0].movement_name, "Snatch");
      assert.equal(payload.prs[0].source_complex_title, "Snatch Balance Complex");
      assert.equal(payload.prs[0].session_title, "WL - Thursday Clean");

      // Cycle goals: Snatch 1RM joined against 85kg PR => 50% progress; Air Bike decoded CALORIES 1 min
      assert.equal(payload.cycle_goals.length, 2);
      const snatchGoal = payload.cycle_goals.find((g) => g.exercise_name === "Snatch")!;
      assert.equal(snatchGoal.current_best_value, 85);
      assert.equal(snatchGoal.progress_pct, 50);
      assert.equal(snatchGoal.notes, "Hit smooth 90kg");
      const bikeGoal = payload.cycle_goals.find((g) => g.exercise_name === "Air Bike")!;
      assert.equal(bikeGoal.goal_metric, "CALORIES");
      assert.equal(bikeGoal.time_window_minutes, 1);
      assert.equal(bikeGoal.current_best_value, null);
      assert.equal(bikeGoal.notes, "Sprint");

      // Daily logs: tombstoned entry excluded
      assert.equal(payload.daily_logs.length, 1);
      assert.equal(payload.daily_logs[0].date, "2026-11-09");
      assert.equal(payload.daily_logs[0].calories_kcal, 2600);

      // Query override ?subcycleType=FAT_LOSS overrides active cycle corridor
      const fatLossRes = await fetch(
        `${srv.url}/export/sheets?subcycleType=FAT_LOSS`,
        { headers: { Authorization: `Bearer ${SHEETS_TOKEN}` } }
      );
      assert.equal(fatLossRes.status, 200);
      const fatLossPayload = (await fatLossRes.json()) as SheetsExportPayload;
      assert.equal(fatLossPayload.active_cycle.subcycle_type, "FAT_LOSS");
      assert.equal(fatLossPayload.active_cycle.target_min_pct_wk, -1.0);
      assert.equal(fatLossPayload.active_cycle.target_max_pct_wk, -0.25);
    } finally {
      await srv.close();
    }
  });

  it("attributes cycle goal progress only to PRs from the goal's own cycle (no cross-cycle leakage)", async () => {
    const backing = new InMemoryBackingStore();
    const ctx = { uid: TARGET_UID, env: "production" as const };
    await backing.writeCollection<ExerciseRecord>(ctx, "exercises", [
      { id: 1, name: "Snatch", category: "BARBELL", metricType: "WEIGHT", unit: "kg", tracksRepMax: true },
    ]);
    await backing.writeCollection<RepMaxRecord>(ctx, "rep_maxes", [
      // Prior-cycle PR above the new cycle's target must NOT count toward cycle 2 progress
      { id: 1, exerciseId: 1, exerciseName: "Snatch", reps: 1, weight: 95, date: "2026-06-01", cycleId: 1, cycleName: "Strength", isActiveCycle: false },
    ]);
    await backing.writeCollection<CycleGoalRecord>(ctx, "cycle_goals", [
      { id: 1, cycleId: 2, cycleName: "Recomposition", isActiveCycle: true, exerciseId: 1, exerciseName: "Snatch", targetReps: 1, startWeight: 80, targetWeight: 90, notes: "" },
    ]);

    const payload = await buildSheetsExport(backing, ctx, {});
    const goal = payload.cycle_goals.find((g) => g.cycle_id === 2)!;
    assert.equal(goal.is_active_cycle, true);
    assert.equal(goal.current_best_value, null);
    assert.equal(goal.progress_pct, null);

    // An in-cycle PR (tagged cycleId 2) is joined normally
    await backing.writeCollection<RepMaxRecord>(ctx, "rep_maxes", [
      { id: 1, exerciseId: 1, exerciseName: "Snatch", reps: 1, weight: 95, date: "2026-06-01", cycleId: 1, cycleName: "Strength", isActiveCycle: false },
      { id: 2, exerciseId: 1, exerciseName: "Snatch", reps: 1, weight: 84, date: "2026-11-12", cycleId: 2, cycleName: "Recomposition", isActiveCycle: true },
    ]);
    const payload2 = await buildSheetsExport(backing, ctx, {});
    const goal2 = payload2.cycle_goals.find((g) => g.cycle_id === 2)!;
    assert.equal(goal2.current_best_value, 84);
    assert.equal(goal2.progress_pct, 40);
  });
});
