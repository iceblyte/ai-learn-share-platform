import request from './request'
import type { ApiResponse, PageData } from '@/types'

export interface ChatSessionItem {
  id: number
  userId: number
  title: string
  contextType: string
  contextId: number | null
  messageCount: number
  lastMessageAt: string | null
  createdAt: string
}

export interface ChatMessageItem {
  id: number
  sessionId: number
  role: 'user' | 'assistant'
  content: string
  contextJson?: string | null
  tokensUsed: number
  latencyMs: number
  status: 'OK' | 'FAILED'
  createdAt: string
}

export interface ChatSearchItem {
  messageId: number
  sessionId: number
  sessionTitle: string
  role: string
  content: string
  createdAt: string
}

/** 游标分页结果（扩展四）：records 按 id 倒序（最新在前），nextCursor 用于翻更早消息 */
export interface CursorPageData<T> {
  records: T[]
  nextCursor: number | null
  hasMore: boolean
}

/** 对话持久化 API（扩展二）。流式继续提问走 fetch，不经过 axios。 */
export const chatApi = {
  createSession(contextType?: string, contextId?: number) {
    return request.post<ApiResponse<ChatSessionItem>>('/chat/sessions', {
      contextType,
      contextId,
    })
  },
  listSessions(page = 1, size = 20) {
    return request.get<ApiResponse<PageData<ChatSessionItem>>>('/chat/sessions', {
      params: { page, size },
    })
  },
  listMessages(sessionId: number, page = 1, size = 50) {
    return request.get<ApiResponse<PageData<ChatMessageItem>>>(
      `/chat/sessions/${sessionId}/messages`,
      { params: { page, size } }
    )
  },
  /** 游标分页（扩展四）：不带 beforeId 查最新一页，向上翻更早消息传 nextCursor */
  listMessagesByCursor(sessionId: number, beforeId?: number | null, limit = 20) {
    return request.get<ApiResponse<CursorPageData<ChatMessageItem>>>(
      `/chat/sessions/${sessionId}/messages/cursor`,
      { params: { beforeId: beforeId ?? undefined, limit } }
    )
  },
  deleteSession(sessionId: number) {
    return request.delete<ApiResponse<void>>(`/chat/sessions/${sessionId}`)
  },
  /** 重命名历史会话标题 */
  renameSession(sessionId: number, title: string) {
    return request.put<ApiResponse<void>>(`/chat/sessions/${sessionId}/title`, { title })
  },
  search(keyword: string, limit = 20) {
    return request.get<ApiResponse<ChatSearchItem[]>>('/chat/messages/search', {
      params: { keyword, limit },
    })
  },
  exportMarkdown(sessionId: number) {
    return request.post<ApiResponse<{ markdown: string; filename: string }>>(
      `/chat/sessions/${sessionId}/export`
    )
  },
}
