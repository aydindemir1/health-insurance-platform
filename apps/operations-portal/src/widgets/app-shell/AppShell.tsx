import { NavLink, Outlet } from 'react-router'
import { useAuth } from '@/features/authentication/model/useAuth'

export function AppShell() {
  const auth = useAuth()
  return (
    <div className="app-layout">
      <aside className="sidebar">
        <div className="brand"><span className="brand-mark">H+</span><span>Health Insurance<br /><small>Operations Portal</small></span></div>
        <nav aria-label="Primary navigation">
          <NavLink to="/dashboard">Dashboard</NavLink>
          <NavLink to="/pre-authorizations">Pre-authorizations</NavLink>
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
