import { Navigate, Route, Routes } from 'react-router'
import { RequireAuthentication } from '@/features/authentication/ui/RequireAuthentication'
import { AppShell } from '@/widgets/app-shell/AppShell'
import { LoginPage } from '@/pages/login/LoginPage'
import { DashboardPage } from '@/pages/dashboard/DashboardPage'
import { PreAuthorizationsPage } from '@/pages/pre-authorizations/PreAuthorizationsPage'
import { PreAuthorizationDetailPage } from '@/pages/pre-authorizations/PreAuthorizationDetailPage'
import { NewPreAuthorizationPage } from '@/pages/pre-authorizations/NewPreAuthorizationPage'
import { NotFoundPage } from '@/pages/not-found/NotFoundPage'

export function AppRouter() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<RequireAuthentication />}>
        <Route element={<AppShell />}>
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="dashboard" element={<DashboardPage />} />
          <Route path="pre-authorizations" element={<PreAuthorizationsPage />} />
          <Route path="pre-authorizations/new" element={<NewPreAuthorizationPage />} />
          <Route path="pre-authorizations/:id" element={<PreAuthorizationDetailPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes>
  )
}
