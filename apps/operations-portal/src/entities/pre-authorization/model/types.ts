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
