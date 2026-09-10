import { afterEach, describe, expect, it, vi } from 'vitest'
import { searchApi } from './search-api'

afterEach(() => vi.unstubAllGlobals())

describe('searchApi.search', () => {
  it('targets the search service and serializes projection filters', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      content: [], page: 1, size: 10, totalElements: 0,
    }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    await searchApi.search({
      query: 'POL-100', type: 'CLAIM', status: 'UNDER_REVIEW',
      providerId: '30000000-0000-0000-0000-000000000001', page: 1, size: 10,
    })

    const url = new URL(fetchMock.mock.calls[0]![0] as string)
    expect(url.origin).toBe('http://localhost:9080')
    expect(url.pathname).toBe('/api/v1/search')
    expect(Object.fromEntries(url.searchParams)).toEqual({
      page: '1', size: '10', q: 'POL-100', type: 'CLAIM', status: 'UNDER_REVIEW',
      providerId: '30000000-0000-0000-0000-000000000001',
    })
  })
})
