import { Navigate, Route, Routes } from 'react-router'
import { lazy, Suspense } from 'react'
import { RequireAuthentication, RequireRole } from '@/features/authentication'
import { AppShell } from '@/widgets/app-shell/AppShell'
import { LoadingState } from '@/shared/ui/AsyncState'

const LoginPage = lazy(() => import('@/pages/login/LoginPage').then(module => ({ default: module.LoginPage })))
const DashboardPage = lazy(() => import('@/pages/dashboard/DashboardPage').then(module => ({ default: module.DashboardPage })))
const PreAuthorizationsPage = lazy(() => import('@/pages/pre-authorizations/PreAuthorizationsPage').then(module => ({ default: module.PreAuthorizationsPage })))
const PreAuthorizationDetailPage = lazy(() => import('@/pages/pre-authorizations/PreAuthorizationDetailPage').then(module => ({ default: module.PreAuthorizationDetailPage })))
const NewPreAuthorizationPage = lazy(() => import('@/pages/pre-authorizations/NewPreAuthorizationPage').then(module => ({ default: module.NewPreAuthorizationPage })))
const NotFoundPage = lazy(() => import('@/pages/not-found/NotFoundPage').then(module => ({ default: module.NotFoundPage })))
const SearchPage = lazy(() => import('@/pages/search/SearchPage').then(module => ({ default: module.SearchPage })))
const AuditPage = lazy(() => import('@/pages/audit/AuditPage').then(module => ({ default: module.AuditPage })))
const ForbiddenPage = lazy(() => import('@/pages/forbidden/ForbiddenPage').then(module => ({ default: module.ForbiddenPage })))

export function AppRouter() {
  return (
    <Suspense fallback={<LoadingState label="Loading page…" />}><Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<RequireAuthentication />}>
        <Route element={<AppShell />}>
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="dashboard" element={<DashboardPage />} />
          <Route path="forbidden" element={<ForbiddenPage />} />
          <Route element={<RequireRole roles={['HOSPITAL_USER', 'INSURANCE_SPECIALIST', 'SYSTEM_ADMIN']} />}>
            <Route path="pre-authorizations" element={<PreAuthorizationsPage />} />
            <Route path="pre-authorizations/:id" element={<PreAuthorizationDetailPage />} />
          </Route>
          <Route element={<RequireRole roles={['HOSPITAL_USER']} />}>
            <Route path="pre-authorizations/new" element={<NewPreAuthorizationPage />} />
          </Route>
          <Route element={<RequireRole roles={['HOSPITAL_USER', 'INSURANCE_SPECIALIST', 'CLAIM_APPROVER', 'SYSTEM_ADMIN']} />}>
            <Route path="search" element={<SearchPage />} />
          </Route>
          <Route element={<RequireRole roles={['SYSTEM_ADMIN']} />}>
            <Route path="audit" element={<AuditPage />} />
          </Route>
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Route>
    </Routes></Suspense>
  )
}
