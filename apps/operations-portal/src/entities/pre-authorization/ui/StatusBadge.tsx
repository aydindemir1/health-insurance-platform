import type { PreAuthorizationStatus } from '../model/types'

export function StatusBadge({ status }: { status: PreAuthorizationStatus }) {
  return <span className={`status status--${status.toLowerCase()}`}>{status}</span>
}
