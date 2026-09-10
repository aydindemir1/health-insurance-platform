const required = [
  'HARBOR_API_URL',
  'HARBOR_PROJECT',
  'HARBOR_USERNAME',
  'HARBOR_PASSWORD',
  'HARBOR_IMAGE_TAG',
];

for (const name of required) {
  if (!process.env[name]) throw new Error(`${name} is required`);
}

const services = (process.env.HARBOR_REPOSITORIES ?? [
  'authorization-service',
  'policy-service',
  'claims-billing-service',
  'notification-worker',
  'search-service',
  'operations-portal',
].join(',')).split(',').map((value) => value.trim()).filter(Boolean);
const severityRank = { None: 0, Unknown: 0, Negligible: 0, Low: 1, Medium: 2, High: 3, Critical: 4 };
const maximum = process.env.HARBOR_MAX_ALLOWED_SEVERITY ?? 'High';
const deadline = Date.now() + 5 * 60 * 1000;
const authorization = Buffer.from(`${process.env.HARBOR_USERNAME}:${process.env.HARBOR_PASSWORD}`).toString('base64');

async function getScan(repository) {
  const origin = process.env.HARBOR_API_URL.replace(/\/$/, '');
  const path = `/api/v2.0/projects/${encodeURIComponent(process.env.HARBOR_PROJECT)}`
    + `/repositories/${encodeURIComponent(repository)}/artifacts/${encodeURIComponent(process.env.HARBOR_IMAGE_TAG)}`
    + '?with_scan_overview=true';
  const response = await fetch(origin + path, { headers: { Authorization: `Basic ${authorization}` } });
  if (!response.ok) throw new Error(`${repository}: Harbor API returned ${response.status}`);
  return Object.values((await response.json()).scan_overview ?? {})[0];
}

for (const service of services) {
  let scan;
  while (Date.now() < deadline) {
    scan = await getScan(service);
    if (scan?.scan_status === 'Success') break;
    if (scan?.scan_status === 'Error' || scan?.scan_status === 'Stopped') {
      throw new Error(`${service}: scan ended with ${scan.scan_status}`);
    }
    await new Promise((resolve) => setTimeout(resolve, 5000));
  }
  if (scan?.scan_status !== 'Success') throw new Error(`${service}: scan timed out`);
  const severity = scan.severity ?? 'Unknown';
  console.log(`${service}: scan=Success severity=${severity}`);
  if ((severityRank[severity] ?? 0) > severityRank[maximum]) {
    throw new Error(`${service}: ${severity} exceeds allowed severity ${maximum}`);
  }
}
