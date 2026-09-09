export type AuditService = 'authorization' | 'policy' | 'claims-billing'

export interface AuditRecord {
  auditId: string
  aggregateType: string
  aggregateId: string
  action: string
  actorSubject: string
  actorRoles: string[]
  providerId?: string
  correlationId: string
  occurredAt: string
  reasonCode: string
  changes: { fromStatus?: string | null; toStatus: string }
  retentionClass: string
}

export interface AuditPageResult {
  content: AuditRecord[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface AuditSearchCriteria {
  service: AuditService
  aggregateId?: string
  action?: string
  page: number
  size: number
}
