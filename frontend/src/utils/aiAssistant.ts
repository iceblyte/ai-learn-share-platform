import { ref } from 'vue'
import request from '@/api/request'

/** AI 助手公开账号信息（进程内缓存，评论 @ 助手与 AI 头像渲染共用） */
export const aiAssistant = ref<{ id: number; nickname: string } | null>(null)

let fetched = false

export async function ensureAiAssistant() {
  if (fetched) return
  fetched = true
  try {
    const res = await request.get('/config/ai-assistant')
    aiAssistant.value = res.data.data
  } catch {}
}
