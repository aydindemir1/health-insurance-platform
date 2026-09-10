import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { RequireRole } from './RequireRole'

let allowed = false

vi.mock('@/features/authentication/model/useAuth', () => ({
  useAuth: () => ({ hasRole: () => allowed }),
}))

function renderRoute() {
  return render(
    <MemoryRouter initialEntries={['/protected']}>
      <Routes>
        <Route path="/forbidden" element={<p>Access denied</p>} />
        <Route element={<RequireRole roles={['SYSTEM_ADMIN']} />}>
          <Route path="/protected" element={<p>Protected content</p>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

describe('RequireRole', () => {
  it('renders the protected route for an authorized user', () => {
    allowed = true
    renderRoute()
    expect(screen.getByText('Protected content')).toBeInTheDocument()
  })

  it('redirects an unauthorized user to the forbidden route', () => {
    allowed = false
    renderRoute()
    expect(screen.getByText('Access denied')).toBeInTheDocument()
  })
})
