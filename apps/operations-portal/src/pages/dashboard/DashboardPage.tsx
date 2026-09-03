import { Link } from 'react-router'
import { useAuth } from '@/features/authentication/model/useAuth'
import { PageHeader } from '@/shared/ui/PageHeader'

export function DashboardPage() {
  const auth = useAuth()
  return (
    <>
      <PageHeader eyebrow="Operations overview" title={`Welcome, ${auth.displayName}`} />
      <section className="metric-grid" aria-label="Current capabilities">
        <article className="metric-card"><span>Authorization API</span><strong>Connected</strong><small>Type-safe client configured</small></article>
        <article className="metric-card"><span>Your access</span><strong>{auth.roles.size}</strong><small>Active realm role{auth.roles.size === 1 ? '' : 's'}</small></article>
        <article className="metric-card"><span>Decision model</span><strong>Protected</strong><small>Optimistic concurrency enabled</small></article>
      </section>
      <section className="content-card dashboard-callout">
        <div><span className="eyebrow">Start a workflow</span><h2>Pre-authorization operations</h2><p>Submit a hospital request or retrieve an existing case using its identifier.</p></div>
        <div className="button-row">
          {auth.hasRole('HOSPITAL_USER') && <Link className="button" to="/pre-authorizations/new">New request</Link>}
          <Link className="button button--secondary" to="/pre-authorizations">Find a request</Link>
        </div>
      </section>
    </>
  )
}
