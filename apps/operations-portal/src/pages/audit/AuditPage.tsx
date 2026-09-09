import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { type FormEvent } from 'react'
import { Navigate, useSearchParams } from 'react-router'
import { z } from 'zod'
import { auditApi } from '@/entities/audit-record/api/audit-api'
import type { AuditSearchCriteria, AuditService } from '@/entities/audit-record/model/types'
import { useAuth } from '@/features/authentication/model/useAuth'
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/AsyncState'
import { PageHeader } from '@/shared/ui/PageHeader'

const services = new Set<AuditService>(['authorization', 'policy', 'claims-billing'])
const optionalIdSchema = z.union([z.literal(''), z.uuid()])
const actions: Record<AuditService, string[]> = {
  authorization: ['PRE_AUTHORIZATION_SUBMITTED', 'PRE_AUTHORIZATION_APPROVED', 'PRE_AUTHORIZATION_REJECTED'],
  policy: ['POLICY_ISSUED'],
  'claims-billing': [
    'CLAIM_SUBMITTED', 'CLAIM_REVIEW_STARTED', 'CLAIM_APPROVED', 'CLAIM_REJECTED',
    'INVOICE_ISSUED', 'INVOICE_RECONCILED', 'INVOICE_VOIDED',
    'INVOICE_DISPUTE_RESOLVED', 'PAYMENT_RECORDED',
  ],
}

function nonNegativeInteger(value: string | null, fallback: number) {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed >= 0 ? parsed : fallback
}

export function AuditPage() {
  const auth = useAuth()
  const [urlParameters, setUrlParameters] = useSearchParams()
  const requestedService = urlParameters.get('service') as AuditService | null
  const service = requestedService && services.has(requestedService) ? requestedService : 'authorization'
  const page = nonNegativeInteger(urlParameters.get('page'), 0)
  const requestedSize = nonNegativeInteger(urlParameters.get('size'), 20)
  const size = [10, 20, 50, 100].includes(requestedSize) ? requestedSize : 20
  const requestedAction = urlParameters.get('action') ?? ''
  const criteria: AuditSearchCriteria = {
    service,
    aggregateId: urlParameters.get('aggregateId') || undefined,
    action: actions[service].includes(requestedAction) ? requestedAction : undefined,
    page,
    size,
  }
  const result = useQuery({
    queryKey: ['audit-records', criteria],
    queryFn: () => auditApi.search(criteria),
    placeholderData: keepPreviousData,
    enabled: auth.hasRole('SYSTEM_ADMIN'),
  })

  if (!auth.hasRole('SYSTEM_ADMIN')) return <Navigate to="/dashboard" replace />

  const applyFilters = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const aggregateId = String(form.get('aggregateId') ?? '').trim()
    const aggregateInput = event.currentTarget.elements.namedItem('aggregateId') as HTMLInputElement
    if (!optionalIdSchema.safeParse(aggregateId).success) {
      aggregateInput.setCustomValidity('Enter a valid aggregate UUID or leave this field empty.')
      aggregateInput.reportValidity()
      return
    }
    aggregateInput.setCustomValidity('')
    const next = new URLSearchParams({
      service: String(form.get('service')), size: String(form.get('size')), page: '0',
    })
    const action = String(form.get('action') ?? '')
    if (aggregateId) next.set('aggregateId', aggregateId)
    if (action) next.set('action', action)
    setUrlParameters(next)
  }

  const moveToPage = (nextPage: number) => {
    const next = new URLSearchParams(urlParameters)
    next.set('page', nextPage.toString())
    setUrlParameters(next)
  }

  return <>
    <PageHeader eyebrow="Governance evidence" title="Audit trail" />
    <section className="content-card filters-card">
      <div className="section-heading"><div><h2>Service-owned audit records</h2><p>Review minimized, append-only state-change evidence without joining service databases.</p></div></div>
      <form className="filter-grid" key={urlParameters.toString()} onSubmit={applyFilters}>
        <label>Owning service<select name="service" defaultValue={service} onChange={event => {
          setUrlParameters({ service: event.currentTarget.value, page: '0', size: size.toString() })
        }}><option value="authorization">Authorization</option><option value="policy">Policy</option><option value="claims-billing">Claims &amp; Billing</option></select></label>
        <label>Action<select name="action" defaultValue={criteria.action ?? ''}><option value="">All actions</option>{actions[service].map(action => <option key={action} value={action}>{action.replaceAll('_', ' ')}</option>)}</select></label>
        <label>Aggregate ID<input name="aggregateId" defaultValue={criteria.aggregateId ?? ''} placeholder="Optional UUID" /></label>
        <label>Rows<select name="size" defaultValue={size}><option value="10">10</option><option value="20">20</option><option value="50">50</option><option value="100">100</option></select></label>
        <div className="filter-actions"><button className="button">Apply filters</button><button className="button button--secondary" type="button" onClick={() => setUrlParameters({ service: 'authorization' })}>Clear</button></div>
      </form>
    </section>
    {result.isPending && <LoadingState label="Loading audit evidence…" />}
    {result.error && <ErrorState error={result.error} retry={() => void result.refetch()} />}
    {result.data?.content.length === 0 && <EmptyState title="No audit records">Change the service or filters, or complete a governed business transition.</EmptyState>}
    {result.data && result.data.content.length > 0 && <section className="content-card table-card">
      <div className="table-scroll"><table>
        <caption>Service-owned audit records</caption>
        <thead><tr><th>Occurred</th><th>Action</th><th>Aggregate</th><th>Actor</th><th>Transition</th><th>Correlation</th></tr></thead>
        <tbody>{result.data.content.map(record => <tr key={record.auditId}>
          <td>{new Date(record.occurredAt).toLocaleString('tr-TR')}<small>{record.retentionClass}</small></td>
          <td><strong>{record.action}</strong><small>{record.reasonCode}</small></td>
          <td><strong>{record.aggregateType}</strong><small><code>{record.aggregateId}</code></small></td>
          <td><strong>{record.actorSubject}</strong><small>{record.actorRoles.join(', ')}</small></td>
          <td><strong>{record.changes.fromStatus ?? 'Created'} → {record.changes.toStatus}</strong>{record.providerId && <small>Provider <code>{record.providerId}</code></small>}</td>
          <td><code>{record.correlationId}</code></td>
        </tr>)}</tbody>
      </table></div>
      <div className="pagination"><span>Page {result.data.page + 1} of {result.data.totalPages} · {result.data.totalElements} records</span><div>
        <button className="button button--secondary" disabled={result.data.first || result.isPlaceholderData} onClick={() => moveToPage(page - 1)}>Previous</button>
        <button className="button button--secondary" disabled={result.data.last || result.isPlaceholderData} onClick={() => moveToPage(page + 1)}>Next</button>
      </div></div>
    </section>}
  </>
}
