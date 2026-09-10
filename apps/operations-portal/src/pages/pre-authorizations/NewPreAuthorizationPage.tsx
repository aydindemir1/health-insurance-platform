import { Navigate } from 'react-router'
import { useAuth } from '@/features/authentication'
import { SubmitPreAuthorizationForm } from '@/features/submit-pre-authorization'
import { PageHeader } from '@/shared/ui/PageHeader'

export function NewPreAuthorizationPage() {
  const auth = useAuth()
  if (!auth.hasRole('HOSPITAL_USER')) return <Navigate to="/pre-authorizations" replace />
  return <><PageHeader eyebrow="Hospital workflow" title="New pre-authorization" /><SubmitPreAuthorizationForm /></>
}
