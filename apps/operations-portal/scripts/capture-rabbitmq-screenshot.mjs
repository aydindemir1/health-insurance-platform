import { mkdir } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { chromium } from "playwright";

const requiredEnvironment = [
  "RABBITMQ_SCREENSHOT_USERNAME",
  "RABBITMQ_SCREENSHOT_PASSWORD",
];
for (const name of requiredEnvironment) {
  if (!process.env[name]) {
    throw new Error(`${name} is required and must remain a runtime-only value.`);
  }
}

const rabbitMqUrl = process.env.RABBITMQ_MANAGEMENT_URL ?? "http://localhost:15672";
const browserChannel = process.env.PLAYWRIGHT_CHANNEL ?? "chrome";
const screenshotsDirectory = fileURLToPath(
  new URL("../../../docs/screenshots/", import.meta.url),
);
await mkdir(screenshotsDirectory, { recursive: true });

const browser = await chromium.launch({ channel: browserChannel, headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });

try {
  await page.goto(rabbitMqUrl);
  await page.getByRole("textbox").nth(0).fill(process.env.RABBITMQ_SCREENSHOT_USERNAME);
  await page.getByRole("textbox").nth(1).fill(process.env.RABBITMQ_SCREENSHOT_PASSWORD);
  await page.getByRole("button", { name: "Login" }).click();
  await page.getByRole("link", { name: "Queues and Streams" }).click();
  await page.getByRole("heading", { name: "All queues (2)" }).waitFor();
  await page.getByRole("link", {
    name: "health.notifications.delivery.v1",
    exact: true,
  }).waitFor();
  await page.getByRole("link", {
    name: "health.notifications.delivery.v1.dlq",
    exact: true,
  }).waitFor();
  await page.screenshot({
    path: path.join(screenshotsDirectory, "06-rabbitmq-notification-queues.png"),
    fullPage: true,
  });
} finally {
  await browser.close();
}

console.log(`Captured RabbitMQ notification topology in ${screenshotsDirectory}`);
