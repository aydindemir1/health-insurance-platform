import { createContext } from 'react'
import type { ApplicationRole, AuthSession } from './auth'

export interface AuthContextValue extends AuthSession {
  initialized: boolean
  error?: Error
  login: () => Promise<void>
  logout: () => Promise<void>
  hasRole: (...roles: ApplicationRole[]) => boolean
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined)
