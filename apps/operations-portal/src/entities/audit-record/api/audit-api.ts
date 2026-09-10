import { apiRequest } from '@/shared/api/http-client'
import { auditPageResultSchema, type AuditPageResult, type AuditSearchCriteria, type AuditService } from '../model/types'

const servicePaths: Record<AuditService, string> = {
  authorization: '/audit-records',
  policy: '/policies/audit-records',
  'claims-billing': '/claims/audit-records',
}

export const auditApi = {
  search: (criteria: AuditSearchCriteria, signal?: AbortSignal) => {
    const parameters = new URLSearchParams({
      page: criteria.page.toString(),
      size: criteria.size.toString(),
    })
    if (criteria.aggregateId) parameters.set('aggregateId', criteria.aggregateId)
    if (criteria.action) parameters.set('action', criteria.action)
    return apiRequest<AuditPageResult>(`${servicePaths[criteria.service]}?${parameters}`, {
      responseSchema: auditPageResultSchema,
      ...(signal ? { signal } : {}),
    })
  },
}
