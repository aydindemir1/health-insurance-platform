import { environment } from '@/shared/config/environment'

export interface ProblemDetails {
  type?: string
  title?: string
  status?: number
  detail?: string
  errors?: Record<string, string>
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

let accessTokenProvider: AccessTokenProvider = async () => undefined

export function setAccessTokenProvider(provider: AccessTokenProvider) {
  accessTokenProvider = provider
}

export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = await accessTokenProvider()
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const response = await fetch(`${environment.apiBaseUrl}${path}`, { ...init, headers })
  if (!response.ok) {
    const problem = await response.json().catch(() => ({})) as ProblemDetails
    throw new ApiError(response.status, problem)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}
