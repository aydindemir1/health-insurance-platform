import { Navigate, Outlet } from 'react-router'
import type { ApplicationRole } from '../model/auth'
import { useAuth } from '../model/useAuth'

export function RequireRole({ roles }: { roles: readonly ApplicationRole[] }) {
  const auth = useAuth()
  return auth.hasRole(...roles) ? <Outlet /> : <Navigate to="/forbidden" replace />
}
