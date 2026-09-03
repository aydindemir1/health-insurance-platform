import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { z } from 'zod'
import { preAuthorizationApi } from '@/entities/pre-authorization/api/pre-authorization-api'
import type {
  PreAuthorizationSearch,
  PreAuthorizationSortField,
  PreAuthorizationStatus,
  SortDirection,
} from '@/entities/pre-authorization/model/types'
import { StatusBadge } from '@/entities/pre-authorization/ui/StatusBadge'
import { useAuth } from '@/features/authentication/model/useAuth'
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/AsyncState'
import { PageHeader } from '@/shared/ui/PageHeader'

const optionalIdSchema = z.union([z.literal(''), z.uuid()])
const allowedStatuses = new Set<PreAuthorizationStatus>(['PENDING', 'APPROVED', 'REJECTED'])
const allowedSortFields = new Set<PreAuthorizationSortField>(['createdAt', 'requestedAmount', 'status'])

function positiveInteger(value: string | null, fallback: number) {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed >= 0 ? parsed : fallback
}

export function PreAuthorizationsPage() {
  const auth = useAuth()
  const navigate = useNavigate()
  const [urlParameters, setUrlParameters] = useSearchParams()
  const page = positiveInteger(urlParameters.get('page'), 0)
  const requestedSize = positiveInteger(urlParameters.get('size'), 20)
  const size = [10, 20, 50].includes(requestedSize) ? requestedSize : 20
  const statusValue = urlParameters.get('status') as PreAuthorizationStatus | null
  const status = statusValue && allowedStatuses.has(statusValue) ? statusValue : undefined
  const sortValue = urlParameters.get('sortBy') as PreAuthorizationSortField | null
  const sortBy = sortValue && allowedSortFields.has(sortValue) ? sortValue : 'createdAt'
  const direction: SortDirection = urlParameters.get('direction') === 'asc' ? 'asc' : 'desc'
  const memberId = urlParameters.get('memberId') ?? ''
  const policyNumber = urlParameters.get('policyNumber') ?? ''
  const search: PreAuthorizationSearch = {
    status,
    memberId: memberId || undefined,
    policyNumber: policyNumber || undefined,
    page,
    size,
    sortBy,
    direction,
  }
  const query = useQuery({
    queryKey: ['pre-authorizations', status, memberId, policyNumber, page, size, sortBy, direction],
    queryFn: () => preAuthorizationApi.search(search),
    placeholderData: keepPreviousData,
  })

  const applyFilters = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const next = new URLSearchParams()
    const nextMemberId = String(form.get('memberId') ?? '').trim()
    const memberInput = event.currentTarget.elements.namedItem('memberId') as HTMLInputElement
    if (!optionalIdSchema.safeParse(nextMemberId).success) {
      memberInput.setCustomValidity('Enter a valid member UUID or leave this field empty.')
      memberInput.reportValidity()
      return
    }
    memberInput.setCustomValidity('')
    for (const name of ['status', 'memberId', 'policyNumber', 'size', 'sortBy', 'direction']) {
      const value = String(form.get(name) ?? '').trim()
      if (value) next.set(name, value)
    }
    next.set('page', '0')
    setUrlParameters(next)
  }

  const openById = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const id = String(new FormData(event.currentTarget).get('authorizationId') ?? '').trim()
    if (z.uuid().safeParse(id).success) navigate(`/pre-authorizations/${id}`)
  }

  const moveToPage = (nextPage: number) => {
    const next = new URLSearchParams(urlParameters)
    next.set('page', nextPage.toString())
    setUrlParameters(next)
  }

  return (
    <>
      <PageHeader eyebrow="Clinical finance" title="Pre-authorizations" actions={
        auth.hasRole('HOSPITAL_USER')
          ? <Link className="button" to="/pre-authorizations/new">New request</Link>
          : undefined
      } />

      <section className="content-card filters-card">
        <div className="section-heading">
          <div><h2>Work queue</h2><p>Filter server-owned data without copying it into client state.</p></div>
          <form className="quick-open" onSubmit={openById}>
            <input name="authorizationId" aria-label="Pre-authorization ID" placeholder="Open by UUID" />
            <button className="button button--secondary">Open</button>
          </form>
        </div>
        <form className="filter-grid" key={urlParameters.toString()} onSubmit={applyFilters}>
          <label>Status
            <select name="status" defaultValue={status ?? ''}>
              <option value="">All statuses</option>
              <option value="PENDING">Pending</option>
              <option value="APPROVED">Approved</option>
              <option value="REJECTED">Rejected</option>
            </select>
          </label>
          <label>Member ID
            <input name="memberId" defaultValue={memberId} placeholder="Optional UUID" />
          </label>
          <label>Policy number
            <input name="policyNumber" defaultValue={policyNumber} placeholder="Exact policy number" />
          </label>
          <label>Sort by
            <select name="sortBy" defaultValue={sortBy}>
              <option value="createdAt">Submitted date</option>
              <option value="requestedAmount">Requested amount</option>
              <option value="status">Status</option>
            </select>
          </label>
          <label>Direction
            <select name="direction" defaultValue={direction}>
              <option value="desc">Descending</option>
              <option value="asc">Ascending</option>
            </select>
          </label>
          <label>Rows
            <select name="size" defaultValue={size}>
              <option value="10">10</option>
              <option value="20">20</option>
              <option value="50">50</option>
            </select>
          </label>
          <div className="filter-actions">
            <button className="button">Apply filters</button>
            <Link className="button button--secondary" to="/pre-authorizations">Clear</Link>
          </div>
        </form>
      </section>

      {query.isPending && <LoadingState label="Loading pre-authorizations…" />}
      {query.error && <ErrorState error={query.error} retry={() => void query.refetch()} />}
      {query.data && query.data.content.length === 0 && (
        <EmptyState title="No matching pre-authorizations">
          Change the filters or create a new request if your role permits it.
        </EmptyState>
      )}
      {query.data && query.data.content.length > 0 && (
        <section className="content-card table-card">
          <div className="table-scroll">
            <table>
              <caption>Filtered pre-authorization work queue</caption>
              <thead><tr><th>Policy</th><th>Member</th><th>Amount</th><th>Status</th><th>Submitted</th><th /></tr></thead>
              <tbody>{query.data.content.map((item) => (
                <tr key={item.id}>
                  <td><strong>{item.policyNumber}</strong><small>{item.serviceCode} · {item.diagnosisCode}</small></td>
                  <td><code>{item.memberId}</code></td>
                  <td>{new Intl.NumberFormat('tr-TR', { style: 'currency', currency: item.currency }).format(item.requestedAmount)}</td>
                  <td><StatusBadge status={item.status} /></td>
                  <td>{new Date(item.createdAt).toLocaleString('tr-TR')}</td>
                  <td><Link className="table-link" to={`/pre-authorizations/${item.id}`}>Review</Link></td>
                </tr>
              ))}</tbody>
            </table>
          </div>
          <div className="pagination">
            <span>Page {query.data.page + 1} of {query.data.totalPages} · {query.data.totalElements} records</span>
            <div>
              <button className="button button--secondary" disabled={query.data.first || query.isPlaceholderData} onClick={() => moveToPage(page - 1)}>Previous</button>
              <button className="button button--secondary" disabled={query.data.last || query.isPlaceholderData} onClick={() => moveToPage(page + 1)}>Next</button>
            </div>
          </div>
        </section>
      )}
    </>
  )
}
