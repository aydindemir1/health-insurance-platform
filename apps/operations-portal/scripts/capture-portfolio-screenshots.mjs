import { mkdir } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";
import { chromium } from "playwright";

const requiredEnvironment = ["DEMO_USER_PASSWORD", "DEMO_POLICY_NUMBER"];
for (const name of requiredEnvironment) {
  if (!process.env[name]) {
    throw new Error(`${name} is required and must remain a runtime-only value.`);
  }
}

const portalUrl = process.env.DEMO_PORTAL_URL ?? "http://localhost:5173";
const browserChannel = process.env.PLAYWRIGHT_CHANNEL ?? "chrome";
const screenshotsDirectory = fileURLToPath(
  new URL("../../../docs/screenshots/", import.meta.url),
);
await mkdir(screenshotsDirectory, { recursive: true });

const browser = await chromium.launch({ channel: browserChannel, headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });

async function signIn(username) {
  await page.getByRole("button", { name: "Sign in with Keycloak" }).click();
  await page.getByRole("textbox", { name: "Username or email" }).fill(username);
  await page.getByRole("textbox", { name: "Password" }).fill(process.env.DEMO_USER_PASSWORD);
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.getByRole("heading", { name: "Welcome, Synthetic Demo" }).waitFor();
}

async function capture(filename) {
  await page.screenshot({
    path: path.join(screenshotsDirectory, filename),
    fullPage: true,
  });
}

async function openPendingDetail() {
  await page.getByRole("link", { name: "Pre-authorizations" }).click();
  const pendingRow = page.getByRole("row").filter({ hasText: "PENDING" }).first();
  await pendingRow.getByRole("link", { name: "Review" }).click();
  await page.getByText("Pre-authorization detail").waitFor();
}

try {
  await page.goto(portalUrl);
  await signIn("hospital-demo");
  await capture("01-dashboard.png");

  await page.getByRole("link", { name: "Pre-authorizations" }).click();
  await page.getByRole("table", { name: "Filtered pre-authorization work queue" }).waitFor();
  await capture("02-pre-authorization-work-queue.png");

  await page.getByRole("link", { name: "New request" }).click();
  await page.getByRole("textbox", { name: "Member ID" })
    .fill("20000000-0000-0000-0000-000000000001");
  await page.getByRole("textbox", { name: "Policy number" })
    .fill(process.env.DEMO_POLICY_NUMBER);
  await page.getByRole("textbox", { name: "Service code" }).fill("IMG-MRI");
  await page.getByRole("textbox", { name: "Diagnosis code" }).fill("M25.5");
  await page.getByRole("textbox", { name: "Requested amount" }).fill("1850.00");
  await capture("03-submit-pre-authorization.png");

  await openPendingDetail();
  await capture("04-pre-authorization-detail.png");

  await page.getByRole("button", { name: "Sign out" }).click();
  await page.getByRole("button", { name: "Sign in with Keycloak" }).waitFor();
  await signIn("insurance-demo");
  await openPendingDetail();
  await page.getByRole("textbox", { name: "Decision reason" })
    .fill("Synthetic demo: coverage and clinical rules verified");
  await capture("05-specialist-decision.png");
} finally {
  await browser.close();
}

console.log(`Captured five synthetic screenshots in ${screenshotsDirectory}`);
