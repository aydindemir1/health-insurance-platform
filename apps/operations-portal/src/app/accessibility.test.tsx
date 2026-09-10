import axe from 'axe-core'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { SubmitPreAuthorizationForm } from '@/features/submit-pre-authorization'
import { ForbiddenPage } from '@/pages/forbidden/ForbiddenPage'

async function expectNoViolations(container: HTMLElement) {
  const result = await axe.run(container, {
    rules: { 'color-contrast': { enabled: false } },
  })
  expect(result.violations.map(violation => ({
    id: violation.id,
    targets: violation.nodes.flatMap(node => node.target),
  }))).toEqual([])
}

describe('critical page accessibility', () => {
  it('has no detectable violations in the pre-authorization form', async () => {
    const { container } = render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter><SubmitPreAuthorizationForm /></MemoryRouter>
      </QueryClientProvider>,
    )

    await expectNoViolations(container)
  })

  it('has no detectable violations in the forbidden state', async () => {
    const { container } = render(<MemoryRouter><ForbiddenPage /></MemoryRouter>)
    await expectNoViolations(container)
  })
})
