import { execFileSync } from "node:child_process";
import { mkdir } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { chromium } from "playwright";

const requiredEnvironment = [
  "POLICY_SCREENSHOT_TOKEN",
  "POLICY_SCREENSHOT_POLICY_NUMBER",
  "POLICY_SCREENSHOT_MEMBER_ID",
];
for (const name of requiredEnvironment) {
  if (!process.env[name]) {
    throw new Error(`${name} is required and must remain a runtime-only value.`);
  }
}

const repositoryRoot = fileURLToPath(new URL("../../../", import.meta.url));
const screenshotsDirectory = path.join(repositoryRoot, "docs", "screenshots");
await mkdir(screenshotsDirectory, { recursive: true });

const policyBaseUrl = process.env.POLICY_SCREENSHOT_BASE_URL ?? "http://localhost:8082";
const keycloakUrl = process.env.POLICY_SCREENSHOT_KEYCLOAK_URL ?? "http://localhost:8080";
const policyNumber = process.env.POLICY_SCREENSHOT_POLICY_NUMBER;
const memberId = process.env.POLICY_SCREENSHOT_MEMBER_ID;
const escapedPolicyNumber = policyNumber.replaceAll("'", "''");

const healthResponse = await fetch(`${policyBaseUrl}/actuator/health`);
if (!healthResponse.ok) throw new Error(`Policy health returned ${healthResponse.status}.`);
const health = await healthResponse.json();

const discoveryResponse = await fetch(
  `${keycloakUrl}/realms/health-insurance/.well-known/openid-configuration`,
);
if (!discoveryResponse.ok) throw new Error(`OIDC discovery returned ${discoveryResponse.status}.`);
const discovery = await discoveryResponse.json();

const requestHeaders = {
  Authorization: `Bearer ${process.env.POLICY_SCREENSHOT_TOKEN}`,
  "Content-Type": "application/json",
  "X-Correlation-ID": "policy-runtime-screenshot",
};
const evaluate = async (scenario, overrides = {}) => {
  const response = await fetch(`${policyBaseUrl}/api/v1/coverage-evaluations`, {
    method: "POST",
    headers: requestHeaders,
    body: JSON.stringify({
    policyNumber,
    memberId,
    serviceCode: "IMG-MRI",
    requestedAmount: 2500,
    currency: "TRY",
    serviceDate: "2026-09-14",
      ...overrides,
    }),
  });
  if (!response.ok) throw new Error(`${scenario} evaluation returned ${response.status}.`);
  const decision = await response.json();
  return {
    scenario,
    httpStatus: response.status,
    eligible: decision.eligible,
    code: decision.code,
    remainingAmount: decision.remainingAmount,
    currency: decision.currency,
  };
};

const decisions = [];
decisions.push(await evaluate("Eligible MRI"));
decisions.push(await evaluate("Limit exceeded", { requestedAmount: 12500 }));
decisions.push(await evaluate("Member mismatch", {
  memberId: "20000000-0000-0000-0000-000000000002",
}));
decisions.push(await evaluate("Service not covered", { serviceCode: "SRV-UNKNOWN" }));
decisions.push(await evaluate("Currency mismatch", { currency: "USD" }));
decisions.push(await evaluate("Expired service date", { serviceDate: "2027-01-01" }));

const invalidResponse = await fetch(`${policyBaseUrl}/api/v1/policies`, {
  method: "POST",
  headers: requestHeaders,
  body: JSON.stringify({
    policyNumber: "",
    memberId,
    validFrom: "2026-01-01",
    validUntil: "2026-12-31",
    coverages: [],
  }),
});
if (invalidResponse.status !== 400) {
  throw new Error(`Invalid policy request returned ${invalidResponse.status}, expected 400.`);
}
const invalidProblem = await invalidResponse.json();
const validationEvidence = {
  httpStatus: invalidResponse.status,
  title: invalidProblem.title,
  invalidFields: Object.keys(invalidProblem.errors ?? {}).sort(),
};

const runDocker = (args) => execFileSync("docker", ["compose", ...args], {
  cwd: repositoryRoot,
  encoding: "utf8",
});

