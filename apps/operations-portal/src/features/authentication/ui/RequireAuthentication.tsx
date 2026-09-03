import { Navigate, Outlet, useLocation } from 'react-router'
import { useAuth } from '@/features/authentication/model/useAuth'
import { ErrorState, LoadingState } from '@/shared/ui/AsyncState'

export function RequireAuthentication() {
  const auth = useAuth()
  const location = useLocation()
  if (!auth.initialized) return <LoadingState label="Establishing secure session…" />
  if (auth.error) return <ErrorState error={auth.error} retry={() => window.location.reload()} />
  if (!auth.authenticated) return <Navigate to="/login" replace state={{ from: location }} />
  return <Outlet />
}
