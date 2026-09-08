import { mkdir } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { chromium } from "playwright";

const kibanaUrl = process.env.KIBANA_URL ?? "http://localhost:5601";
const browserChannel = process.env.PLAYWRIGHT_CHANNEL ?? "chrome";
const screenshotsDirectory = fileURLToPath(
  new URL("../../../docs/screenshots/", import.meta.url),
);
await mkdir(screenshotsDirectory, { recursive: true });

const browser = await chromium.launch({ channel: browserChannel, headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });

try {
  await page.goto(`${kibanaUrl}/app/apm/services`, { waitUntil: "domcontentloaded", timeout: 60_000 });
  await page.waitForTimeout(10_000);
  const visibleText = await page.locator("body").innerText();
  if (!/(Services|Applications|APM|Observability)/i.test(visibleText)) {
    throw new Error(`Kibana APM page did not render. URL=${page.url()} title=${await page.title()} visible=${visibleText.slice(0, 500)}`);
  }
  await page.screenshot({
    path: path.join(screenshotsDirectory, "08-kibana-apm-services.png"),
    fullPage: true,
  });
} finally {
  await browser.close();
}

console.log(`Captured Kibana APM services in ${screenshotsDirectory}`);
