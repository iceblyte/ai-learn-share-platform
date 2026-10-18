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
  webSearchEnabled,
  sendMessage,
  loadingOlder,
  hasOlderMessages,
  fetchOlderMessages,
  historyLoading,
  loadSessions,
  openSession,
} = chat

// 右侧来源面板：展示所选消息的联网搜索来源
const panelSources = ref<import('@/composables/useAiChat').WebSourceItem[] | null>(null)

function openSourcesPanel(sources: import('@/composables/useAiChat').WebSourceItem[] | undefined) {
  panelSources.value = sources && sources.length ? sources : null
}

function closeSourcesPanel() {
  panelSources.value = null
}

function hostOf(url: string) {
  try {
    return new URL(url).hostname.replace(/^www\./, '')
  } catch {
    return url
  }
}

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
            side-panel
            @view-sources="openSourcesPanel"
          />

          <p v-if="historyLoading" class="text-center text-xs text-slate-400">加载中...</p>
        </div>
      </div>

      <div class="border-t border-slate-200 bg-white px-4 py-4">
        <div class="mx-auto max-w-3xl">
          <!-- 输入容器：上方输入区，底部工具行（智能搜索 | 发送），DeepSeek 风格 -->
          <div class="rounded-2xl border border-slate-300 bg-white transition focus-within:border-primary-400 focus-within:ring-2 focus-within:ring-primary-100">
            <textarea
              v-model="input"
              rows="2"
              placeholder="给 AI 学习助手发送消息...（Enter 发送）"
              class="max-h-40 w-full resize-none bg-transparent px-4 pt-3 text-sm text-slate-700 outline-none"
              @keydown.enter.exact.prevent="sendMessage()"
            ></textarea>
            <div class="flex items-center justify-between px-2 pb-2">
              <!-- 智能搜索开关（联网检索，回答附来源） -->
              <button
                class="flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-xs transition"
                :class="webSearchEnabled
                  ? 'border-primary-500 bg-primary-50 text-primary-600'
                  : 'border-transparent text-slate-500 hover:bg-slate-100 hover:text-primary-600'"
                :title="webSearchEnabled ? '联网搜索已开启：回答将引用网页来源' : '开启后回答会联网检索并附来源'"
                @click="webSearchEnabled = !webSearchEnabled"
              >
                <svg class="h-4 w-4 shrink-0" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" viewBox="0 0 24 24" aria-hidden="true">
                  <circle cx="12" cy="12" r="8.25" />
                  <path d="M3.75 12h16.5M12 3.75c2.4 2.2 3.75 5.06 3.75 8.25S14.4 18.05 12 20.25c-2.4-2.2-3.75-5.06-3.75-8.25S9.6 5.95 12 3.75z" />
                </svg>
                智能搜索
              </button>
              <button
                :disabled="!input.trim()"
                class="flex h-9 w-9 items-center justify-center rounded-xl bg-primary-600 text-white transition hover:bg-primary-700 disabled:cursor-not-allowed disabled:bg-slate-300"
                @click="sendMessage()"
              >
                <svg v-if="!sending" class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M14.752 11.168l-9.193-5.106A1 1 0 004 6.944v10.112a1 1 0 001.559.832l9.193-5.106a1 1 0 000-1.664z"/>
                </svg>
                <div v-else class="h-5 w-5 animate-spin rounded-full border-2 border-white/35 border-t-white"></div>
              </button>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 右侧来源面板：展示所选回答的联网搜索来源 -->
    <aside v-if="panelSources" class="hidden w-80 shrink-0 flex-col border-l border-slate-200 bg-slate-50 lg:flex">
      <div class="flex items-center justify-between border-b border-slate-200 px-4 py-3">
        <h3 class="text-sm font-semibold text-slate-700">搜索结果</h3>
        <button
          class="rounded p-1 text-slate-400 transition hover:bg-slate-200 hover:text-slate-600"
          title="关闭"
          @click="closeSourcesPanel"
        >
          <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
          </svg>
        </button>
      </div>
      <div class="flex-1 space-y-2.5 overflow-y-auto px-3 py-3">
        <a
          v-for="(source, index) in panelSources"
          :key="`${source.url}-${index}`"
          :href="source.url"
          target="_blank"
          rel="noopener noreferrer"
          class="block rounded-xl border border-slate-200 bg-white px-3 py-2.5 transition hover:border-primary-300 hover:shadow-sm"
        >
          <p class="text-[11px] text-slate-400">{{ hostOf(source.url) }}</p>
          <p class="mt-0.5 text-sm font-medium leading-5 text-slate-800 line-clamp-2">[{{ index + 1 }}] {{ source.title }}</p>
          <p class="mt-1 text-xs leading-5 text-slate-500 line-clamp-3">{{ source.snippet }}</p>
        </a>
      </div>
    </aside>
  </div>
</template>
