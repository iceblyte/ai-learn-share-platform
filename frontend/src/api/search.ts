import request from './request'
import type { ApiResponse, PageData, Resource, NlSearchResult, SearchParams } from '@/types'

export const searchApi = {
  search(params: SearchParams) {
    return request.get<ApiResponse<PageData<Resource>>>('/search', { params })
  },
  nlSearch(query: string) {
    return request.post<ApiResponse<NlSearchResult>>('/search/nl', { query })
  },
  getHistory() {
    return request.get<ApiResponse<string[]>>('/search/history')
  },
  clearHistory() {
    return request.delete('/search/history')
  },
  getHotSearches() {
    return request.get<ApiResponse<string[]>>('/search/hot')
  },
}
