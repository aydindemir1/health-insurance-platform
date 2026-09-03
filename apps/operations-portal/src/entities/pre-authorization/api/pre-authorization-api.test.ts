import { afterEach, describe, expect, it, vi } from 'vitest'
import { preAuthorizationApi } from './pre-authorization-api'

afterEach(() => vi.unstubAllGlobals())

describe('preAuthorizationApi.search', () => {
  it('serializes filters and pagination into the collection request', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      content: [],
      page: 2,
      size: 10,
      totalElements: 0,
      totalPages: 0,
      first: false,
      last: true,
    }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await preAuthorizationApi.search({
      status: 'PENDING',
      memberId: '20000000-0000-0000-0000-000000000001',
      policyNumber: 'POL-100',
      page: 2,
      size: 10,
      sortBy: 'requestedAmount',
      direction: 'asc',
    })

    const url = new URL(fetchMock.mock.calls[0][0] as string)
    expect(url.pathname).toBe('/api/v1/pre-authorizations')
    expect(Object.fromEntries(url.searchParams)).toEqual({
      page: '2',
      size: '10',
      sortBy: 'requestedAmount',
      direction: 'asc',
      status: 'PENDING',
      memberId: '20000000-0000-0000-0000-000000000001',
      policyNumber: 'POL-100',
    })
  })
})
