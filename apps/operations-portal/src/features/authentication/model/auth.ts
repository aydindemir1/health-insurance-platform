export const applicationRoles = [
  'HOSPITAL_USER',
  'INSURANCE_SPECIALIST',
  'CLAIM_APPROVER',
  'SYSTEM_ADMIN',
] as const

export type ApplicationRole = typeof applicationRoles[number]

export interface AuthSession {
  authenticated: boolean
  displayName: string
  roles: ReadonlySet<ApplicationRole>
}
