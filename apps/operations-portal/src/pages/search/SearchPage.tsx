import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { type FormEvent } from 'react'
import { useSearchParams } from 'react-router'
import { z } from 'zod'
import { searchApi } from '@/entities/search-record/api/search-api'
import type { SearchCriteria, SearchRecordType } from '@/entities/search-record/model/types'
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/AsyncState'
import { PageHeader } from '@/shared/ui/PageHeader'

const optionalIdSchema = z.union([z.literal(''), z.uuid()])
const recordTypes = new Set<SearchRecordType>(['PRE_AUTHORIZATION', 'CLAIM'])

function nonNegativeInteger(value: string | null, fallback: number) {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed >= 0 ? parsed : fallback
}

export function SearchPage() {
  const [urlParameters, setUrlParameters] = useSearchParams()
  const page = nonNegativeInteger(urlParameters.get('page'), 0)
  const requestedSize = nonNegativeInteger(urlParameters.get('size'), 20)
  const size = [10, 20, 50].includes(requestedSize) ? requestedSize : 20
  const typeValue = urlParameters.get('type') as SearchRecordType | null
  const criteria: SearchCriteria = {
    query: urlParameters.get('q') || undefined,
    type: typeValue && recordTypes.has(typeValue) ? typeValue : undefined,
    status: urlParameters.get('status') || undefined,
    providerId: urlParameters.get('providerId') || undefined,
    page,
    size,
  }
  const result = useQuery({
    queryKey: ['operations-search', criteria],
    queryFn: () => searchApi.search(criteria),
    placeholderData: keepPreviousData,
  })

  const applyFilters = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const providerId = String(form.get('providerId') ?? '').trim()
    const providerInput = event.currentTarget.elements.namedItem('providerId') as HTMLInputElement
    if (!optionalIdSchema.safeParse(providerId).success) {
      providerInput.setCustomValidity('Enter a valid provider UUID or leave this field empty.')
      providerInput.reportValidity()
      return
    }
    providerInput.setCustomValidity('')
    const next = new URLSearchParams()
    for (const name of ['q', 'type', 'status', 'providerId', 'size']) {
      const value = String(form.get(name) ?? '').trim()
      if (value) next.set(name, value)
    }
    next.set('page', '0')
    setUrlParameters(next)
  }

  const moveToPage = (nextPage: number) => {
    const next = new URLSearchParams(urlParameters)
    next.set('page', nextPage.toString())
    setUrlParameters(next)
  }
  const totalPages = result.data ? Math.ceil(result.data.totalElements / result.data.size) : 0

  return <>
    <PageHeader eyebrow="Operations index" title="Healthcare search" />
    <section className="content-card filters-card">
      <div className="section-heading">
        <div><h2>Claims and pre-authorizations</h2><p>Search denormalized read models without coupling services to one another's databases.</p></div>
      </div>
      <form className="filter-grid" key={urlParameters.toString()} onSubmit={applyFilters}>
        <label>Search text<input name="q" defaultValue={criteria.query ?? ''} placeholder="Policy, invoice, service or identifier" /></label>
        <label>Record type<select name="type" defaultValue={criteria.type ?? ''}><option value="">All records</option><option value="PRE_AUTHORIZATION">Pre-authorization</option><option value="CLAIM">Claim</option></select></label>
        <label>Status<input name="status" defaultValue={criteria.status ?? ''} placeholder="For example APPROVED" /></label>
        <label>Provider ID<input name="providerId" defaultValue={criteria.providerId ?? ''} placeholder="Optional UUID" /></label>
        <label>Rows<select name="size" defaultValue={size}><option value="10">10</option><option value="20">20</option><option value="50">50</option></select></label>
        <div className="filter-actions"><button className="button">Search</button><button className="button button--secondary" type="button" onClick={() => setUrlParameters({})}>Clear</button></div>
      </form>
    </section>
    {result.isPending && <LoadingState label="Searching the operations index…" />}
    {result.error && <ErrorState error={result.error} retry={() => void result.refetch()} />}
    {result.data?.content.length === 0 && <EmptyState title="No indexed records">Change the search filters or complete a pre-authorization decision to create a projection.</EmptyState>}
    {result.data && result.data.content.length > 0 && <section className="content-card table-card">
      <div className="table-scroll"><table>
        <caption>Elasticsearch healthcare operations results</caption>
        <thead><tr><th>Type</th><th>Policy / service</th><th>Financial state</th><th>Provider</th><th>Updated</th></tr></thead>
        <tbody>{result.data.content.map(record => <tr key={record.id}>
          <td><span className={`record-type record-type--${record.type.toLowerCase()}`}>{record.type === 'CLAIM' ? 'Claim' : 'Pre-authorization'}</span><small>{record.sourceId}</small></td>
          <td><strong>{record.policyNumber}</strong><small>{record.serviceCode} · Member {record.memberId}</small></td>
          <td><strong>{record.status}</strong><small>{new Intl.NumberFormat('tr-TR', { style: 'currency', currency: record.currency }).format(record.amount)}{record.invoiceStatus ? ` · Invoice ${record.invoiceStatus}` : ''}</small></td>
          <td><code>{record.providerId}</code></td>
          <td>{new Date(record.occurredAt).toLocaleString('tr-TR')}</td>
        </tr>)}</tbody>
      </table></div>
      <div className="pagination"><span>Page {result.data.page + 1} of {totalPages} · {result.data.totalElements} records</span><div>
        <button className="button button--secondary" disabled={page === 0 || result.isPlaceholderData} onClick={() => moveToPage(page - 1)}>Previous</button>
        <button className="button button--secondary" disabled={page + 1 >= totalPages || result.isPlaceholderData} onClick={() => moveToPage(page + 1)}>Next</button>
      </div></div>
    </section>}
  </>
}
