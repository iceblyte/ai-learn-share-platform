<script setup lang="ts">
/**
 * 会话列表（悬浮球抽屉与全屏对话页共用）：
 * 新建、搜索、内联重命名、删除、分页"加载更多"。
 * 直接接收 useAiChat 组合式对象，避免多视图重复事件转发。
 */
import type { useAiChat } from '@/composables/useAiChat'

type Chat = ReturnType<typeof useAiChat>

const props = defineProps<{
  chat: Chat
  currentSessionId: number | null
}>()

const emit = defineEmits<{
  open: [sessionId: number]
  create: []
}>()

function onCreate() {
  props.chat.startNewSession()
  emit('create')
}
</script>

<template>
  <div>
    <button
      class="mb-3 w-full rounded-xl border border-dashed border-slate-300 px-3 py-2 text-xs text-slate-500 transition hover:border-primary-300 hover:text-primary-600"
      @click="onCreate"
    >
      ＋ 新对话
    </button>

    <div class="mb-3 flex gap-2">
      <input
        v-model="chat.searchKeyword.value"
        placeholder="搜索问过的问题..."
        class="min-w-0 flex-1 rounded-lg border border-slate-300 px-2 py-1.5 text-xs outline-none focus:border-primary-400"
        @keydown.enter="chat.runSearch"
      />
      <button
        class="rounded-lg bg-slate-800 px-2.5 py-1.5 text-xs text-white transition hover:bg-primary-600 disabled:bg-slate-300"
        :disabled="chat.searching.value"
        @click="chat.runSearch"
      >
        搜索
      </button>
    </div>

    <ul v-if="chat.searchResults.value.length" class="mb-3 space-y-2">
      <li
        v-for="result in chat.searchResults.value"
        :key="`${result.sessionId}-${result.content}`"
        class="cursor-pointer rounded-xl border border-slate-200 bg-white px-3 py-2 transition hover:border-primary-300"
        @click="emit('open', result.sessionId)"
      >
        <p class="truncate text-xs font-medium text-slate-700">{{ result.content }}</p>
        <p class="truncate text-[11px] text-slate-400">{{ result.sessionTitle }}</p>
      </li>
    </ul>

    <p v-if="chat.historyLoading.value && !chat.sessions.value.length" class="py-4 text-center text-xs text-slate-400">加载中...</p>
    <p v-else-if="!chat.sessions.value.length" class="py-4 text-center text-xs text-slate-400">还没有会话记录，问第一个问题吧</p>
    <ul v-else class="space-y-2">
      <li
        v-for="session in chat.sessions.value"
        :key="session.id"
        class="group flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-3 py-2 transition hover:border-primary-300"
        :class="{ 'border-primary-300 bg-primary-50': session.id === currentSessionId }"
      >
        <!-- 重命名态：内联输入框 -->
        <template v-if="chat.editingSessionId.value === session.id">
          <input
            v-model="chat.editingTitle.value"
            class="min-w-0 flex-1 rounded-lg border border-primary-300 px-2 py-1 text-xs outline-none focus:border-primary-500"
            placeholder="输入新的标题"
            @keydown.enter.prevent="chat.saveRename(session.id)"
            @keydown.esc="chat.editingSessionId.value = null"
          />
          <button
            class="shrink-0 rounded p-1 text-primary-500 transition hover:text-primary-600"
            title="保存标题"
            @click="chat.saveRename(session.id)"
          >
            <svg class="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"/>
            </svg>
          </button>
          <button
            class="shrink-0 rounded p-1 text-slate-300 transition hover:text-slate-500"
            title="取消"
            @click="chat.editingSessionId.value = null"
          >
            <svg class="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
            </svg>
          </button>
        </template>
        <template v-else>
          <button class="min-w-0 flex-1 text-left" @click="emit('open', session.id)">
            <p class="truncate text-xs font-medium text-slate-700">{{ session.title || '未命名会话' }}</p>
            <p class="text-[11px] text-slate-400">{{ chat.formatTime(session.lastMessageAt) }} · {{ session.messageCount }} 条</p>
          </button>
          <button
            class="shrink-0 rounded p-1 text-slate-300 opacity-0 transition group-hover:opacity-100 hover:text-primary-500"
            title="重命名"
            @click.stop="chat.startRename(session)"
          >
            <svg class="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"/>
            </svg>
          </button>
          <button
            class="shrink-0 rounded p-1 text-slate-300 opacity-0 transition group-hover:opacity-100 hover:text-red-500"
            title="删除会话"
            @click.stop="chat.removeSession(session.id)"
          >
            <svg class="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/>
            </svg>
          </button>
        </template>
      </li>
    </ul>

    <!-- 分页：加载更多（Bug 修复：会话过多时不再无法查看） -->
    <div v-if="chat.sessions.value.length && chat.hasMoreSessions()" class="mt-3 flex justify-center">
      <button
        class="rounded-full border border-slate-200 bg-white px-3 py-1 text-xs text-slate-500 transition hover:border-primary-200 hover:text-primary-600 disabled:cursor-not-allowed disabled:opacity-60"
        :disabled="chat.historyLoading.value"
        @click="chat.loadMoreSessions()"
      >
        {{ chat.historyLoading.value ? '加载中...' : `加载更多（已显示 ${chat.sessions.value.length}/${chat.sessionsTotal.value}）` }}
      </button>
    </div>
  </div>
</template>
