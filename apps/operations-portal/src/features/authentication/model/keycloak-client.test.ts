import { beforeEach, describe, expect, it, vi } from 'vitest'

const keycloak = vi.hoisted(() => ({
  init: vi.fn(),
  updateToken: vi.fn(),
  login: vi.fn().mockResolvedValue(undefined),
  logout: vi.fn().mockResolvedValue(undefined),
  clearToken: vi.fn(),
  authenticated: true,
  token: 'access-token',
  tokenParsed: { name: 'Aydın Demir' },
  realmAccess: { roles: ['INSURANCE_SPECIALIST'] },
  onAuthSuccess: undefined as (() => void) | undefined,
  onAuthLogout: undefined as (() => void) | undefined,
  onAuthRefreshSuccess: undefined as (() => void) | undefined,
  onTokenExpired: undefined as (() => void) | undefined,
}))

vi.mock('keycloak-js', () => ({
  default: function KeycloakMock() {
    return keycloak
  },
}))

import {
  accessToken,
  currentSession,
  initializeKeycloak,
  subscribeToKeycloak,
} from './keycloak-client'

describe('Keycloak client lifecycle', () => {
  beforeEach(() => {
    keycloak.init.mockResolvedValue(true)
    keycloak.updateToken.mockResolvedValue(true)
    keycloak.clearToken.mockClear()
  })

  it('uses PKCE, maps roles, refreshes tokens and clears an expired session on refresh failure', async () => {
    await initializeKeycloak()
    expect(keycloak.init).toHaveBeenCalledWith(expect.objectContaining({
      onLoad: 'check-sso',
      pkceMethod: 'S256',
    }))
    expect(currentSession()).toMatchObject({
      authenticated: true,
      displayName: 'Aydın Demir',
    })
    expect(currentSession().roles.has('INSURANCE_SPECIALIST')).toBe(true)
    await expect(accessToken()).resolves.toBe('access-token')
    expect(keycloak.updateToken).toHaveBeenCalledWith(30)

    const listener = vi.fn()
    const unsubscribe = subscribeToKeycloak(listener)
    keycloak.updateToken.mockRejectedValueOnce(new Error('refresh failed'))
    keycloak.onTokenExpired?.()

    await vi.waitFor(() => expect(keycloak.clearToken).toHaveBeenCalledOnce())
    expect(listener).toHaveBeenCalledOnce()
    unsubscribe()
  })
})
