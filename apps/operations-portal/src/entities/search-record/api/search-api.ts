import { apiRequest } from '@/shared/api/http-client'
import { environment } from '@/shared/config/environment'
import { searchPageResultSchema, type SearchCriteria, type SearchPageResult } from '../model/types'

export const searchApi = {
  search: (criteria: SearchCriteria, signal?: AbortSignal) => {
    const parameters = new URLSearchParams({
      page: criteria.page.toString(),
      size: criteria.size.toString(),
    })
    if (criteria.query) parameters.set('q', criteria.query)
    if (criteria.type) parameters.set('type', criteria.type)
    if (criteria.status) parameters.set('status', criteria.status)
    if (criteria.providerId) parameters.set('providerId', criteria.providerId)
    return apiRequest<SearchPageResult>(`/search?${parameters}`, {
      baseUrl: environment.searchApiBaseUrl,
      responseSchema: searchPageResultSchema,
      ...(signal ? { signal } : {}),
    })
  },
}
