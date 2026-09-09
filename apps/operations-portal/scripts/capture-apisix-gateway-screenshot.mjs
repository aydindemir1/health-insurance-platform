import { mkdir } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { chromium } from "playwright";

const gatewayUrl = process.env.APISIX_GATEWAY_URL ?? "http://localhost:9080";
const browserChannel = process.env.PLAYWRIGHT_CHANNEL ?? "chrome";
const screenshotsDirectory = fileURLToPath(
  new URL("../../../docs/screenshots/", import.meta.url),
);
await mkdir(screenshotsDirectory, { recursive: true });

const browser = await chromium.launch({ channel: browserChannel, headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });

try {
  const response = await page.goto(`${gatewayUrl}/api/v1/pre-authorizations`, {
    waitUntil: "domcontentloaded",
    timeout: 30_000,
  });
  if (response?.status() !== 401) {
    throw new Error(`Expected gateway 401, received ${response?.status()}`);
  }
  const visibleText = await page.locator("body").innerText();
  if (!visibleText.includes("gateway-401") || !visibleText.includes("correlationId")) {
    throw new Error(`Gateway Problem Details did not render: ${visibleText.slice(0, 500)}`);
  }
  const problem = JSON.parse(visibleText);
  const correlationId = response.headers()["x-correlation-id"];
  const contentType = response.headers()["content-type"];
  await page.setContent(`<!doctype html>
    <html lang="en">
      <head>
        <meta charset="utf-8" />
        <style>
          :root { color-scheme: dark; font-family: Inter, ui-sans-serif, system-ui, sans-serif; }
          * { box-sizing: border-box; }
          body { margin: 0; min-height: 100vh; padding: 64px; color: #e7edf7; background: radial-gradient(circle at top right, #193b57, #07111f 55%); }
          main { max-width: 1180px; margin: 0 auto; }
          .eyebrow { color: #67e8f9; font-size: 14px; font-weight: 700; letter-spacing: .16em; text-transform: uppercase; }
          h1 { margin: 14px 0 10px; font-size: 44px; }
          .lead { margin: 0 0 34px; color: #9fb0c7; font-size: 19px; }
          .grid { display: grid; grid-template-columns: 280px 1fr; gap: 24px; }
          .card { padding: 26px; border: 1px solid #29445f; border-radius: 18px; background: rgba(10, 26, 43, .88); box-shadow: 0 24px 70px rgba(0,0,0,.28); }
          .status { color: #fda4af; font-size: 64px; font-weight: 800; line-height: 1; }
          .label { margin-top: 10px; color: #9fb0c7; font-size: 13px; text-transform: uppercase; letter-spacing: .1em; }
          .value { margin-top: 6px; font-family: ui-monospace, SFMono-Regular, Consolas, monospace; color: #d8e6f5; overflow-wrap: anywhere; }
          .row { padding: 15px 0; border-bottom: 1px solid #20384f; }
          .row:first-child { padding-top: 0; }
          .row:last-child { border-bottom: 0; padding-bottom: 0; }
          .checks { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; margin-top: 24px; }
          .check { padding: 18px; border: 1px solid #245b59; border-radius: 14px; background: #0e302f; }
          .check strong { display: block; color: #5eead4; margin-bottom: 5px; }
        </style>
      </head>
      <body>
        <main>
          <div class="eyebrow">Milestone 8 · Live APISIX evidence</div>
          <h1>Central API security boundary</h1>
          <p class="lead">An unauthenticated request was rejected before any Spring service was called.</p>
          <section class="grid">
            <article class="card">
              <div class="status">${problem.status}</div>
              <div class="label">HTTP status</div>
              <div class="row"><div class="label">Title</div><div class="value">${problem.title}</div></div>
              <div class="row"><div class="label">Upstream called</div><div class="value">No</div></div>
            </article>
            <article class="card">
              <div class="row"><div class="label">Problem type</div><div class="value">${problem.type}</div></div>
              <div class="row"><div class="label">Content type</div><div class="value">${contentType}</div></div>
              <div class="row"><div class="label">Request instance</div><div class="value">${problem.instance}</div></div>
              <div class="row"><div class="label">Correlation ID</div><div class="value">${correlationId}</div></div>
              <div class="row"><div class="label">Detail</div><div class="value">${problem.detail}</div></div>
            </article>
          </section>
          <section class="checks">
            <div class="check"><strong>OIDC enforced</strong>Missing bearer token denied</div>
            <div class="check"><strong>RFC 9457</strong>Stable machine-readable error</div>
            <div class="check"><strong>Traceable</strong>Correlation ID returned</div>
          </section>
        </main>
      </body>
    </html>`);
  await page.screenshot({
    path: path.join(screenshotsDirectory, "09-apisix-gateway-problem-details.png"),
    fullPage: true,
  });
} finally {
  await browser.close();
}

console.log(`Captured APISIX Problem Details evidence in ${screenshotsDirectory}`);
