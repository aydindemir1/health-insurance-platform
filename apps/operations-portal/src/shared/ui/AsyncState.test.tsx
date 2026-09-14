import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/shared/api/http-client'
import { ErrorState, LoadingState } from './AsyncState'

describe('asynchronous states', () => {
  it('renders a status while data is loading', () => {
    render(<LoadingState label="Loading governed data…" />)
    expect(screen.getByRole('status')).toHaveTextContent('Loading governed data…')
  })

  it('shows safe Problem Details context and retries on demand', async () => {
    const retry = vi.fn()
    render(<ErrorState
      error={new ApiError(409, { detail: 'Reload the request.', correlationId: 'request-123' })}
      retry={retry}
    />)

    expect(screen.getByRole('alert')).toHaveTextContent('Reload the request.')
    expect(screen.getByRole('alert')).toHaveTextContent('request-123')
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(retry).toHaveBeenCalledOnce()
  })
})
