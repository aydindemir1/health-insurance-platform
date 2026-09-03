import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { z } from 'zod'
import { useAuth } from '@/features/authentication/model/useAuth'
import { EmptyState } from '@/shared/ui/AsyncState'
import { PageHeader } from '@/shared/ui/PageHeader'

const idSchema = z.uuid()

export function PreAuthorizationsPage() {
  const auth = useAuth()
  const navigate = useNavigate()
  const [id, setId] = useState('')
  const [error, setError] = useState('')
  const submit = (event: FormEvent) => {
    event.preventDefault()
    const parsed = idSchema.safeParse(id.trim())
    if (!parsed.success) return setError('Enter a valid pre-authorization UUID.')
    navigate(`/pre-authorizations/${parsed.data}`)
  }
  return (
    <>
      <PageHeader eyebrow="Clinical finance" title="Pre-authorizations" actions={
        auth.hasRole('HOSPITAL_USER') ? <Link className="button" to="/pre-authorizations/new">New request</Link> : undefined
      } />
      <section className="content-card lookup-card">
        <div><h2>Find a request</h2><p>Retrieve the current server state using the authorization identifier.</p></div>
        <form onSubmit={submit} noValidate>
          <label htmlFor="authorization-id">Pre-authorization ID</label>
          <div className="inline-field"><input id="authorization-id" value={id} onChange={(event) => { setId(event.target.value); setError('') }} placeholder="10000000-0000-0000-0000-000000000001" /><button className="button">Open</button></div>
          {error && <span className="field-error">{error}</span>}
        </form>
      </section>
      <EmptyState title="No server-side list endpoint yet">The current backend exposes secure lookup by ID. Filtered and paginated listing will be added with an explicit query port instead of maintaining a duplicate client-side store.</EmptyState>
    </>
  )
}
