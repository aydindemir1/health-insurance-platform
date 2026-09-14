import axe from 'axe-core'
import { expect, test, type Page } from '@playwright/test'

const password = process.env.DEMO_USER_PASSWORD
const screenshotPath = process.env.PORTFOLIO_SCREENSHOT_PATH

test.skip(!password, 'DEMO_USER_PASSWORD is required')

async function signIn(page: Page) {
  await page.goto('/')
  await page.getByRole('button', { name: 'Sign in with Keycloak' }).click()
  await page.getByRole('textbox', { name: 'Username or email' }).fill('hospital-demo')
  await page.getByRole('textbox', { name: 'Password' }).fill(password!)
  await page.getByRole('button', { name: 'Sign In' }).click()
  await expect(page.getByRole('heading', { name: /Welcome,/ })).toBeVisible()
}

test('authenticated operations page is accessible and usable on a mobile viewport', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await signIn(page)
  await page.getByRole('link', { name: 'Pre-authorizations' }).click()
  await expect(page.getByRole('heading', { name: 'Pre-authorizations' })).toBeVisible()
  await expect(page.getByRole('status')).toBeHidden()
  await expect(page.getByRole('alert')).toHaveCount(0)

  await page.addScriptTag({ content: axe.source })
  const violations = await page.evaluate(async () => {
    const result = await window.axe.run(document)
    return result.violations.map(violation => ({
      id: violation.id,
      targets: violation.nodes.flatMap(node => node.target),
    }))
  })
  expect(violations).toEqual([])

  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  const dashboardLink = page.getByRole('link', { name: 'Dashboard' })
  await dashboardLink.focus()
  await expect(dashboardLink).toBeFocused()
  await page.keyboard.press('Tab')
  await expect(page.getByRole('link', { name: 'Pre-authorizations' })).toBeFocused()
  if (screenshotPath) await page.screenshot({ path: screenshotPath, fullPage: true })
})
