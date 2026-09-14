import { expect, test, type Page } from '@playwright/test'

const password = process.env.DEMO_USER_PASSWORD
const policyNumber = process.env.DEMO_POLICY_NUMBER

test.skip(!password || !policyNumber, 'DEMO_USER_PASSWORD and DEMO_POLICY_NUMBER are required')

async function signIn(page: Page, username: string) {
  await page.goto('/')
  await page.getByRole('button', { name: 'Sign in with Keycloak' }).click()
  await page.getByRole('textbox', { name: 'Username or email' }).fill(username)
  await page.getByRole('textbox', { name: 'Password' }).fill(password!)
  await page.getByRole('button', { name: 'Sign In' }).click()
  await expect(page.getByRole('heading', { name: /Welcome,/ })).toBeVisible()
}

test('hospital navigation, filtered list, search and empty state respect scope', async ({ page }) => {
  await signIn(page, 'hospital-demo')
  await expect(page.getByRole('link', { name: 'Audit trail' })).toHaveCount(0)

  await page.getByRole('link', { name: 'Pre-authorizations' }).click()
  await page.getByRole('textbox', { name: 'Policy number' }).fill(policyNumber!)
  await page.getByRole('button', { name: 'Apply filters' }).click()
  await expect(page.getByRole('table', { name: 'Filtered pre-authorization work queue' }))
    .toContainText(policyNumber!)

  await page.getByRole('link', { name: 'Healthcare search' }).click()
  await page.getByRole('textbox', { name: 'Search text' }).fill(policyNumber!)
  await page.getByRole('button', { name: 'Search', exact: true }).click()
  await expect(page.getByRole('table', { name: 'Elasticsearch healthcare operations results' }))
    .toContainText(policyNumber!)

  await page.getByRole('textbox', { name: 'Search text' }).fill(`NO-MATCH-${Date.now()}`)
  await page.getByRole('button', { name: 'Search', exact: true }).click()
  await expect(page.getByText('No indexed records', { exact: true })).toBeVisible()

  await page.goto('/audit')
  await expect(page).toHaveURL(/\/forbidden$/)
})

test('system administrator can read the service-owned audit view', async ({ page }) => {
  await signIn(page, 'system-admin-demo')
  await page.getByRole('link', { name: 'Audit trail' }).click()
  await expect(page.getByRole('heading', { name: 'Audit trail' })).toBeVisible()
  await expect(page.getByRole('table', { name: 'Service-owned audit records' })).toBeVisible()
  await expect(page.getByText(/PRE_AUTHORIZATION_(SUBMITTED|APPROVED|REJECTED)/).first()).toBeVisible()
})
