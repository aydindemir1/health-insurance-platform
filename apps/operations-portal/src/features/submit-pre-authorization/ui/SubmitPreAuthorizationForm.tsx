import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router'
import { z } from 'zod'
import { preAuthorizationApi } from '@/entities/pre-authorization'
import { ErrorState } from '@/shared/ui/AsyncState'

const schema = z.object({
  memberId: z.uuid('Enter a valid member UUID.'),
  policyNumber: z.string().trim().min(1, 'Policy number is required.').max(50),
  serviceCode: z.string().trim().min(1, 'Service code is required.').max(40),
  diagnosisCode: z.string().trim().min(1, 'Diagnosis code is required.').max(20),
  requestedAmount: z.string().regex(/^\d+(\.\d{1,2})?$/, 'Enter a positive amount with up to two decimals.').refine((value) => Number(value) > 0, 'Amount must be positive.'),
  currency: z.string().trim().regex(/^[A-Z]{3}$/, 'Use a three-letter ISO currency code.'),
})

type FormValues = z.infer<typeof schema>

export function SubmitPreAuthorizationForm() {
  const navigate = useNavigate()
  const form = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: { memberId: '', policyNumber: '', serviceCode: '', diagnosisCode: '', requestedAmount: '', currency: 'TRY' } })
  const mutation = useMutation({
    mutationFn: (values: FormValues) => preAuthorizationApi.submit({ ...values, requestedAmount: Number(values.requestedAmount) }),
    onSuccess: (created) => navigate(`/pre-authorizations/${created.id}`),
  })
  return (
    <form className="content-card form-grid" onSubmit={form.handleSubmit((values) => mutation.mutate(values))} noValidate>
      <Field label="Member ID" error={form.formState.errors.memberId?.message} errorId="memberId-error"><input {...form.register('memberId')} aria-invalid={Boolean(form.formState.errors.memberId)} aria-describedby={form.formState.errors.memberId ? 'memberId-error' : undefined} placeholder="Member UUID" /></Field>
      <Field label="Policy number" error={form.formState.errors.policyNumber?.message} errorId="policyNumber-error"><input {...form.register('policyNumber')} aria-invalid={Boolean(form.formState.errors.policyNumber)} aria-describedby={form.formState.errors.policyNumber ? 'policyNumber-error' : undefined} placeholder="POL-2026-001" /></Field>
      <Field label="Service code" error={form.formState.errors.serviceCode?.message} errorId="serviceCode-error"><input {...form.register('serviceCode')} aria-invalid={Boolean(form.formState.errors.serviceCode)} aria-describedby={form.formState.errors.serviceCode ? 'serviceCode-error' : undefined} placeholder="IMG-MRI" /></Field>
      <Field label="Diagnosis code" error={form.formState.errors.diagnosisCode?.message} errorId="diagnosisCode-error"><input {...form.register('diagnosisCode')} aria-invalid={Boolean(form.formState.errors.diagnosisCode)} aria-describedby={form.formState.errors.diagnosisCode ? 'diagnosisCode-error' : undefined} placeholder="J18.9" /></Field>
      <Field label="Requested amount" error={form.formState.errors.requestedAmount?.message} errorId="requestedAmount-error"><input {...form.register('requestedAmount')} aria-invalid={Boolean(form.formState.errors.requestedAmount)} aria-describedby={form.formState.errors.requestedAmount ? 'requestedAmount-error' : undefined} inputMode="decimal" placeholder="1250.00" /></Field>
      <Field label="Currency" error={form.formState.errors.currency?.message} errorId="currency-error"><input {...form.register('currency')} aria-invalid={Boolean(form.formState.errors.currency)} aria-describedby={form.formState.errors.currency ? 'currency-error' : undefined} maxLength={3} /></Field>
      {mutation.error && <div className="form-span"><ErrorState error={mutation.error} /></div>}
      <div className="form-actions form-span"><button className="button" disabled={mutation.isPending}>{mutation.isPending ? 'Submitting…' : 'Submit pre-authorization'}</button><button type="button" className="button button--secondary" onClick={() => navigate(-1)}>Cancel</button></div>
    </form>
  )
}

function Field({ label, error, errorId, children }: { label: string; error: string | undefined; errorId: string; children: React.ReactNode }) {
  return <label className="field"><span>{label}</span>{children}{error && <small id={errorId} className="field-error" role="alert">{error}</small>}</label>
}
