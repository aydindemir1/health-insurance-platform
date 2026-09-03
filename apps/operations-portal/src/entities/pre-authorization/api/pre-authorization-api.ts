import { apiRequest } from '@/shared/api/http-client'
import type {
  PageResult,
  PreAuthorization,
  PreAuthorizationSearch,
  SubmitPreAuthorization,
} from '@/entities/pre-authorization/model/types'

export const preAuthorizationApi = {
  search: (search: PreAuthorizationSearch) => {
    const parameters = new URLSearchParams({
      page: search.page.toString(),
      size: search.size.toString(),
      sortBy: search.sortBy,
      direction: search.direction,
    })
    if (search.status) parameters.set('status', search.status)
    if (search.memberId) parameters.set('memberId', search.memberId)
    if (search.policyNumber) parameters.set('policyNumber', search.policyNumber)
    return apiRequest<PageResult<PreAuthorization>>(`/pre-authorizations?${parameters}`)
  },
  getById: (id: string) => apiRequest<PreAuthorization>(`/pre-authorizations/${id}`),
  submit: (request: SubmitPreAuthorization) => apiRequest<PreAuthorization>('/pre-authorizations', {
    method: 'POST',
    body: JSON.stringify(request),
  }),
  approve: (id: string, reason?: string) => apiRequest<PreAuthorization>(
    `/pre-authorizations/${id}/approval`, {
      method: 'POST',
      body: JSON.stringify({ reason: reason || null }),
    }),
  reject: (id: string, reason: string) => apiRequest<PreAuthorization>(
    `/pre-authorizations/${id}/rejection`, {
      method: 'POST',
      body: JSON.stringify({ reason }),
    }),
}
