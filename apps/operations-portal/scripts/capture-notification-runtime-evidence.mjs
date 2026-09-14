import { execFileSync } from "node:child_process";
import { mkdir } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { chromium } from "playwright";

const root = fileURLToPath(new URL("../../../", import.meta.url));
const output = path.join(root, "docs", "screenshots");
await mkdir(output, { recursive: true });
const run = (args) => execFileSync("docker", ["compose", ...args], {
  cwd: root,
  encoding: "utf8",
});
const service = (name) => run([
  "ps", "--format", "table {{.Service}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}", name,
]);
const postgres = run(["exec", "-T", "notification-worker-db", "sh", "-c",
  `psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off -c "select task_id,notification_type,recipient_kind,status,received_at,delivered_at from notification_deliveries order by received_at desc limit 5; select id,dateexecuted from databasechangelog order by orderexecuted; select constraint_name from information_schema.table_constraints where table_name='notification_deliveries' and constraint_name like 'ck_%' order by constraint_name; select indexname from pg_indexes where tablename='notification_deliveries' order by indexname;"`]);
const rabbit = run(["exec", "-T", "rabbitmq", "rabbitmqctl", "list_queues",
  "name", "messages_ready", "messages_unacknowledged"]);
const bindings = run(["exec", "-T", "rabbitmq", "rabbitmqctl", "list_bindings",
  "source_name", "destination_name", "routing_key"]);
const healthResponse = await fetch("http://localhost:8085/actuator/health");
if (!healthResponse.ok) {
  throw new Error(`Notification Worker health returned ${healthResponse.status}.`);
}
const health = JSON.stringify(await healthResponse.json(), null, 2);

const escape = (value) => String(value)
  .replaceAll("&", "&amp;")
  .replaceAll("<", "&lt;")
  .replaceAll(">", "&gt;");
const browser = await chromium.launch({
  channel: process.env.PLAYWRIGHT_CHANNEL ?? "chrome",
  headless: true,
});
const page = await browser.newPage({ viewport: { width: 1440, height: 950 } });

try {
  await page.setContent(`
    <style>
      :root{color-scheme:dark} body{margin:0;padding:36px;background:#0b1218;color:#e7edf2;font:15px/1.42 Consolas,"Cascadia Mono",monospace}
      h1{margin:0 0 8px;color:#50c8ff;font:700 30px/1.2 system-ui}.subtitle,.safe{color:#9fb0bd;font-family:system-ui}.subtitle{margin-bottom:22px}
      section{border:1px solid #29404f;border-radius:12px;background:#111d26;padding:18px;margin:14px 0;overflow:hidden}h2{margin:0 0 10px;color:#8ee3a6;font:650 20px/1.2 system-ui}pre{margin:0;white-space:pre-wrap;overflow-wrap:anywhere}.safe{margin-top:18px;font-size:14px}
    </style>
    <h1>Notification Worker — live delivery evidence</h1>
    <div class="subtitle">RabbitMQ task · idempotent PostgreSQL delivery · unsupported-version DLQ</div>
    <section><h2>Source-run worker health</h2><pre>${escape(health)}</pre></section>
    <section><h2>Runtime containers</h2><pre>${escape(service("notification-worker-db") + service("rabbitmq"))}</pre></section>
    <section><h2>Owner database</h2><pre>${escape(postgres)}</pre></section>
    <section><h2>Queue and DLQ state</h2><pre>${escape(rabbit)}</pre></section>
    <section><h2>Durable bindings</h2><pre>${escape(bindings)}</pre></section>
    <div class="safe">Live local evidence · synthetic identifiers and operational metadata only · no credentials, tokens, contact addresses or message bodies</div>`);
  await page.screenshot({
    path: path.join(output, "25-notification-worker-runtime.png"),
    fullPage: true,
  });
} finally {
  await browser.close();
}

console.log(`Captured Notification Worker runtime evidence in ${output}`);
