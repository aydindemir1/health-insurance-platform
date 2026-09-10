import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { preAuthorizationApi, preAuthorizationKeys, type PreAuthorization } from '@/entities/pre-authorization'
import { useAuth } from '@/features/authentication'
import { ErrorState } from '@/shared/ui/AsyncState'
import { ApiError } from '@/shared/api/http-client'

export function DecisionPanel({ authorization }: { authorization: PreAuthorization }) {
  const auth = useAuth()
  const queryClient = useQueryClient()
  const [reason, setReason] = useState('')
  const [action, setAction] = useState<'approve' | 'reject'>('approve')
  const mutation = useMutation({
    mutationFn: () => action === 'approve'
      ? preAuthorizationApi.approve(authorization.id, reason)
      : preAuthorizationApi.reject(authorization.id, reason),
    onSuccess: (updated) => {
      queryClient.setQueryData(preAuthorizationKeys.detail(authorization.id), updated)
      void queryClient.invalidateQueries({ queryKey: preAuthorizationKeys.lists })
    },
  })
  if (!auth.hasRole('INSURANCE_SPECIALIST') || authorization.status !== 'PENDING') return null
  const invalidRejection = action === 'reject' && !reason.trim()
  const concurrentUpdate = mutation.error instanceof ApiError && mutation.error.status === 409
  const reloadCurrentDecision = () => {
    mutation.reset()
    void queryClient.invalidateQueries({ queryKey: preAuthorizationKeys.detail(authorization.id) })
  }
  return (
    <section className="content-card decision-panel">
      <div><span className="eyebrow">Specialist action</span><h2>Record a decision</h2><p>The server will reject a stale decision if another specialist updates this request first.</p></div>
      <div className="segmented" role="group" aria-label="Decision type">
        <button className={action === 'approve' ? 'active' : ''} onClick={() => setAction('approve')}>Approve</button>
        <button className={action === 'reject' ? 'active' : ''} onClick={() => setAction('reject')}>Reject</button>
      </div>
      <label className="field"><span>Decision reason {action === 'reject' && '(required)'}</span><textarea value={reason} onChange={(event) => setReason(event.target.value)} maxLength={500} rows={4} /></label>
      {concurrentUpdate && (
        <div className="state-panel state-panel--error" role="alert">
          <strong>This request was changed by another specialist.</strong>
          <p>Reload the latest decision before taking another action.</p>
          <button className="button button--secondary" onClick={reloadCurrentDecision}>Reload current request</button>
        </div>
      )}
      {mutation.error && !concurrentUpdate && <ErrorState error={mutation.error} retry={() => mutation.mutate()} />}
      <button className="button" disabled={mutation.isPending || invalidRejection} onClick={() => mutation.mutate()}>{mutation.isPending ? 'Saving decision…' : `Confirm ${action}`}</button>
    </section>
  )
}
