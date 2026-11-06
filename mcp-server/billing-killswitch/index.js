/**
 * Automated GCP Billing Kill-Switch (Cloud Run / Cloud Functions Gen2 HTTP & CloudEvent handler).
 *
 * Triggered by Pub/Sub topic: `billing-killswitch-alerts`
 * When `costAmount >= budgetAmount`, immediately unlinks the billing account from `virgymia-c2cc7`
 * via the Cloud Billing REST API (`PUT /v1/projects/{projectId}/billingInfo`).
 */
import http from "node:http";
import { fileURLToPath } from "node:url";

const PROJECT_ID = process.env.GCP_PROJECT_ID || "virgymia-c2cc7";
const PORT = Number(process.env.PORT || 8080);

async function getAccessToken() {
  const res = await fetch(
    "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token",
    { headers: { "Metadata-Flavor": "Google" } }
  );
  if (!res.ok) {
    throw new Error(`Failed to obtain metadata access token: ${res.status}`);
  }
  const data = await res.json();
  return data.access_token;
}

export async function evaluateBudgetNotification(pubsubDataJson, disableBillingFn) {
  const costAmount = Number(pubsubDataJson.costAmount ?? 0);
  const budgetAmount = Number(pubsubDataJson.budgetAmount ?? 0);

  if (budgetAmount > 0 && costAmount >= budgetAmount) {
    await disableBillingFn(PROJECT_ID);
    return {
      action: "BILLING_DISABLED",
      projectId: PROJECT_ID,
      costAmount,
      budgetAmount,
    };
  }

  return {
    action: "NO_OP_BELOW_CAP",
    projectId: PROJECT_ID,
    costAmount,
    budgetAmount,
  };
}

async function disableProjectBilling(projectId) {
  const token = await getAccessToken();
  const url = `https://cloudbilling.googleapis.com/v1/projects/${projectId}/billingInfo`;

  const infoRes = await fetch(url, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const info = await infoRes.json();
  if (!info.billingEnabled) {
    console.log(`Billing is already disabled on project ${projectId}.`);
    return;
  }

  const updateRes = await fetch(url, {
    method: "PUT",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ billingAccountName: "" }),
  });

  if (!updateRes.ok) {
    const errText = await updateRes.text();
    throw new Error(`Failed to unlink billing on ${projectId}: ${updateRes.status} ${errText}`);
  }
  console.warn(`CRITICAL: Unlinked billing account from ${projectId} due to budget cap!`);
}

// Start lightweight HTTP server for Cloud Run Pub/Sub Push subscription
if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const server = http.createServer(async (req, res) => {
    if (req.method !== "POST") {
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ status: "ok", service: "billing-killswitch" }));
      return;
    }

    let raw = "";
    for await (const chunk of req) {
      raw += chunk;
    }

    try {
      const envelope = JSON.parse(raw || "{}");
      const base64Data = envelope.message?.data;
      if (!base64Data) {
        res.writeHead(400).end("Missing Pub/Sub message.data");
        return;
      }
      const decoded = JSON.parse(Buffer.from(base64Data, "base64").toString("utf8"));
      const outcome = await evaluateBudgetNotification(decoded, disableProjectBilling);
      console.log("Budget alert evaluated:", JSON.stringify(outcome));
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify(outcome));
    } catch (err) {
      console.error("Error processing budget alert:", err);
      res.writeHead(500).end(err instanceof Error ? err.message : "Internal error");
    }
  });

  server.listen(PORT, () => {
    console.log(`Billing Kill-Switch listening on port ${PORT} for project ${PROJECT_ID}`);
  });
}
