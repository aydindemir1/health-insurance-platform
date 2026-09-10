import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { preAuthorizationApi, preAuthorizationKeys, type PreAuthorization } from '@/entities/pre-authorization'
import { ApiError } from '@/shared/api/http-client'
import { DecisionPanel } from './DecisionPanel'

vi.mock('@/features/authentication', () => ({
  useAuth: () => ({ hasRole: (role: string) => role === 'INSURANCE_SPECIALIST' }),
}))

const authorization: PreAuthorization = {
  id: '10000000-0000-0000-0000-000000000001',
  memberId: '20000000-0000-0000-0000-000000000001',
  providerId: '30000000-0000-0000-0000-000000000001',
  policyNumber: 'POL-100',
  serviceCode: 'IMG-MRI',
  diagnosisCode: 'J18.9',
  requestedAmount: 1250,
  currency: 'TRY',
  status: 'PENDING',
  decisionReason: null,
  createdAt: '2026-09-03T12:00:00Z',
  decidedAt: null,
}

afterEach(() => vi.restoreAllMocks())

function renderPanel() {
  const client = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  const invalidate = vi.spyOn(client, 'invalidateQueries')
  render(<QueryClientProvider client={client}><DecisionPanel authorization={authorization} /></QueryClientProvider>)
  return { client, invalidate }
}

describe('DecisionPanel', () => {
  it('updates the detail cache and invalidates lists after a decision', async () => {
    const updated = { ...authorization, status: 'APPROVED' as const, decisionReason: 'Eligible' }
    vi.spyOn(preAuthorizationApi, 'approve').mockResolvedValue(updated)
    const { client, invalidate } = renderPanel()

    await userEvent.click(screen.getByRole('button', { name: 'Confirm approve' }))

    await waitFor(() => expect(client.getQueryData(preAuthorizationKeys.detail(authorization.id))).toEqual(updated))
    expect(invalidate).toHaveBeenCalledWith({ queryKey: preAuthorizationKeys.lists })
  })

  it('reloads current data instead of replaying a stale decision', async () => {
    const approve = vi.spyOn(preAuthorizationApi, 'approve').mockRejectedValue(
      new ApiError(409, { title: 'Concurrent update' }),
    )
    const { invalidate } = renderPanel()

    await userEvent.click(screen.getByRole('button', { name: 'Confirm approve' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Reload current request' }))

    expect(approve).toHaveBeenCalledOnce()
    expect(invalidate).toHaveBeenCalledWith({ queryKey: preAuthorizationKeys.detail(authorization.id) })
  })
})
