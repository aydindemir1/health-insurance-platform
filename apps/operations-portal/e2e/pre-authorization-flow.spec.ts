import { expect, test, type Page } from '@playwright/test'

const password = process.env.DEMO_USER_PASSWORD
const policyNumber = process.env.DEMO_POLICY_NUMBER
const memberId = process.env.DEMO_MEMBER_ID ?? '20000000-0000-0000-0000-000000000001'

test.skip(!password || !policyNumber, 'DEMO_USER_PASSWORD and DEMO_POLICY_NUMBER are required')

async function signIn(page: Page, username: string) {
  await page.getByRole('button', { name: 'Sign in with Keycloak' }).click()
  await page.getByRole('textbox', { name: 'Username or email' }).fill(username)
  await page.getByRole('textbox', { name: 'Password' }).fill(password!)
  await page.getByRole('button', { name: 'Sign In' }).click()
  await expect(page.getByRole('heading', { name: /Welcome,/ })).toBeVisible()
}

test('hospital submission can be approved by an insurance specialist', async ({ page }) => {
  await page.goto('/')
  await signIn(page, 'hospital-demo')
  await page.getByRole('link', { name: 'New request' }).click()
  await page.getByRole('textbox', { name: 'Member ID' }).fill(memberId)
  await page.getByRole('textbox', { name: 'Policy number' }).fill(policyNumber!)
  await page.getByRole('textbox', { name: 'Service code' }).fill('IMG-MRI')
  await page.getByRole('textbox', { name: 'Diagnosis code' }).fill('M25.5')
  await page.getByRole('textbox', { name: 'Requested amount' }).fill('1.00')
  await page.getByRole('button', { name: 'Submit pre-authorization' }).click()
  await expect(page.getByText('PENDING', { exact: true })).toBeVisible()
  const detailUrl = page.url()

  await page.getByRole('button', { name: 'Sign out' }).click()
  await signIn(page, 'insurance-demo')
  await page.goto(detailUrl)
  await page.getByRole('textbox', { name: 'Decision reason' }).fill('Automated portfolio acceptance test')
  await page.getByRole('button', { name: 'Confirm approve' }).click()

  await expect(page.getByText('APPROVED', { exact: true })).toBeVisible()
})
