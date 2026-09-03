import { Navigate } from 'react-router'
import { useAuth } from '@/features/authentication/model/useAuth'
import { ErrorState, LoadingState } from '@/shared/ui/AsyncState'

export function LoginPage() {
  const auth = useAuth()
  if (!auth.initialized) return <LoadingState label="Preparing secure sign-in…" />
  if (auth.error) return <ErrorState error={auth.error} retry={() => window.location.reload()} />
  if (auth.authenticated) return <Navigate to="/dashboard" replace />
  return (
    <main className="login-page">
      <section className="login-panel">
        <span className="brand-mark brand-mark--large">H+</span>
        <span className="eyebrow">Health Insurance Platform</span>
        <h1>Operations, without the noise.</h1>
        <p>Review pre-authorizations and make traceable decisions through one secure workspace.</p>
        <button className="button" onClick={() => void auth.login()}>Sign in with Keycloak</button>
        <small>OAuth 2.0 / OpenID Connect · PKCE S256</small>
      </section>
      <section className="login-context" aria-label="Platform capabilities">
        <div><strong>Provider ownership</strong><span>Every hospital request is bound to the verified identity.</span></div>
        <div><strong>Decision safety</strong><span>Concurrent decisions are detected before data can be overwritten.</span></div>
        <div><strong>Audit ready</strong><span>Business transitions remain explicit and explainable.</span></div>
      </section>
    </main>
  )
}
