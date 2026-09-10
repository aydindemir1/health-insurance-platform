import { environment } from '@/shared/config/environment'
import type { ZodType } from 'zod'

export interface ProblemDetails {
  type?: string
  title?: string
  status?: number
  detail?: string
  errors?: Record<string, string>
  correlationId?: string
}

export class ApiError extends Error {
  readonly status: number
  readonly problem: ProblemDetails

  constructor(
    status: number,
    problem: ProblemDetails,
  ) {
    super(problem.detail ?? problem.title ?? `Request failed with status ${status}`)
    this.status = status
    this.problem = problem
  }
}

type AccessTokenProvider = () => Promise<string | undefined>
type UnauthorizedHandler = () => Promise<void>

let accessTokenProvider: AccessTokenProvider = async () => undefined
let unauthorizedHandler: UnauthorizedHandler = async () => undefined

export function setAccessTokenProvider(provider: AccessTokenProvider) {
  accessTokenProvider = provider
}

export function setUnauthorizedHandler(handler: UnauthorizedHandler) {
  unauthorizedHandler = handler
}

interface ApiRequestOptions<T> extends RequestInit {
  baseUrl?: string
  responseSchema?: ZodType<T>
  timeoutMs?: number
}

export async function apiRequest<T>(path: string, options: ApiRequestOptions<T> = {}): Promise<T> {
  const { baseUrl = environment.apiBaseUrl, responseSchema, timeoutMs = 10_000, ...init } = options
  const token = await accessTokenProvider()
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (!headers.has('X-Correlation-ID')) headers.set('X-Correlation-ID', crypto.randomUUID())

  const controller = new AbortController()
  const abortFromCaller = () => controller.abort(init.signal?.reason)
  if (init.signal?.aborted) abortFromCaller()
  else init.signal?.addEventListener('abort', abortFromCaller, { once: true })
  const timeout = setTimeout(() => controller.abort(new DOMException('Request timed out', 'TimeoutError')), timeoutMs)
  let response: Response
  try {
    response = await fetch(`${baseUrl}${path}`, { ...init, headers, signal: controller.signal })
  } finally {
    clearTimeout(timeout)
    init.signal?.removeEventListener('abort', abortFromCaller)
  }
  if (response.status === 401) await unauthorizedHandler()
  if (!response.ok) {
    const received = await response.json().catch(() => ({})) as ProblemDetails
    const correlationId = response.headers.get('X-Correlation-ID')
    const problem = correlationId && !received.correlationId
      ? { ...received, correlationId }
      : received
    throw new ApiError(response.status, problem)
  }
  if (response.status === 204) return undefined as T
  const payload: unknown = await response.json()
  return responseSchema ? responseSchema.parse(payload) : payload as T
}
