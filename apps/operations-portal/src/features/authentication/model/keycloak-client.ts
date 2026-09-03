import Keycloak from 'keycloak-js'
import { environment } from '@/shared/config/environment'
import { applicationRoles, type ApplicationRole, type AuthSession } from './auth'

const keycloak = new Keycloak(environment.keycloak)
let initialization: Promise<boolean> | undefined

export function initializeKeycloak() {
  initialization ??= keycloak.init({
    onLoad: 'check-sso',
    pkceMethod: 'S256',
    silentCheckSsoRedirectUri: `${window.location.origin}/silent-check-sso.html`,
  })
  return initialization
}

export function currentSession(): AuthSession {
  const realmRoles = new Set(keycloak.realmAccess?.roles ?? [])
  const roles = new Set<ApplicationRole>(
    applicationRoles.filter((role) => realmRoles.has(role)),
  )
  return {
    authenticated: Boolean(keycloak.authenticated),
    displayName: keycloak.tokenParsed?.name
      ?? keycloak.tokenParsed?.preferred_username
      ?? 'Operations user',
    roles,
  }
}

export async function accessToken() {
  if (!keycloak.authenticated) return undefined
  await keycloak.updateToken(30)
  return keycloak.token
}

export const keycloakActions = {
  login: () => keycloak.login(),
  logout: () => keycloak.logout({ redirectUri: window.location.origin }),
}

export function subscribeToKeycloak(listener: () => void) {
  keycloak.onAuthSuccess = listener
  keycloak.onAuthLogout = listener
  keycloak.onAuthRefreshSuccess = listener
  keycloak.onTokenExpired = () => void keycloak.updateToken(30).then(listener)
  return () => {
    keycloak.onAuthSuccess = undefined
    keycloak.onAuthLogout = undefined
    keycloak.onAuthRefreshSuccess = undefined
    keycloak.onTokenExpired = undefined
  }
}
