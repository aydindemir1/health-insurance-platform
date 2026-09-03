import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { preAuthorizationApi } from '@/entities/pre-authorization/api/pre-authorization-api'
import { StatusBadge } from '@/entities/pre-authorization/ui/StatusBadge'
import { DecisionPanel } from '@/features/review-pre-authorization/ui/DecisionPanel'
import { ErrorState, LoadingState } from '@/shared/ui/AsyncState'
import { PageHeader } from '@/shared/ui/PageHeader'

export function PreAuthorizationDetailPage() {
  const { id = '' } = useParams()
  const query = useQuery({ queryKey: ['pre-authorization', id], queryFn: () => preAuthorizationApi.getById(id), enabled: Boolean(id) })
  if (query.isPending) return <LoadingState label="Loading pre-authorization…" />
  if (query.error) return <ErrorState error={query.error} retry={() => void query.refetch()} />
  const item = query.data
  return (
    <>
      <PageHeader eyebrow="Pre-authorization detail" title={item.policyNumber} actions={<Link className="button button--secondary" to="/pre-authorizations">Back to lookup</Link>} />
      <section className="content-card detail-card">
        <div className="detail-heading"><div><small>Authorization ID</small><code>{item.id}</code></div><StatusBadge status={item.status} /></div>
        <dl className="detail-grid">
          <div><dt>Member ID</dt><dd>{item.memberId}</dd></div><div><dt>Provider ID</dt><dd>{item.providerId}</dd></div>
          <div><dt>Service</dt><dd>{item.serviceCode}</dd></div><div><dt>Diagnosis</dt><dd>{item.diagnosisCode}</dd></div><div><dt>Requested amount</dt><dd>{new Intl.NumberFormat('tr-TR', { style: 'currency', currency: item.currency }).format(item.requestedAmount)}</dd></div>
          <div><dt>Submitted</dt><dd>{new Date(item.createdAt).toLocaleString('tr-TR')}</dd></div><div><dt>Decision reason</dt><dd>{item.decisionReason ?? '—'}</dd></div>
        </dl>
      </section>
      <DecisionPanel authorization={item} />
    </>
  )
}
