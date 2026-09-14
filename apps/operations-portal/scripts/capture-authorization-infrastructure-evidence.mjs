import { execFileSync } from "node:child_process";
import { mkdir } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { chromium } from "playwright";

const id = process.env.AUTHORIZATION_SCREENSHOT_PRE_AUTHORIZATION_ID;
if (!id || !/^[0-9a-f-]{36}$/i.test(id)) {
  throw new Error("AUTHORIZATION_SCREENSHOT_PRE_AUTHORIZATION_ID must be a synthetic UUID.");
}

const root = fileURLToPath(new URL("../../../", import.meta.url));
const output = path.join(root, "docs", "screenshots");
await mkdir(output, { recursive: true });
const run = (args) => execFileSync("docker", ["compose", ...args], {
  cwd: root,
  encoding: "utf8",
});
const service = (name) => run(["ps", "--format", "table {{.Service}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}", name]);
const postgres = run(["exec", "-T", "authorization-db", "sh", "-c",
  `psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off -c "select id,status,version,created_at,decided_at from pre_authorizations where id='${id}'; select id,author,dateexecuted from databasechangelog order by orderexecuted; select constraint_name from information_schema.table_constraints where table_name='pre_authorizations' and constraint_type='CHECK' order by constraint_name; select action,correlation_id,occurred_at from audit_records where aggregate_id='${id}' order by occurred_at;"`]);
const kafka = run(["exec", "-T", "kafka", "sh", "-c",
  "printf 'Persisted topic partitions and DLT partitions:\\n'; find /tmp/kafka-logs -maxdepth 1 -type d -name 'health.authorization.pre-authorization.v1*' | sed 's#.*/##' | sort"]);
const kafkaOutbox = run(["exec", "-T", "authorization-db", "sh", "-c",
  `psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off -c "select event_type,event_version,occurred_at,published_at,(published_at is not null) as broker_acknowledged,publish_attempts,last_error from outbox_messages where aggregate_id='${id}' order by occurred_at;"`]);
const rabbit = run(["exec", "-T", "rabbitmq", "rabbitmqctl", "-q", "list_queues",
  "name", "messages", "consumers", "durable", "arguments"]);
const rabbitBindings = run(["exec", "-T", "rabbitmq", "rabbitmqctl", "-q", "list_bindings",
  "source_name", "destination_name", "routing_key"]);
const rabbitOutbox = run(["exec", "-T", "authorization-db", "sh", "-c",
  `psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off -c "select notification_type,task_version,occurred_at,published_at,(published_at is not null) as broker_acknowledged,publish_attempts,last_error from notification_task_outbox where business_reference_id='${id}' order by occurred_at;"`]);

const escape = (value) => String(value).replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
const browser = await chromium.launch({ channel: process.env.PLAYWRIGHT_CHANNEL ?? "chrome", headless: true });

const capture = async (filename, title, subtitle, sections) => {
  const page = await browser.newPage({ viewport: { width: 1440, height: 950 } });
  await page.setContent(`
    <style>
      :root { color-scheme: dark; } body { margin:0; padding:36px; background:#0b1218; color:#e7edf2; font:16px/1.45 Consolas,"Cascadia Mono",monospace; }
      h1 { margin:0 0 8px; color:#50c8ff; font:700 30px/1.2 system-ui; } .subtitle,.safe { color:#9fb0bd; font-family:system-ui; }
      .subtitle { margin-bottom:24px; } section { border:1px solid #29404f; border-radius:12px; background:#111d26; padding:20px; margin:16px 0; overflow:hidden; }
      h2 { margin:0 0 12px; color:#8ee3a6; font:650 20px/1.2 system-ui; } pre { margin:0; white-space:pre-wrap; overflow-wrap:anywhere; }
      .safe { margin-top:20px; font-size:14px; }
    </style>
    <h1>${escape(title)}</h1><div class="subtitle">${escape(subtitle)}</div>
    ${sections.map(([heading, body]) => `<section><h2>${escape(heading)}</h2><pre>${escape(body)}</pre></section>`).join("")}
    <div class="safe">Live local evidence · synthetic identifiers only · no credentials, tokens, business payloads or message bodies</div>`);
  await page.screenshot({ path: path.join(output, filename), fullPage: true });
  await page.close();
};

try {
  await capture("20-authorization-postgresql-runtime.png", "Authorization — PostgreSQL and Liquibase", "Source of truth · lifecycle invariants · optimistic version · minimized audit", [
    ["Container", service("authorization-db")], ["Aggregate, migrations, constraints and audit", postgres],
  ]);
  await capture("21-authorization-kafka-runtime.png", "Authorization — Kafka integration event", "Durable topic partitions · transactional outbox · producer acknowledgement", [
    ["Broker", service("kafka")], ["Broker topic storage metadata", kafka], ["Kafka outbox publication evidence", kafkaOutbox],
  ]);
  await capture("22-authorization-rabbitmq-runtime.png", "Authorization — RabbitMQ notification task", "Durable queue · DLQ routing · transactional task outbox · publisher acknowledgement", [
    ["Broker", service("rabbitmq")], ["Queues", rabbit], ["Bindings", rabbitBindings], ["Notification outbox publication evidence", rabbitOutbox],
  ]);
} finally {
  await browser.close();
}

console.log(`Captured PostgreSQL, Kafka and RabbitMQ evidence in ${output}`);
