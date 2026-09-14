import { execFileSync } from "node:child_process";
import { mkdir } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { chromium } from "playwright";

const preAuthorizationId = process.env.AUTHORIZATION_SCREENSHOT_PRE_AUTHORIZATION_ID;
if (!preAuthorizationId) {
  throw new Error("AUTHORIZATION_SCREENSHOT_PRE_AUTHORIZATION_ID is required.");
}
if (!/^[0-9a-f-]{36}$/i.test(preAuthorizationId)) {
  throw new Error("The pre-authorization ID must be a UUID.");
}

const repositoryRoot = fileURLToPath(new URL("../../../", import.meta.url));
const screenshotsDirectory = path.join(repositoryRoot, "docs", "screenshots");
await mkdir(screenshotsDirectory, { recursive: true });

const authorizationBaseUrl = process.env.AUTHORIZATION_SCREENSHOT_BASE_URL ?? "http://localhost:8081";
const healthResponse = await fetch(`${authorizationBaseUrl}/actuator/health`);
if (!healthResponse.ok) throw new Error(`Authorization health returned ${healthResponse.status}.`);
const health = await healthResponse.json();

const unauthenticatedResponse = await fetch(`${authorizationBaseUrl}/api/v1/pre-authorizations`);
const unauthenticatedProblem = await unauthenticatedResponse.json();
if (unauthenticatedResponse.status !== 401) {
  throw new Error(`Unauthenticated request returned ${unauthenticatedResponse.status}, expected 401.`);
}

const runDocker = (args) => execFileSync("docker", ["compose", ...args], {
  cwd: repositoryRoot,
  encoding: "utf8",
});
const sql = [
  `select id,status,version,provider_id,created_at,decided_at from pre_authorizations where id='${preAuthorizationId}';`,
  "select count(*) as applied_migrations from databasechangelog;",
  "select count(*) as pre_authorization_check_constraints from information_schema.table_constraints where table_name='pre_authorizations' and constraint_type='CHECK';",
  `select action,correlation_id,occurred_at from audit_records where aggregate_id='${preAuthorizationId}' order by occurred_at;`,
  `select event_type,event_version,(published_at is not null) as broker_acknowledged,publish_attempts from outbox_messages where aggregate_id='${preAuthorizationId}' order by occurred_at;`,
  `select notification_type,task_version,(published_at is not null) as broker_acknowledged,publish_attempts from notification_task_outbox where business_reference_id='${preAuthorizationId}' order by occurred_at;`,
].join(" ");
const postgres = runDocker([
  "exec", "-T", "authorization-db", "sh", "-c",
  `psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off -c "${sql}"`,
]);
const containers = runDocker([
  "ps", "--format", "table {{.Service}}\t{{.Status}}\t{{.Ports}}",
  "authorization-db", "kafka", "rabbitmq", "keycloak",
]);
const queues = runDocker([
  "exec", "-T", "rabbitmq", "rabbitmqctl", "-q", "list_queues",
  "name", "messages", "consumers", "durable",
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
        font: 16px/1.45 Consolas, "Cascadia Mono", monospace; }
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
    <h1>Authorization Service — live local runtime evidence</h1>
    <div class="subtitle">Java 21 · Spring Boot · Keycloak · PostgreSQL/Liquibase · Kafka · RabbitMQ · synthetic data only</div>
    <div class="grid">
      <section><h2>Health</h2><pre>${escapeHtml(json(health))}</pre></section>
      <section><h2>RFC 9457 security boundary</h2><pre>${escapeHtml(json({
        httpStatus: unauthenticatedResponse.status,
        contentType: unauthenticatedResponse.headers.get("content-type"),
        type: unauthenticatedProblem.type,
        title: unauthenticatedProblem.title,
      }))}</pre></section>
      <section class="wide"><h2>Docker dependencies</h2><pre>${escapeHtml(containers)}</pre></section>
      <section class="wide"><h2>PostgreSQL state, Liquibase, invariants, audit and broker acknowledgements</h2><pre>${escapeHtml(postgres)}</pre></section>
      <section class="wide"><h2>RabbitMQ durable queues</h2><pre>${escapeHtml(queues)}</pre></section>
    </div>
    <div class="safe">Generated from a live local run. Broker acknowledgement is recorded only after the relay succeeds. No token, password, payload, diagnosis or member identity is rendered.</div>
  `);
  await page.screenshot({
    path: path.join(screenshotsDirectory, "19-authorization-service-runtime.png"),
    fullPage: true,
  });
} finally {
  await browser.close();
}

console.log(`Captured Authorization runtime evidence in ${screenshotsDirectory}`);
