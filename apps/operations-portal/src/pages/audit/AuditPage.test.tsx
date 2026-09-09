import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { auditApi } from '@/entities/audit-record/api/audit-api'
import { AuditPage } from './AuditPage'

vi.mock('@/features/authentication/model/useAuth', () => ({
  useAuth: () => ({ hasRole: (role: string) => role === 'SYSTEM_ADMIN' }),
}))

afterEach(() => vi.restoreAllMocks())

describe('AuditPage', () => {
  it('loads the selected service page and renders minimized transition evidence', async () => {
    const search = vi.spyOn(auditApi, 'search').mockResolvedValue({
      content: [{
        auditId: '10000000-0000-0000-0000-000000000001', aggregateType: 'CLAIM',
        aggregateId: '20000000-0000-0000-0000-000000000001', action: 'CLAIM_APPROVED',
        actorSubject: 'claim-approver', actorRoles: ['CLAIM_APPROVER'],
        providerId: '30000000-0000-0000-0000-000000000001', correlationId: 'correlation-100',
        occurredAt: '2026-09-09T12:00:00Z', reasonCode: 'CLAIM_DECISION',
        changes: { fromStatus: 'IN_REVIEW', toStatus: 'APPROVED' }, retentionClass: 'AUDIT_EVIDENCE',
      }], page: 0, size: 10, totalElements: 1, totalPages: 1, first: true, last: true,
    })
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

    render(<QueryClientProvider client={queryClient}><MemoryRouter initialEntries={['/audit?service=claims-billing&action=CLAIM_APPROVED&size=10']}><AuditPage /></MemoryRouter></QueryClientProvider>)

    expect(await screen.findByText('CLAIM_APPROVED')).toBeInTheDocument()
    expect(screen.getByText('IN_REVIEW → APPROVED')).toBeInTheDocument()
    expect(screen.getByText('Page 1 of 1 · 1 records')).toBeInTheDocument()
    expect(search).toHaveBeenCalledWith(expect.objectContaining({ service: 'claims-billing', action: 'CLAIM_APPROVED', page: 0, size: 10 }))
  })
})
