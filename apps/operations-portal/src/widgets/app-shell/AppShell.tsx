import { NavLink, Outlet } from 'react-router'
import { useAuth } from '@/features/authentication'

export function AppShell() {
  const auth = useAuth()
  return (
    <div className="app-layout">
      <aside className="sidebar">
        <div className="brand"><span className="brand-mark">H+</span><span>Health Insurance<br /><small>Operations Portal</small></span></div>
        <nav aria-label="Primary navigation">
          <NavLink to="/dashboard">Dashboard</NavLink>
          {auth.hasRole('HOSPITAL_USER', 'INSURANCE_SPECIALIST', 'SYSTEM_ADMIN') && <NavLink to="/pre-authorizations">Pre-authorizations</NavLink>}
          {auth.hasRole('HOSPITAL_USER', 'INSURANCE_SPECIALIST', 'CLAIM_APPROVER', 'SYSTEM_ADMIN') && <NavLink to="/search">Healthcare search</NavLink>}
          {auth.hasRole('SYSTEM_ADMIN') && <NavLink to="/audit">Audit trail</NavLink>}
        </nav>
        <div className="sidebar-user">
          <span className="avatar">{auth.displayName.slice(0, 1).toUpperCase()}</span>
          <div><strong>{auth.displayName}</strong><small>{[...auth.roles][0]?.replaceAll('_', ' ') ?? 'Authenticated user'}</small></div>
          <button className="text-button" onClick={() => void auth.logout()}>Sign out</button>
        </div>
      </aside>
      <main className="main-content"><Outlet /></main>
    </div>
  )
}
