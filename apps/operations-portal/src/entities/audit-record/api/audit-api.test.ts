import { afterEach, describe, expect, it, vi } from 'vitest'
import { auditApi } from './audit-api'

afterEach(() => vi.restoreAllMocks())

describe('auditApi', () => {
  it('uses the selected service-owned endpoint and encodes filters', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true,
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))

    await auditApi.search({
      service: 'claims-billing', aggregateId: '10000000-0000-0000-0000-000000000001',
      action: 'CLAIM_APPROVED', page: 2, size: 20,
    })

    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining(
      '/claims/audit-records?page=2&size=20&aggregateId=10000000-0000-0000-0000-000000000001&action=CLAIM_APPROVED'),
    expect.anything())
  })
})
