import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { preAuthorizationApi } from '@/entities/pre-authorization/api/pre-authorization-api'
import { PreAuthorizationsPage } from './PreAuthorizationsPage'

vi.mock('@/features/authentication/model/useAuth', () => ({
  useAuth: () => ({ hasRole: () => false }),
}))

afterEach(() => vi.restoreAllMocks())

describe('PreAuthorizationsPage', () => {
  it('loads the URL-filtered work queue and renders page metadata', async () => {
    const search = vi.spyOn(preAuthorizationApi, 'search').mockResolvedValue({
      content: [{
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
      }],
      page: 0,
      size: 10,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    })
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })

    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/pre-authorizations?status=PENDING&size=10']}>
          <PreAuthorizationsPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )

    expect(await screen.findByText('POL-100')).toBeInTheDocument()
    expect(screen.getByText('Page 1 of 1 · 1 records')).toBeInTheDocument()
    expect(search).toHaveBeenCalledWith(expect.objectContaining({
      status: 'PENDING',
      page: 0,
      size: 10,
      sortBy: 'createdAt',
      direction: 'desc',
    }))
  })
})
