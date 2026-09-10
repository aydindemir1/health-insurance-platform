import { Link } from 'react-router'

export function ForbiddenPage() {
  return (
    <section className="content-card state-panel" role="alert">
      <span className="eyebrow">Access denied</span>
      <h1>You do not have permission to open this page.</h1>
      <p>Your identity is valid, but the requested operation requires a different role.</p>
      <Link className="button button--secondary" to="/dashboard">Return to dashboard</Link>
    </section>
  )
}
