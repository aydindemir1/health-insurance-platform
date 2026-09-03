import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { SubmitPreAuthorizationForm } from './SubmitPreAuthorizationForm'

describe('SubmitPreAuthorizationForm', () => {
  it('shows validation errors before sending an incomplete request', async () => {
    const user = userEvent.setup()
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter><SubmitPreAuthorizationForm /></MemoryRouter>
      </QueryClientProvider>,
    )

    await user.click(screen.getByRole('button', { name: 'Submit pre-authorization' }))

    expect(await screen.findByText('Enter a valid member UUID.')).toBeInTheDocument()
    expect(screen.getByText('Policy number is required.')).toBeInTheDocument()
    expect(screen.getByText('Diagnosis code is required.')).toBeInTheDocument()
    expect(screen.getByText('Enter a positive amount with up to two decimals.')).toBeInTheDocument()
  })
})
