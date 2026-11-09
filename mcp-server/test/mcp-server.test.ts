import assert from "node:assert/strict";
import crypto from "node:crypto";
import type { Server } from "node:http";
import { after, before, describe, it } from "node:test";
import { createMcpExpressApp } from "../src/index.js";
import { computeWeightMomentumAnalytics } from "../src/schemas.js";
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

    // 4. Exchange the code (bound to the registered client_id and redirect_uri)
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
    // Tokens carry a 16-byte client_id digest, never the full (potentially multi-KB) client_id.
    assert.ok(tokenData.access_token.length < 600, `access_token too large: ${tokenData.access_token.length}`);
    assert.ok(tokenData.refresh_token.length < 600, `refresh_token too large: ${tokenData.refresh_token.length}`);

    // 5. Refresh token rotation
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

    // 6. The refresh token is bound to the client it was issued to
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

    // 7. Multi-URI registrations are deflate-compressed (`z_` prefix) and still decode on /authorize
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

    // DCR rejects script-capable and non-loopback plain-http redirect URIs
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

    // Authorization codes are never issued to arbitrary redirect URIs or unknown clients
    assert.equal((await postAuthorize({ redirect_uri: "https://attacker.example/steal" })).status, 400);
    assert.equal((await postAuthorize({ client_id: "default" })).status, 400);
    const forgedId = reg.client_id.replace(/\.[^.]+$/, ".AAAA");
    assert.equal((await postAuthorize({ client_id: forgedId })).status, 400);
    // PKCE is mandatory and S256-only
    assert.equal((await postAuthorize({ code_challenge: "" })).status, 400);
    assert.equal((await postAuthorize({ code_challenge_method: "plain" })).status, 400);

    // GET /authorize refuses to render the consent page for an unregistered redirect_uri
    const badGet = await fetch(
      `${baseUrl}/authorize?client_id=${encodeURIComponent(reg.client_id)}` +
        `&redirect_uri=${encodeURIComponent("https://attacker.example/steal")}` +
        `&code_challenge=${codeChallenge}&code_challenge_method=S256&response_type=code`
    );
    assert.equal(badGet.status, 400);

    // Loopback redirect URIs may use any port (RFC 8252 §7.3); state with '&' / quotes is embedded safely
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

    // A code is bound to the client it was issued to
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
    const toolsJson = JSON.stringify(tools);
    assert.equal(toolsJson.includes('"$ref"'), false, "Tool schemas must never contain $ref (rejected by Gemini)");
    assert.equal(toolsJson.includes('"exclusiveMinimum"'), false, "Tool schemas must never contain exclusiveMinimum (rejected by Gemini)");

    const headResp = await fetch(`${baseUrl}/mcp`, {
      method: "HEAD",
      headers: { Authorization: "Bearer dev-static-token-123" },
    });
    assert.equal(headResp.status, 200);

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

    // 5. Verify get_weight_momentum_analytics (linear interpolation + 3:4:2:1 composite)
    await callMcp({
      jsonrpc: "2.0",
      id: 6,
      method: "tools/call",
      params: {
        name: "upsert_weight_entry",
        arguments: {
          date: "2026-10-30",
          weightKg: 80.0,
        },
      },
    });
    await callMcp({
      jsonrpc: "2.0",
      id: 7,
      method: "tools/call",
      params: {
        name: "upsert_weight_entry",
        arguments: {
          date: "2026-11-06",
          weightKg: 76.5,
        },
      },
    });

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
    assert.equal(momentumPayload.cycle_metadata.subcycle_label, "Fat Loss");
    assert.equal(momentumPayload.cycle_metadata.calibration_complete, true);
    assert.equal(momentumPayload.cycle_metadata.total_timeline_days, 8);
    assert.equal(momentumPayload.target_corridor.min_weekly_pct, -1.0);
    assert.equal(momentumPayload.target_corridor.max_weekly_pct, -0.25);
    assert.equal(momentumPayload.global_safety_bounds.possible_muscle_loss_below_pct, -1.0);
    assert.equal(momentumPayload.global_safety_bounds.suboptimal_muscle_gain_above_pct, 0.75);
    assert.equal(momentumPayload.daily_timeline.length, 8);
    assert.equal(momentumPayload.daily_timeline[0].source, "REAL");
    assert.equal(momentumPayload.daily_timeline[0].weight_kg, 80.0);
    assert.equal(momentumPayload.daily_timeline[1].source, "THEORETICAL");
    assert.equal(momentumPayload.daily_timeline[1].weight_kg, 79.5);
    assert.equal(momentumPayload.daily_timeline[7].source, "REAL");
    assert.equal(momentumPayload.daily_timeline[7].weight_kg, 76.5);
    assert.equal(momentumPayload.horizons["7d"].avg_kg, 78.0);
    assert.equal(momentumPayload.horizons["14d"].avg_kg, 78.25);
    assert.ok(momentumPayload.weekly_body_weight_composite.current_kg > 0);
    assert.equal(momentumPayload.weekly_body_weight_composite.diagnostic_code, "POSSIBLE_MUSCLE_LOSS");
    assert.equal(momentumPayload.weekly_body_weight_composite.severity, "CRITICAL_RED");
    assert.equal(momentumPayload.weekly_body_weight_composite.diagnostic_badge, "Possible muscle loss");
    assert.equal(
      momentumPayload.weekly_body_weight_composite.diagnostic_message,
      "Losing faster than -1.00%/wk. High risk of lean muscle loss."
    );
    assert.ok(momentumPayload.projected_finish_weight.min_kg < momentumPayload.projected_finish_weight.max_kg);

    // asOfDate before the latest weigh-in excludes later entries (Android referenceDate parity)
    const asOfResp = await callMcp({
      jsonrpc: "2.0",
      id: 9,
      method: "tools/call",
      params: {
        name: "get_weight_momentum_analytics",
        arguments: { asOfDate: "2026-11-02", includeDailyTimeline: true },
      },
    });
    const asOfPayload = JSON.parse(asOfResp.result.content[0].text);
    assert.equal(asOfPayload.daily_timeline[asOfPayload.daily_timeline.length - 1].date, "2026-11-02");
    assert.ok(asOfPayload.daily_timeline.every((pt: { date: string }) => pt.date <= "2026-11-02"));

    // Verify SPEC-WEIGHT-CARD-02 strict inequality corridors across FAT_LOSS, RECOMPOSITION, MUSCLE_GAIN
    const recompSlip = computeWeightMomentumAnalytics(
      [
        { isoDate: "2026-10-01", weightKg: 80.0 },
        { isoDate: "2026-10-08", weightKg: 79.3 },
      ],
      { subcycleType: "RECOMPOSITION" }
    );
    assert.equal(recompSlip.cycle_metadata.subcycle_type, "RECOMPOSITION");
    assert.equal(recompSlip.target_corridor.min_weekly_pct, -0.25);
    assert.equal(recompSlip.target_corridor.max_weekly_pct, 0.25);
    assert.equal(recompSlip.weekly_body_weight_composite.diagnostic_code, "WARNING_FAT_LOSS_PHASE");
    assert.equal(recompSlip.weekly_body_weight_composite.severity, "WARNING_AMBER");
    assert.equal(recompSlip.weekly_body_weight_composite.diagnostic_badge, "Warning: Fat loss phase");

    const muscleGainOptimal = computeWeightMomentumAnalytics(
      [
        { isoDate: "2026-10-01", weightKg: 80.0 },
        { isoDate: "2026-10-08", weightKg: 80.7 },
      ],
      { subcycleType: "MUSCLE_GAIN" }
    );
    assert.equal(muscleGainOptimal.cycle_metadata.subcycle_type, "MUSCLE_GAIN");
    assert.equal(muscleGainOptimal.cycle_metadata.subcycle_label, "Muscle Gain");
    assert.equal(muscleGainOptimal.target_corridor.min_weekly_pct, 0.25);
    assert.equal(muscleGainOptimal.target_corridor.max_weekly_pct, 0.75);
    assert.equal(muscleGainOptimal.weekly_body_weight_composite.diagnostic_code, "ON_TARGET");
    assert.equal(muscleGainOptimal.weekly_body_weight_composite.severity, "OPTIMAL_GREEN");
    assert.equal(muscleGainOptimal.weekly_body_weight_composite.diagnostic_badge, "Optimal");
    assert.equal(muscleGainOptimal.weekly_body_weight_composite.diagnostic_message, null);
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
