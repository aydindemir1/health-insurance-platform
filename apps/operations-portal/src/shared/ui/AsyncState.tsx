import type { ReactNode } from 'react'
import { ApiError } from '@/shared/api/http-client'

export function LoadingState({ label = 'Loading data…' }: { label?: string }) {
  return <div className="state-panel" role="status"><span className="spinner" />{label}</div>
}

export function EmptyState({ title, children }: { title: string; children: ReactNode }) {
  return <div className="state-panel"><strong>{title}</strong><p>{children}</p></div>
}

export function ErrorState({ error, retry }: { error: Error; retry?: () => void }) {
  const message = error instanceof ApiError
    ? error.problem.detail ?? error.problem.title ?? error.message
    : error.message
  return (
    <div className="state-panel state-panel--error" role="alert">
      <strong>We could not complete the request</strong>
      <p>{message}</p>
      {retry && <button className="button button--secondary" onClick={retry}>Try again</button>}
    </div>
  )
}
