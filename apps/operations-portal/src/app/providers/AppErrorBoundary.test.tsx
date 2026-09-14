import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { AppErrorBoundary } from './AppErrorBoundary'

function BrokenPage(): never {
  throw new Error('sensitive-render-detail')
}

describe('AppErrorBoundary', () => {
  it('renders a safe recovery screen and logs only minimized metadata', () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined)

    render(<AppErrorBoundary><BrokenPage /></AppErrorBoundary>)

    expect(screen.getByRole('alert')).toHaveTextContent('The portal could not render this page.')
    expect(screen.getByRole('alert')).not.toHaveTextContent('sensitive-render-detail')
    expect(consoleError).toHaveBeenLastCalledWith('Unhandled portal render error', {
      errorName: 'Error',
      componentStackAvailable: true,
    })
  })
})
