import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiRequest, setAccessTokenProvider } from './http-client'

afterEach(() => {
  vi.unstubAllGlobals()
  setAccessTokenProvider(async () => undefined)
})

describe('apiRequest', () => {
  it('adds the refreshed bearer token', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ id: '1' }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    setAccessTokenProvider(async () => 'verified-token')

    await apiRequest('/pre-authorizations/1')

    const request = fetchMock.mock.calls[0][1] as RequestInit
    expect(new Headers(request.headers).get('Authorization')).toBe('Bearer verified-token')
  })

  it('preserves RFC 9457 details on failed requests', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ title: 'Concurrent update', status: 409, detail: 'Reload the request' }), { status: 409, headers: { 'Content-Type': 'application/problem+json' } })))

    await expect(apiRequest('/pre-authorizations/1')).rejects.toMatchObject({
      status: 409,
      problem: { title: 'Concurrent update', detail: 'Reload the request' },
    })
  })
})
