import { apiRequest } from '@/shared/api/http-client'
import {
  preAuthorizationPageSchema,
  preAuthorizationSchema,
  type PageResult,
  type PreAuthorization,
  type PreAuthorizationSearch,
  type SubmitPreAuthorization,
} from '../model/types'

export const preAuthorizationApi = {
  search: (search: PreAuthorizationSearch, signal?: AbortSignal) => {
    const parameters = new URLSearchParams({
      page: search.page.toString(),
      size: search.size.toString(),
      sortBy: search.sortBy,
      direction: search.direction,
    })
    if (search.status) parameters.set('status', search.status)
    if (search.memberId) parameters.set('memberId', search.memberId)
    if (search.policyNumber) parameters.set('policyNumber', search.policyNumber)
    return apiRequest<PageResult<PreAuthorization>>(`/pre-authorizations?${parameters}`, {
      responseSchema: preAuthorizationPageSchema,
      ...(signal ? { signal } : {}),
    })
  },
  getById: (id: string, signal?: AbortSignal) => apiRequest<PreAuthorization>(`/pre-authorizations/${id}`, {
    responseSchema: preAuthorizationSchema,
    ...(signal ? { signal } : {}),
  }),
  submit: (request: SubmitPreAuthorization) => apiRequest<PreAuthorization>('/pre-authorizations', {
    method: 'POST',
    body: JSON.stringify(request),
    responseSchema: preAuthorizationSchema,
  }),
  approve: (id: string, reason?: string) => apiRequest<PreAuthorization>(
    `/pre-authorizations/${id}/approval`, {
      method: 'POST',
      body: JSON.stringify({ reason: reason || null }),
      responseSchema: preAuthorizationSchema,
    }),
  reject: (id: string, reason: string) => apiRequest<PreAuthorization>(
    `/pre-authorizations/${id}/rejection`, {
      method: 'POST',
      body: JSON.stringify({ reason }),
      responseSchema: preAuthorizationSchema,
    }),
}
