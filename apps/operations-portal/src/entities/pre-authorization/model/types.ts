export type PreAuthorizationStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export interface PreAuthorization {
  id: string
  memberId: string
  providerId: string
  policyNumber: string
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
  diagnosisCode: string
  requestedAmount: number
  currency: string
}
