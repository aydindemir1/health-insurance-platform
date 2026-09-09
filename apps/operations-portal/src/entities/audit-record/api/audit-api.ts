import { apiRequest } from '@/shared/api/http-client'
import type { AuditPageResult, AuditSearchCriteria, AuditService } from '@/entities/audit-record/model/types'

const servicePaths: Record<AuditService, string> = {
  authorization: '/audit-records',
  policy: '/policies/audit-records',
  'claims-billing': '/claims/audit-records',
}

export const auditApi = {
  search: (criteria: AuditSearchCriteria) => {
    const parameters = new URLSearchParams({
      page: criteria.page.toString(),
      size: criteria.size.toString(),
    })
    if (criteria.aggregateId) parameters.set('aggregateId', criteria.aggregateId)
    if (criteria.action) parameters.set('action', criteria.action)
    return apiRequest<AuditPageResult>(`${servicePaths[criteria.service]}?${parameters}`)
  },
}