const containers = runDocker([
  "ps", "--format", "table {{.Service}}\t{{.Status}}\t{{.Ports}}",
  "policy-db", "redis", "keycloak",
]);
const postgres = runDocker([
  "exec", "-T", "policy-db", "sh", "-c",
  `psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off -c "select p.policy_number,p.status,c.service_code,c.limit_amount,c.used_amount,c.currency from policies p join policy_coverages c on c.policy_id=p.id where p.policy_number='${escapedPolicyNumber}';" -c "select a.action,a.actor_roles,a.correlation_id,a.reason_code from audit_records a join policies p on p.id=a.aggregate_id where p.policy_number='${escapedPolicyNumber}';" -c "select id from databasechangelog order by orderexecuted;" -c "select conname from pg_constraint where conrelid in ('policies'::regclass, 'policy_coverages'::regclass) and conname like 'chk_%' order by conname;" -c "select conname from pg_constraint where conrelid='audit_records'::regclass and conname='chk_policy_audit_changes_allowlist';" -c "select tgname from pg_trigger where tgrelid='audit_records'::regclass and not tgisinternal;"`,
]);
const redis = runDocker([
  "exec", "-T", "redis", "sh", "-c",
  "redis-cli --scan --pattern 'policy:coverage:v1:*' | while read key; do echo \"$key TYPE=$(redis-cli type \"$key\") TTL=$(redis-cli ttl \"$key\")s\"; done",
]);

const escapeHtml = (value) => String(value)
  .replaceAll("&", "&amp;")
  .replaceAll("<", "&lt;")
  .replaceAll(">", "&gt;");
const json = (value) => JSON.stringify(value, null, 2);

const browser = await chromium.launch({
  channel: process.env.PLAYWRIGHT_CHANNEL ?? "chrome",
  headless: true,
});
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });

try {
  await page.setContent(`
    <style>
      :root { color-scheme: dark; }
      body { margin: 0; padding: 36px; background: #0b1218; color: #e7edf2;
        font: 17px/1.45 Consolas, "Cascadia Mono", monospace; }
      h1 { margin: 0 0 8px; color: #50c8ff; font: 700 30px/1.2 system-ui; }
      .subtitle { margin-bottom: 26px; color: #9fb0bd; font-family: system-ui; }
      .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; }
      section { border: 1px solid #29404f; border-radius: 12px; background: #111d26;
        padding: 20px; overflow: hidden; }
      section.wide { grid-column: 1 / -1; }
      h2 { margin: 0 0 12px; color: #8ee3a6; font: 650 20px/1.2 system-ui; }
      pre { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; }
      .safe { margin-top: 22px; color: #9fb0bd; font: 14px/1.4 system-ui; }
    </style>
    <h1>Policy Service — live local runtime evidence</h1>
    <div class="subtitle">Java 21 · Spring Boot · Keycloak · PostgreSQL · Redis · synthetic data only</div>
    <div class="grid">
      <section><h2>Policy health</h2><pre>${escapeHtml(json(health))}</pre></section>
      <section><h2>Keycloak OIDC boundary</h2><pre>${escapeHtml(json({
        issuer: discovery.issuer,
        jwks_uri: discovery.jwks_uri,
      }))}</pre></section>
      <section class="wide"><h2>Coverage decision matrix</h2><pre>${escapeHtml(json(decisions))}</pre></section>
      <section class="wide"><h2>RFC 9457 validation evidence</h2><pre>${escapeHtml(json(validationEvidence))}</pre></section>
      <section class="wide"><h2>Docker Compose dependencies</h2><pre>${escapeHtml(containers)}</pre></section>
      <section class="wide"><h2>PostgreSQL source of truth and transactional audit</h2><pre>${escapeHtml(postgres)}</pre></section>
      <section class="wide"><h2>Redis cache keys and TTL</h2><pre>${escapeHtml(redis || "No live keys found")}</pre></section>
    </div>
    <div class="safe">Generated from a live local run. Tokens and passwords are process-only and are never rendered.</div>
  `);
  await page.screenshot({
    path: path.join(screenshotsDirectory, "18-policy-service-runtime.png"),
    fullPage: true,
  });
} finally {
  await browser.close();
}

console.log(JSON.stringify({ decisions, validationEvidence }, null, 2));
console.log(`Captured Policy runtime evidence in ${screenshotsDirectory}`);
