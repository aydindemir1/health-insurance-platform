import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router'
import { z } from 'zod'
import { preAuthorizationApi } from '@/entities/pre-authorization/api/pre-authorization-api'
import { ErrorState } from '@/shared/ui/AsyncState'

const schema = z.object({
  memberId: z.uuid('Enter a valid member UUID.'),
  policyNumber: z.string().trim().min(1, 'Policy number is required.').max(50),
  diagnosisCode: z.string().trim().min(1, 'Diagnosis code is required.').max(20),
  requestedAmount: z.string().regex(/^\d+(\.\d{1,2})?$/, 'Enter a positive amount with up to two decimals.').refine((value) => Number(value) > 0, 'Amount must be positive.'),
  currency: z.string().trim().regex(/^[A-Z]{3}$/, 'Use a three-letter ISO currency code.'),
})

type FormValues = z.infer<typeof schema>

export function SubmitPreAuthorizationForm() {
  const navigate = useNavigate()
  const form = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: { memberId: '', policyNumber: '', diagnosisCode: '', requestedAmount: '', currency: 'TRY' } })
  const mutation = useMutation({
    mutationFn: (values: FormValues) => preAuthorizationApi.submit({ ...values, requestedAmount: Number(values.requestedAmount) }),
    onSuccess: (created) => navigate(`/pre-authorizations/${created.id}`),
  })
  return (
    <form className="content-card form-grid" onSubmit={form.handleSubmit((values) => mutation.mutate(values))} noValidate>
      <Field label="Member ID" error={form.formState.errors.memberId?.message}><input {...form.register('memberId')} placeholder="Member UUID" /></Field>
      <Field label="Policy number" error={form.formState.errors.policyNumber?.message}><input {...form.register('policyNumber')} placeholder="POL-2026-001" /></Field>
      <Field label="Diagnosis code" error={form.formState.errors.diagnosisCode?.message}><input {...form.register('diagnosisCode')} placeholder="J18.9" /></Field>
      <Field label="Requested amount" error={form.formState.errors.requestedAmount?.message}><input {...form.register('requestedAmount')} inputMode="decimal" placeholder="1250.00" /></Field>
      <Field label="Currency" error={form.formState.errors.currency?.message}><input {...form.register('currency')} maxLength={3} /></Field>
      {mutation.error && <div className="form-span"><ErrorState error={mutation.error} /></div>}
      <div className="form-actions form-span"><button className="button" disabled={mutation.isPending}>{mutation.isPending ? 'Submitting…' : 'Submit pre-authorization'}</button><button type="button" className="button button--secondary" onClick={() => navigate(-1)}>Cancel</button></div>
    </form>
  )
}

function Field({ label, error, children }: { label: string; error?: string; children: React.ReactNode }) {
  return <label className="field"><span>{label}</span>{children}{error && <small className="field-error">{error}</small>}</label>
}
