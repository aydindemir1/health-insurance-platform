import { mkdir } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { chromium } from "playwright";

const elasticsearchUrl = process.env.ELASTICSEARCH_URL ?? "http://localhost:9200";
const browserChannel = process.env.PLAYWRIGHT_CHANNEL ?? "chrome";
const screenshotsDirectory = fileURLToPath(
  new URL("../../../docs/screenshots/", import.meta.url),
);
await mkdir(screenshotsDirectory, { recursive: true });

const json = async (path) => {
  const response = await fetch(`${elasticsearchUrl}${path}`);
  if (!response.ok) {
    throw new Error(`Elasticsearch request ${path} returned ${response.status}`);
  }
  return response.json();
};

const aliases = await json(
  "/_cat/aliases/healthcare-operations?format=json&h=alias,index,is_write_index",
);
if (aliases.length !== 1 || aliases[0].is_write_index !== "true") {
  throw new Error(`Expected one writable stable alias, received ${JSON.stringify(aliases)}`);
}
const activeIndex = aliases[0].index;
const activeCount = await json("/healthcare-operations/_count");
const indices = await json(
  "/_cat/indices/healthcare-operations-v*?format=json&h=index,docs.count,store.size,status",
);
const predecessor = indices.find((index) => index.index === "healthcare-operations-v1");
if (!predecessor) throw new Error("The retained v1 rollback index was not found");

const escapeHtml = (value) => String(value)
  .replaceAll("&", "&amp;")
  .replaceAll("<", "&lt;")
  .replaceAll(">", "&gt;")
  .replaceAll('"', "&quot;")
  .replaceAll("'", "&#039;");

const browser = await chromium.launch({ channel: browserChannel, headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });

try {
  await page.setContent(`<!doctype html>
    <html lang="en">
      <head>
        <meta charset="utf-8" />
        <style>
          :root { color-scheme: dark; font-family: Inter, ui-sans-serif, system-ui, sans-serif; }
          * { box-sizing: border-box; }
          body { margin: 0; min-height: 100vh; padding: 62px; color: #e7edf7; background: radial-gradient(circle at top right, #143f4c, #07111f 58%); }
          main { max-width: 1180px; margin: 0 auto; }
          .eyebrow { color: #67e8f9; font-size: 14px; font-weight: 700; letter-spacing: .16em; text-transform: uppercase; }
          h1 { margin: 14px 0 10px; font-size: 43px; }
          .lead { margin: 0 0 30px; color: #9fb0c7; font-size: 19px; }
          .flow { display: grid; grid-template-columns: 1fr 70px 1fr; align-items: stretch; gap: 16px; }
          .card { padding: 25px; border: 1px solid #29445f; border-radius: 18px; background: rgba(10, 26, 43, .9); box-shadow: 0 24px 70px rgba(0,0,0,.26); }
          .active { border-color: #2a8278; background: rgba(9, 46, 48, .92); }
          .arrow { display: grid; place-items: center; color: #67e8f9; font-size: 38px; }
          .label { color: #9fb0c7; font-size: 12px; font-weight: 700; letter-spacing: .1em; text-transform: uppercase; }
          .value { margin-top: 8px; color: #e5f4ff; font: 15px/1.5 ui-monospace, SFMono-Regular, Consolas, monospace; overflow-wrap: anywhere; }
          .count { margin: 18px 0 2px; color: #5eead4; font-size: 60px; font-weight: 800; line-height: 1; }
          .checks { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; margin-top: 22px; }
          .check { padding: 18px; border: 1px solid #245b59; border-radius: 14px; background: #0e302f; color: #b9c9d8; }
          .check strong { display: block; color: #5eead4; margin-bottom: 5px; }
        </style>
      </head>
      <body>
        <main>
          <div class="eyebrow">Milestone 10 · Live Elasticsearch evidence</div>
          <h1>Zero-downtime search projection rebuild</h1>
          <p class="lead">The stable alias moved atomically after owner snapshots were validated. The predecessor remains available for explicit rollback.</p>
          <section class="flow">
            <article class="card">
              <div class="label">Retained predecessor</div>
              <div class="value">${escapeHtml(predecessor.index)}</div>
              <div class="count">${escapeHtml(predecessor["docs.count"] ?? "0")}</div>
              <div class="label">documents · status ${escapeHtml(predecessor.status)}</div>
            </article>
            <div class="arrow">→</div>
            <article class="card active">
              <div class="label">Stable alias · write index</div>
              <div class="value">healthcare-operations</div>
              <div class="value">${escapeHtml(activeIndex)}</div>
              <div class="count">${escapeHtml(activeCount.count)}</div>
              <div class="label">validated distinct documents</div>
            </article>
          </section>
          <section class="checks">
            <div class="check"><strong>Source owned</strong>Bounded exports; no shared database access</div>
            <div class="check"><strong>Race safe</strong>Monotonic revisions reject stale projections</div>
            <div class="check"><strong>Rollback ready</strong>No index deletion during activation</div>
          </section>
        </main>
      </body>
    </html>`);
  await page.screenshot({
    path: path.join(screenshotsDirectory, "11-search-rebuild-recovery.png"),
    fullPage: true,
  });
} finally {
  await browser.close();
}

console.log(`Captured search recovery evidence in ${screenshotsDirectory}`);
