import { Component, type ErrorInfo, type ReactNode } from 'react'

interface State {
  error?: Error
}

export class AppErrorBoundary extends Component<{ children: ReactNode }, State> {
  state: State = {}

  static getDerivedStateFromError(error: Error): State {
    return { error }
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('Unhandled portal error', error, errorInfo)
  }

  render() {
    if (this.state.error) {
      return (
        <main className="fatal-error" role="alert">
          <span className="eyebrow">Unexpected error</span>
          <h1>The portal could not render this page.</h1>
          <p>No healthcare data was included in this message. Reload the application to start a clean session.</p>
          <button className="button" onClick={() => window.location.reload()}>Reload portal</button>
        </main>
      )
    }
    return this.props.children
  }
}
