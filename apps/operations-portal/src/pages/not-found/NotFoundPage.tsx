import { Link } from 'react-router'
import { EmptyState } from '@/shared/ui/AsyncState'

export function NotFoundPage() {
  return <EmptyState title="Page not found">Return to the <Link to="/dashboard">operations dashboard</Link>.</EmptyState>
}
