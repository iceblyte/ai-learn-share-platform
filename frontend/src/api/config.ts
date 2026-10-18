import request from './request'
import type { ApiResponse } from '@/types'

/** 系统配置（登录可读的展示类开关，写操作在管理后台） */
export const configApi = {
  /** AI 智能摘要自动生成开关 */
  getAiSummaryEnabled() {
    return request.get<ApiResponse<{ enabled: boolean }>>('/config/ai-summary')
  },
}
