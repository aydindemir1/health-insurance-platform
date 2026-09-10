export type SearchRecordType = 'PRE_AUTHORIZATION' | 'CLAIM'

export interface SearchRecord {
  id: string
  type: SearchRecordType
  sourceId: string
  preAuthorizationId?: string | null | undefined
  memberId: string
  providerId: string
  policyNumber: string
  serviceCode: string
  status: string
  invoiceStatus?: string | null | undefined
  invoiceNumber?: string | null | undefined
  amount: number
  approvedAmount?: number | null | undefined
  paidAmount?: number | null | undefined
  currency: string
  reason?: string | null | undefined
  occurredAt: string
}

export interface SearchPageResult {
  content: SearchRecord[]
  page: number
  size: number
  totalElements: number
}

export interface SearchCriteria {
  query?: string
  type?: SearchRecordType
  status?: string
  providerId?: string
  page: number
  size: number
}

export const searchPageResultSchema = z.object({
  content: z.array(z.object({
    id: z.string().min(1),
    type: z.enum(['PRE_AUTHORIZATION', 'CLAIM']),
    sourceId: z.uuid(),
    preAuthorizationId: z.uuid().nullish(),
    memberId: z.uuid(),
    providerId: z.uuid(),
    policyNumber: z.string(),
    serviceCode: z.string(),
    status: z.string(),
    invoiceStatus: z.string().nullish(),
    invoiceNumber: z.string().nullish(),
    amount: z.number().finite(),
    approvedAmount: z.number().finite().nullish(),
    paidAmount: z.number().finite().nullish(),
    currency: z.string().regex(/^[A-Z]{3}$/),
    reason: z.string().nullish(),
    occurredAt: z.string().min(1),
  })),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalElements: z.number().int().nonnegative(),
})
import { z } from 'zod'
