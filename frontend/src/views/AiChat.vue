<script setup lang="ts">
/**
 * 全屏 AI 对话页（DeepSeek 风格）：左侧历史会话列表（搜索/重命名/删除/分页），
 * 右侧对话区（markdown 渲染 + 工具调用状态 + 流式输出）。
 * 与悬浮球共用 useAiChat 组合式与消息/会话组件。
 */
import { nextTick, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { useAiChat } from '@/composables/useAiChat'
import AiMessageBubble from '@/components/AiMessageBubble.vue'
import ChatSessionList from '@/components/ChatSessionList.vue'
import AiAvatarMark from '@/components/AiAvatarMark.vue'

const router = useRouter()
const userStore = useUserStore()

const chat = useAiChat({
  welcome: '我是 AI 学习助手。可以问我资源内容、学习路径、技术概念，或者让我帮你检索平台资料。',
  // 全屏页不携带页面上下文（不挂在某个资源页下）
  getPayloadContext: () => ({}),
})
const {
  messages,
  sending,
  input,
  currentSessionId,
  sendMessage,
  loadingOlder,
  hasOlderMessages,
  fetchOlderMessages,
  historyLoading,
  loadSessions,
  openSession,
} = chat

const messageViewport = ref<HTMLElement | null>(null)
let autoScrollEnabled = true
let suppressAutoScroll = false

/** 空会话引导的快捷提问 */
const quickPrompts = [
  '平台有哪些 Java 学习资源？',
  '推荐几本算法入门资料',
  '帮我制定两周的 Python 学习计划',
]

onMounted(() => {
  if (userStore.isLoggedIn) {
    loadSessions().catch(() => {})
  }
})

function handleViewportScroll() {
  const el = messageViewport.value
  if (!el) return
  autoScrollEnabled = el.scrollHeight - el.scrollTop - el.clientHeight < 64
}

watch(messages, async () => {
  await nextTick()
  if (suppressAutoScroll) return
  if (autoScrollEnabled) scrollToBottom()
}, { deep: true })

function scrollToBottom() {
  if (messageViewport.value) {
    messageViewport.value.scrollTop = messageViewport.value.scrollHeight
  }
}

/** 向上翻更早消息：前插后锚定视口，避免跳底 */
async function loadOlderAnchored() {
  const viewport = messageViewport.value
  const prevScrollHeight = viewport?.scrollHeight ?? 0
  const prevScrollTop = viewport?.scrollTop ?? 0
  suppressAutoScroll = true
  await fetchOlderMessages()
  await nextTick()
  if (viewport) {
    viewport.scrollTop = viewport.scrollHeight - prevScrollHeight + prevScrollTop
  }
  suppressAutoScroll = false
}

function handleOpenSession(sessionId: number) {
  autoScrollEnabled = true
  openSession(sessionId)
    .then(() => nextTick(() => scrollToBottom()))
    .catch(() => {})
}

function handleCreate() {
  autoScrollEnabled = true
}
</script>

<template>
  <!-- 扣除顶部主站导航（h-16 = 4rem），页面自身占满剩余视口 -->
  <div class="flex h-[calc(100vh-4rem)] overflow-hidden">
    <!-- 左侧：历史会话列表 -->
    <aside class="hidden w-64 shrink-0 flex-col border-r border-slate-200 bg-slate-50 md:flex">
      <div class="flex-1 overflow-y-auto px-3 py-4">
        <template v-if="userStore.isLoggedIn">
          <ChatSessionList
            :chat="chat"
            :current-session-id="currentSessionId"
            @open="handleOpenSession"
            @create="handleCreate"
          />
        </template>
        <template v-else>
          <button
            class="mb-4 w-full rounded-xl bg-primary-600 px-3 py-2 text-xs text-white transition hover:bg-primary-700"
            @click="router.push({ name: 'Login', query: { redirect: '/ai-chat' } })"
          >
            登录后同步历史会话
          </button>
          <p class="text-xs leading-5 text-slate-400">
            登录后可以在这里查看历史会话、继续追问、重命名与搜索。
            未登录也可以直接在右侧与 AI 对话。
          </p>
        </template>
      </div>
      <div class="border-t border-slate-200 px-4 py-3 text-[11px] text-slate-400">
        AI 生成内容仅供参考
      </div>
    </aside>

    <!-- 右侧：对话区 -->
    <section class="flex min-w-0 flex-1 flex-col bg-white">
      <div
        ref="messageViewport"
        class="flex-1 overflow-y-auto px-4 py-6"
        @scroll="handleViewportScroll"
      >
        <div class="mx-auto max-w-3xl space-y-5">
          <div v-if="currentSessionId && hasOlderMessages" class="flex justify-center">
            <button
              class="rounded-full border border-slate-200 bg-white px-3 py-1 text-xs text-slate-500 transition hover:border-primary-200 hover:text-primary-600 disabled:cursor-not-allowed disabled:opacity-60"
              :disabled="loadingOlder"
              @click="loadOlderAnchored"
            >
              {{ loadingOlder ? '加载中...' : '加载更早的消息' }}
            </button>
          </div>

          <!-- 空会话：居中欢迎区 + 快捷提问 -->
          <div v-if="messages.length <= 1 && !currentSessionId" class="flex flex-col items-center pt-24 text-center">
            <AiAvatarMark class="h-14 w-14 shadow-lg shadow-primary-600/25 rounded-2xl" />
            <h2 class="mt-5 text-xl font-semibold text-slate-800">AI 学习助手</h2>
            <p class="mt-2 max-w-md text-sm text-slate-400">{{ messages[0]?.content || '可以问我资源内容、学习路径、技术概念，或让我检索平台资料' }}</p>
            <div class="mt-8 flex flex-wrap justify-center gap-2">
              <button
                v-for="prompt in quickPrompts"
                :key="prompt"
                class="rounded-full border border-slate-200 bg-white px-4 py-2 text-xs text-slate-600 shadow-sm transition hover:border-primary-300 hover:bg-primary-50 hover:text-primary-600"
                :disabled="sending"
                @click="sendMessage(prompt)"
              >
                {{ prompt }}
              </button>
            </div>
          </div>

          <AiMessageBubble
            v-for="message in messages"
            v-show="messages.length > 1 || currentSessionId"
            :key="message.id"
            :message="message"
          />

          <p v-if="historyLoading" class="text-center text-xs text-slate-400">加载中...</p>
        </div>
      </div>

      <div class="border-t border-slate-200 bg-white px-4 py-4">
        <div class="mx-auto flex max-w-3xl items-end gap-3">
          <textarea
            v-model="input"
            rows="2"
            placeholder="给 AI 学习助手发送消息...（Enter 发送）"
            class="max-h-40 min-h-[3rem] flex-1 resize-none rounded-2xl border border-slate-300 px-4 py-3 text-sm text-slate-700 outline-none transition focus:border-primary-400 focus:ring-2 focus:ring-primary-100"
            @keydown.enter.exact.prevent="sendMessage()"
          ></textarea>
          <button
            :disabled="sending || !input.trim()"
            class="flex h-11 w-11 items-center justify-center rounded-2xl bg-primary-600 text-white transition hover:bg-primary-700 disabled:cursor-not-allowed disabled:bg-slate-300"
            @click="sendMessage()"
          >
            <svg v-if="!sending" class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M14.752 11.168l-9.193-5.106A1 1 0 004 6.944v10.112a1 1 0 001.559.832l9.193-5.106a1 1 0 000-1.664z"/>
            </svg>
            <div v-else class="h-5 w-5 animate-spin rounded-full border-2 border-white/35 border-t-white"></div>
          </button>
        </div>
      </div>
    </section>
  </div>
</template>
