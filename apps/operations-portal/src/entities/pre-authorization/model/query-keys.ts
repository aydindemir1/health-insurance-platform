import type { PreAuthorizationSearch } from './types'

export const preAuthorizationKeys = {
  all: ['pre-authorizations'] as const,
  lists: ['pre-authorizations', 'list'] as const,
  list: (criteria: PreAuthorizationSearch) => ['pre-authorizations', 'list', criteria] as const,
  detail: (id: string) => ['pre-authorizations', 'detail', id] as const,
}
