import { z } from 'zod'

export type PreAuthorizationStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface PreAuthorization {
  id: string
  memberId: string
  providerId: string
  policyNumber: string
  serviceCode: string
  diagnosisCode: string
  requestedAmount: number
  currency: string
  status: PreAuthorizationStatus
  decisionReason: string | null
  createdAt: string
  decidedAt: string | null
}

export interface SubmitPreAuthorization {
  memberId: string
  policyNumber: string
  serviceCode: string
  diagnosisCode: string
  requestedAmount: number
  currency: string
}

export type PreAuthorizationSortField = 'createdAt' | 'requestedAmount' | 'status'
export type SortDirection = 'asc' | 'desc'

export interface PreAuthorizationSearch {
  status?: PreAuthorizationStatus
  memberId?: string
  policyNumber?: string
  page: number
  size: number
  sortBy: PreAuthorizationSortField
  direction: SortDirection
}

export interface PageResult<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export const preAuthorizationSchema = z.object({
  id: z.uuid(),
  memberId: z.uuid(),
  providerId: z.uuid(),
  policyNumber: z.string(),
  serviceCode: z.string(),
  diagnosisCode: z.string(),
  requestedAmount: z.number().finite(),
  currency: z.string().regex(/^[A-Z]{3}$/),
  status: z.enum(['PENDING', 'APPROVED', 'REJECTED']),
  decisionReason: z.string().nullable(),
  createdAt: z.string().min(1),
  decidedAt: z.string().nullable(),
})

export const preAuthorizationPageSchema = z.object({
  content: z.array(preAuthorizationSchema),
  page: z.number().int().nonnegative(),
  size: z.number().int().positive(),
  totalElements: z.number().int().nonnegative(),
  totalPages: z.number().int().nonnegative(),
  first: z.boolean(),
  last: z.boolean(),
})
