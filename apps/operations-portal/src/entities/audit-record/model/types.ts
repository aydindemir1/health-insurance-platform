export type AuditService = 'authorization' | 'policy' | 'claims-billing'

export interface AuditRecord {
  auditId: string
  aggregateType: string
  aggregateId: string
  action: string
  actorSubject: string
  actorRoles: string[]
  providerId?: string | null | undefined
  correlationId: string
  occurredAt: string
  reasonCode: string
  changes: { fromStatus?: string | null | undefined; toStatus: string }
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

export const auditPageResultSchema = z.object({
  content: z.array(z.object({
    auditId: z.uuid(),
    aggregateType: z.string(),
    aggregateId: z.uuid(),
    action: z.string(),
    actorSubject: z.string(),
    actorRoles: z.array(z.string()),
    providerId: z.uuid().nullish(),
    correlationId: z.string(),
    occurredAt: z.string().min(1),
    reasonCode: z.string(),
    changes: z.object({
      fromStatus: z.string().nullable().optional(),
      toStatus: z.string(),
    }),
    retentionClass: z.string(),
  })),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalElements: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative(),
  first: z.boolean(),
  last: z.boolean(),
})
import { z } from 'zod'
