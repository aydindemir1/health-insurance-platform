import { apiRequest } from '@/shared/api/http-client'
import type { PreAuthorization, SubmitPreAuthorization } from '@/entities/pre-authorization/model/types'

export const preAuthorizationApi = {
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
