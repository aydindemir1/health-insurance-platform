import { afterEach, describe, expect, it, vi } from 'vitest'
import { z } from 'zod'
import { apiRequest, setAccessTokenProvider, setUnauthorizedHandler } from './http-client'

afterEach(() => {
  vi.unstubAllGlobals()
  setAccessTokenProvider(async () => undefined)
  setUnauthorizedHandler(async () => undefined)
})

describe('apiRequest', () => {
  it('adds the refreshed bearer token', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ id: '1' }), { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)
    setAccessTokenProvider(async () => 'verified-token')

    await apiRequest('/pre-authorizations/1')

    const request = fetchMock.mock.calls[0]![1] as RequestInit
    expect(new Headers(request.headers).get('Authorization')).toBe('Bearer verified-token')
  })

  it('preserves RFC 9457 details on failed requests', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ title: 'Concurrent update', status: 409, detail: 'Reload the request' }), { status: 409, headers: { 'Content-Type': 'application/problem+json', 'X-Correlation-ID': 'request-123' } })))

    await expect(apiRequest('/pre-authorizations/1')).rejects.toMatchObject({
      status: 409,
      problem: { title: 'Concurrent update', detail: 'Reload the request', correlationId: 'request-123' },
    })
  })

  it('rejects a response that violates its runtime contract', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ id: 42 }), { status: 200 })))

    await expect(apiRequest('/resource', {
      responseSchema: z.object({ id: z.string() }),
    })).rejects.toBeInstanceOf(z.ZodError)
  })

  it('invokes the session recovery handler for an unauthorized response', async () => {
    const recover = vi.fn().mockResolvedValue(undefined)
    setUnauthorizedHandler(recover)
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{}', { status: 401 })))

    await expect(apiRequest('/protected')).rejects.toMatchObject({ status: 401 })
    expect(recover).toHaveBeenCalledOnce()
  })

  it('cancels fetch when the caller aborts the request', async () => {
    const fetchMock = vi.fn((_url: string, init?: RequestInit) => new Promise<Response>((_resolve, reject) => {
      const rejectAbort = () => reject(init?.signal?.reason)
      if (init?.signal?.aborted) rejectAbort()
      else init?.signal?.addEventListener('abort', rejectAbort, { once: true })
    }))
    vi.stubGlobal('fetch', fetchMock)
    const controller = new AbortController()
    const request = apiRequest('/slow', { signal: controller.signal })

    controller.abort()

    await expect(request).rejects.toMatchObject({ name: 'AbortError' })
  })
})
