import { execFileSync } from "node:child_process";
import { mkdir } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { chromium } from "playwright";

const claimId = process.env.CLAIMS_SCREENSHOT_CLAIM_ID;
if (!claimId || !/^[0-9a-f-]{36}$/i.test(claimId)) {
  throw new Error("CLAIMS_SCREENSHOT_CLAIM_ID must be a synthetic UUID.");
}
const root = fileURLToPath(new URL("../../../", import.meta.url));
const output = path.join(root, "docs", "screenshots");
await mkdir(output, { recursive: true });
const run = (args) => execFileSync("docker", ["compose", ...args], { cwd: root, encoding: "utf8" });
const service = (name) => run(["ps", "--format", "table {{.Service}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}", name]);
const postgres = run(["exec", "-T", "claims-billing-db", "sh", "-c",
  `psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off -c "select c.id,c.status as claim_status,c.version as claim_version,i.id as invoice_id,i.status as invoice_status,i.version as invoice_version,(select count(*) from invoice_payments p where p.invoice_id=i.id) as payment_count from claims c join invoices i on i.claim_id=c.id where c.id='${claimId}'; select id,dateexecuted from databasechangelog order by orderexecuted; select action,correlation_id,occurred_at from audit_records where aggregate_id in ('${claimId}',(select id from invoices where claim_id='${claimId}')) order by occurred_at; select count(*) as search_projection_outbox_rows from claim_search_outbox where claim_id='${claimId}';"`]);
const kafka = run(["exec", "-T", "kafka", "sh", "-c",
  "printf 'Authorization source topic and DLT partitions:\\n'; find /tmp/kafka-logs -maxdepth 1 -type d -name 'health.authorization.pre-authorization.v1*' | sed 's#.*/##' | sort"]);
const inbox = run(["exec", "-T", "claims-billing-db", "sh", "-c",
  `psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off -c "select pm.message_id,pm.consumer_name,pm.processed_at from processed_messages pm join audit_records a on a.correlation_id=pm.message_id::text where a.aggregate_id='${claimId}' and a.action='CLAIM_SUBMITTED'; select count(*) as claims_for_pre_authorization from claims where pre_authorization_id=(select pre_authorization_id from claims where id='${claimId}');"`]);

const escape = (value) => String(value).replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;");
const browser = await chromium.launch({ channel: process.env.PLAYWRIGHT_CHANNEL ?? "chrome", headless: true });
const capture = async (filename, title, subtitle, sections) => {
  const page = await browser.newPage({ viewport: { width: 1440, height: 950 } });
  await page.setContent(`
    <style>
      :root{color-scheme:dark} body{margin:0;padding:36px;background:#0b1218;color:#e7edf2;font:16px/1.45 Consolas,"Cascadia Mono",monospace}
      h1{margin:0 0 8px;color:#50c8ff;font:700 30px/1.2 system-ui}.subtitle,.safe{color:#9fb0bd;font-family:system-ui}.subtitle{margin-bottom:24px}
      section{border:1px solid #29404f;border-radius:12px;background:#111d26;padding:20px;margin:16px 0;overflow:hidden}h2{margin:0 0 12px;color:#8ee3a6;font:650 20px/1.2 system-ui}pre{margin:0;white-space:pre-wrap;overflow-wrap:anywhere}.safe{margin-top:20px;font-size:14px}
    </style><h1>${escape(title)}</h1><div class="subtitle">${escape(subtitle)}</div>
    ${sections.map(([heading, body]) => `<section><h2>${escape(heading)}</h2><pre>${escape(body)}</pre></section>`).join("")}
    <div class="safe">Live local evidence · synthetic identifiers only · no credentials, tokens, amounts, policy/member data, payment references or message bodies</div>`);
  await page.screenshot({ path: path.join(output, filename), fullPage: true });
  await page.close();
};

try {
  await capture("23-claims-billing-postgresql-runtime.png", "Claims & Billing — PostgreSQL lifecycle", "Claim approval · invoice settlement · optimistic versions · Liquibase · audit · search outbox", [
    ["Database container", service("claims-billing-db")], ["Transactional state evidence", postgres],
  ]);
  await capture("24-claims-billing-kafka-consumer-runtime.png", "Claims & Billing — Kafka idempotent consumer", "Authorization approval topic · DLT partitions · durable processed-message inbox · unique Claim creation", [
    ["Kafka broker", service("kafka")], ["Topic storage metadata", kafka], ["Consumer idempotency evidence", inbox],
  ]);
} finally { await browser.close(); }

console.log(`Captured Claims/Billing runtime evidence in ${output}`);
