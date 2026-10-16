<script setup lang="ts">
/**
 * AI 消息气泡：用户消息靠右（头像在右），助手消息 markdown 渲染靠左
 * （品牌头像在左），并在回答上方展示"调用 xx 工具"状态（SSE tool 事件）。
 */
import { computed } from 'vue'
import { renderMarkdown } from '@/utils/markdown'
import { useUserStore } from '@/store/user'
import type { ChatMessage } from '@/composables/useAiChat'

const props = defineProps<{ message: ChatMessage }>()

const userStore = useUserStore()

const TOOL_LABELS: Record<string, string> = {
  search_resources: '搜索资源库',
  get_resource_detail: '查询资源详情',
}

const html = computed(() => (props.message.role === 'assistant' ? renderMarkdown(props.message.content) : ''))

const toolLabel = (name: string) => TOOL_LABELS[name] || name

const userInitial = computed(
  () =>
    userStore.userInfo?.nickname?.[0] ||
    userStore.userInfo?.username?.[0] ||
    '我'
)
</script>

<template>
  <div :class="message.role === 'user' ? 'flex justify-end gap-2.5' : 'flex justify-start gap-2.5'">
    <!-- 助手头像（品牌书本图标） -->
    <div
      v-if="message.role === 'assistant'"
      class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-primary-600 text-white shadow-sm"
    >
      <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"/>
      </svg>
    </div>

    <div
      :class="[
        'max-w-[85%] rounded-2xl px-3 py-2 text-sm leading-6 shadow-sm',
        message.role === 'user'
          ? 'bg-primary-600 text-white'
          : 'border border-slate-200 bg-white text-slate-700'
      ]"
    >
      <!-- 工具调用状态（仅助手消息） -->
      <div v-if="message.toolSteps?.length" class="mb-2 space-y-1">
        <div
          v-for="(tool, index) in message.toolSteps"
          :key="`${tool}-${index}`"
          class="flex items-center gap-1.5 rounded-lg bg-slate-50 px-2 py-1 text-[11px] text-slate-500"
        >
          <svg
            class="h-3 w-3 shrink-0"
            :class="message.streaming && index === message.toolSteps.length - 1 && !message.content ? 'animate-spin' : ''"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"/>
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/>
          </svg>
          <span>调用 {{ toolLabel(tool) }}</span>
        </div>
      </div>

      <!-- 用户消息纯文本；助手消息 markdown 渲染 -->
      <p v-if="message.role === 'user'" class="whitespace-pre-wrap break-words">{{ message.content }}</p>
      <!-- eslint-disable-next-line vue/no-v-html —— 内容已经 DOMPurify 净化 -->
      <div v-else class="markdown-body break-words" v-html="html"></div>

      <span v-if="message.streaming" class="mt-1 inline-block h-2 w-2 animate-pulse rounded-full bg-emerald-500"></span>
    </div>

    <!-- 用户头像（昵称首字） -->
    <div
      v-if="message.role === 'user'"
      class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary-100 text-xs font-medium text-primary-600"
    >
      {{ userInitial }}
    </div>
  </div>
</template>

<style scoped>
/* markdown 渲染的最小排版（Tailwind preflight 会重置默认样式） */
.markdown-body :deep(p) {
  margin: 0.25rem 0;
}
.markdown-body :deep(p:first-child) {
  margin-top: 0;
}
.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}
.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: 0.375rem 0;
  padding-left: 1.25rem;
}
.markdown-body :deep(ul) {
  list-style: disc;
}
.markdown-body :deep(ol) {
  list-style: decimal;
}
.markdown-body :deep(li) {
  margin: 0.125rem 0;
}
.markdown-body :deep(code) {
  background: #f1f5f9;
  border-radius: 0.25rem;
  padding: 0.1rem 0.3rem;
  font-size: 0.8125rem;
}
.markdown-body :deep(pre) {
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 0.5rem;
  padding: 0.625rem 0.75rem;
  overflow-x: auto;
  margin: 0.375rem 0;
}
.markdown-body :deep(pre code) {
  background: transparent;
  color: inherit;
  padding: 0;
  font-size: 0.8125rem;
}
.markdown-body :deep(strong) {
  font-weight: 600;
  color: inherit;
}
.markdown-body :deep(blockquote) {
  border-left: 3px solid #cbd5e1;
  padding-left: 0.625rem;
  color: #64748b;
  margin: 0.375rem 0;
}
.markdown-body :deep(table) {
  border-collapse: collapse;
  margin: 0.375rem 0;
  font-size: 0.8125rem;
  display: block;
  overflow-x: auto;
}
.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid #e2e8f0;
  padding: 0.25rem 0.5rem;
}
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  font-weight: 600;
  margin: 0.5rem 0 0.25rem;
  font-size: 0.9375rem;
}
.markdown-body :deep(a) {
  color: #2563eb;
  text-decoration: underline;
}
.markdown-body :deep(hr) {
  border-color: #e2e8f0;
  margin: 0.5rem 0;
}
</style>
