import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { setAccessTokenProvider } from '@/shared/api/http-client'
import type { AuthSession } from './auth'
import { AuthContext, type AuthContextValue } from './auth-context'
import {
  accessToken,
  currentSession,
  initializeKeycloak,
  keycloakActions,
  subscribeToKeycloak,
} from './keycloak-client'

const anonymousSession: AuthSession = {
  authenticated: false,
  displayName: '',
  roles: new Set(),
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthSession>(anonymousSession)
  const [initialized, setInitialized] = useState(false)
  const [error, setError] = useState<Error>()

  useEffect(() => {
    let active = true
    const sync = () => active && setSession(currentSession())
    const unsubscribe = subscribeToKeycloak(sync)
    setAccessTokenProvider(accessToken)
    void initializeKeycloak()
      .then(() => sync())
      .catch((reason: unknown) => {
        if (active) setError(reason instanceof Error ? reason : new Error('Authentication failed'))
      })
      .finally(() => active && setInitialized(true))
    return () => {
      active = false
      unsubscribe()
    }
  }, [])

  const value = useMemo<AuthContextValue>(() => ({
    ...session,
    initialized,
    error,
    login: keycloakActions.login,
    logout: keycloakActions.logout,
    hasRole: (...roles) => roles.some((role) => session.roles.has(role)),
  }), [session, initialized, error])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
