export type SearchRecordType = 'PRE_AUTHORIZATION' | 'CLAIM'

export interface SearchRecord {
  id: string
  type: SearchRecordType
  sourceId: string
  preAuthorizationId?: string
  memberId: string
  providerId: string
  policyNumber: string
  serviceCode: string
  status: string
  invoiceStatus?: string
  invoiceNumber?: string
  amount: number
  approvedAmount?: number
  paidAmount?: number
  currency: string
  reason?: string
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
