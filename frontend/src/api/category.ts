import request from './request'
import type { ApiResponse, Category, Tag } from '@/types'

export const categoryApi = {
  getTree() {
    return request.get<ApiResponse<Category[]>>('/categories')
  },
  getDetail(id: number) {
    return request.get<ApiResponse<Category>>(`/categories/${id}`)
  },
}

export const tagApi = {
  getList() {
    return request.get<ApiResponse<Tag[]>>('/tags')
  },
  getHot() {
    return request.get<ApiResponse<Tag[]>>('/tags/hot')
  },
  search(keyword: string) {
    return request.get<ApiResponse<Tag[]>>('/tags/search', { params: { keyword } })
  },
}
