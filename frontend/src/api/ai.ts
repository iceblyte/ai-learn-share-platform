import request from './request'
import type { ApiResponse, PageData, Recommendation } from '@/types'

export const aiApi = {
  generateSummary(resourceId: number) {
    return request.post<ApiResponse<{ summary: string }>>(`/ai/summary`, { resourceId })
  },
  getRecommendations(page = 1, size = 10) {
    return request.get<ApiResponse<PageData<Recommendation>>>('/ai/recommendations', {
      params: { page, size },
    })
  },
  getRecommendReasons(resourceIds: number[]) {
    return request.post<ApiResponse<Record<number, string>>>(
      '/ai/recommendations/reasons',
      { resourceIds }
    )
  },
}
