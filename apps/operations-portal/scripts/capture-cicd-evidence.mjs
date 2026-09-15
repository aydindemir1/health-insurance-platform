import { chromium } from 'playwright';
import path from 'node:path';
import { execFileSync } from 'node:child_process';

const required = ['JENKINS_ADMIN_USERNAME', 'JENKINS_ADMIN_PASSWORD', 'HARBOR_USERNAME', 'HARBOR_PASSWORD', 'NEXUS_ADMIN_PASSWORD'];
for (const name of required) {
  if (!process.env[name]) throw new Error(`${name} is required`);
}

const output = path.resolve('../../docs/screenshots');
const browser = await chromium.launch({
  headless: true,
  executablePath: process.env.PLAYWRIGHT_CHROME_PATH ?? 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
});
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });

const basic = Buffer.from(`${process.env.JENKINS_ADMIN_USERNAME}:${process.env.JENKINS_ADMIN_PASSWORD}`).toString('base64');
await page.setExtraHTTPHeaders({ Authorization: `Basic ${basic}` });
await page.goto('http://localhost:8086/job/health-insurance-platform/10/', { waitUntil: 'networkidle' });
await page.screenshot({ path: path.join(output, '12-jenkins-supply-chain.png'), fullPage: true });

const harborBasic = Buffer.from(`${process.env.HARBOR_USERNAME}:${process.env.HARBOR_PASSWORD}`).toString('base64');
await page.setExtraHTTPHeaders({ Authorization: `Basic ${harborBasic}` });
await page.goto('http://localhost:8088/harbor/projects', { waitUntil: 'domcontentloaded' });
await page.waitForTimeout(5000);
await page.screenshot({ path: path.join(output, '13-harbor-artifacts.png'), fullPage: true });

const argoContext = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 1000 } });
const argo = await argoContext.newPage();
await argo.goto('https://localhost:18080', { waitUntil: 'domcontentloaded' });
await argo.locator('input').nth(0).fill('admin');
const argoPassword = Buffer.from(execFileSync('kubectl', ['--context', 'portfolio-ci', '-n', 'argocd', 'get', 'secret', 'argocd-initial-admin-secret', '-o', 'jsonpath={.data.password}'], { encoding: 'utf8' }).trim(), 'base64').toString('utf8');
await argo.locator('input[type="password"]').fill(argoPassword);
await argo.locator('button[type="submit"]').click();
await argo.waitForTimeout(5000);
await argo.screenshot({ path: path.join(output, '14-argocd-gitops-sync.png'), fullPage: true });
await argoContext.close();

const nexusBasic = Buffer.from(`admin:${process.env.NEXUS_ADMIN_PASSWORD}`).toString('base64');
await page.setExtraHTTPHeaders({ Authorization: `Basic ${nexusBasic}` });
await page.goto('http://localhost:8087/#browse/browse:maven-snapshots', { waitUntil: 'domcontentloaded' });
await page.waitForTimeout(5000);
for (let step = 0; step < 3; step += 1) {
  const next = page.getByRole('button', { name: /next|finish/i }).last();
  if (await next.isVisible().catch(() => false)) await next.click();
}
await page.waitForTimeout(1000);
await page.screenshot({ path: path.join(output, '15-nexus-maven-artifacts.png'), fullPage: true });

const escape = (value) => value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
const evidence = async (title, command, file) => {
  await page.setExtraHTTPHeaders({});
  await page.setContent(`<style>body{background:#101820;color:#e8eef2;font:18px Consolas,monospace;padding:36px}h1{color:#43b9ff}pre{white-space:pre-wrap;line-height:1.5;background:#17242e;padding:24px;border-radius:10px}</style><h1>${title}</h1><pre>${escape(command)}</pre>`);
  await page.screenshot({ path: path.join(output, file), fullPage: true });
};

const docker = execFileSync('docker', ['ps', '--format', 'table {{.Names}}\t{{.Status}}\t{{.Image}}'], { encoding: 'utf8' });
await evidence('Docker runtime — Milestone 12', docker, '16-docker-cicd-runtime.png');

const pods = execFileSync('kubectl', ['--context', 'portfolio-ci', 'get', 'pods', '-n', 'argocd'], { encoding: 'utf8' });
const application = execFileSync('kubectl', ['--context', 'portfolio-ci', 'get', 'application', 'health-insurance-staging', '-n', 'argocd'], { encoding: 'utf8' });
const deployments = execFileSync('kubectl', ['--context', 'portfolio-ci', 'get', 'deployments', '-n', 'health-insurance', '-o', 'custom-columns=NAME:.metadata.name,IMAGE:.spec.template.spec.containers[0].image'], { encoding: 'utf8' });
await evidence('Kubernetes and Argo CD — Verified delivery', `${pods}\n${application}\n${deployments}`, '17-kubernetes-argocd-runtime.png');

await browser.close();
