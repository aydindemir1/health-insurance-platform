import { apiRequest } from '@/shared/api/http-client'
import { environment } from '@/shared/config/environment'
import type { SearchCriteria, SearchPageResult } from '@/entities/search-record/model/types'

export const searchApi = {
  search: (criteria: SearchCriteria) => {
    const parameters = new URLSearchParams({
      page: criteria.page.toString(),
      size: criteria.size.toString(),
    })
    if (criteria.query) parameters.set('q', criteria.query)
    if (criteria.type) parameters.set('type', criteria.type)
    if (criteria.status) parameters.set('status', criteria.status)
    if (criteria.providerId) parameters.set('providerId', criteria.providerId)
    return apiRequest<SearchPageResult>(`/search?${parameters}`, {}, environment.searchApiBaseUrl)
  },
}
