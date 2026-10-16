<script setup lang="ts">
/**
 * 递归评论节点：多级嵌套评论的分支展示。
 * 每个节点渲染自己 + 递归渲染 replies，回复框由根组件统一托管状态，
 * 节点通过 emit 通知根组件"我要回复哪条"。
 */
import { ref, watch } from 'vue'
import { commentApi } from '@/api/comment'
import { useUserStore } from '@/store/user'
import type { Comment, Resource } from '@/types'

const props = defineProps<{
  comment: Comment
  resource: Resource | null
  depth: number
  replyToId: number | null
}>()

const emit = defineEmits<{
  reply: [comment: Comment]
  replied: [parentId: number, reply: Comment]
  submitReply: [parentId: number, content: string]
  cancelReply: []
}>()

const userStore = useUserStore()
const liking = ref(false)
const replyText = ref('')

// 切换回复目标时清空输入框
watch(() => props.replyToId, () => {
  replyText.value = ''
})

function submitThisReply() {
  const content = replyText.value.trim()
  if (!content) return
  emit('submitReply', props.comment.id, content)
  replyText.value = ''
}

function avatar(user: Comment['user']): string | null {
  const url = (user as any)?.avatarUrl || (user as any)?.avatar
  return typeof url === 'string' && url ? url : null
}

async function handleLike() {
  if (liking.value) return
  liking.value = true
  try {
    const res = await commentApi.like(props.comment.id)
    props.comment.likeCount = res.data.data.likeCount
  } catch {
    // 点赞失败静默处理
  } finally {
    liking.value = false
  }
}
</script>

<template>
  <div class="flex gap-3">
    <!-- 头像 -->
    <img
      v-if="avatar(comment.user)"
      :src="avatar(comment.user)!"
      class="rounded-full object-cover flex-shrink-0"
      :class="depth === 0 ? 'w-8 h-8' : 'w-6 h-6'"
      alt="avatar"
    />
    <div
      v-else
      class="rounded-full flex items-center justify-center flex-shrink-0"
      :class="depth === 0 ? 'w-8 h-8 bg-green-100' : 'w-6 h-6 bg-primary-100'"
    >
      <span class="font-medium" :class="depth === 0 ? 'text-xs text-green-600' : 'text-[10px] text-primary-600'">
        {{ comment.user?.nickname?.[0] || '用' }}
      </span>
    </div>

    <!-- 内容 -->
    <div class="flex-1 min-w-0">
      <div class="flex items-center gap-2">
        <span class="text-sm font-medium">{{ comment.user?.nickname }}</span>
        <span
          v-if="comment.user && (resource as any)?.publisherId && comment.user.id === (resource as any).publisherId"
          class="text-xs bg-primary-100 text-primary-600 px-1.5 py-0.5 rounded"
        >作者</span>
        <span class="text-xs text-slate-400">{{ new Date(comment.createdAt).toLocaleString() }}</span>
      </div>
      <p class="text-sm text-slate-600 mt-1 break-words whitespace-pre-wrap">{{ comment.content }}</p>
      <div class="flex items-center gap-3 mt-2">
        <button
          class="flex items-center gap-1 text-xs text-slate-400 hover:text-primary-500 transition-colors"
          @click="handleLike"
        >
          <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M14 10h4.764a2 2 0 011.789 2.894l-3.5 7A2 2 0 0115.263 21h-4.017c-.163 0-.326-.02-.485-.06L7 20m7-10V5a2 2 0 00-2-2h-.095c-.5 0-.905.405-.905.905 0 .714-.211 1.412-.608 2.006L7 11v9m7-10h-2M7 20H5a2 2 0 01-2-2v-6a2 2 0 012-2h2.5"/></svg>
          {{ comment.likeCount || 0 }}
        </button>
        <button
          v-if="userStore.isLoggedIn"
          class="text-xs text-slate-400 hover:text-primary-500 transition-colors"
          @click="emit('reply', comment)"
        >
          回复
        </button>
      </div>

      <!-- 回复框：仅被激活的节点显示 -->
      <div v-if="replyToId === comment.id" class="mt-3">
        <textarea
          v-model="replyText"
          class="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent h-16 resize-none"
          :placeholder="`回复 @${comment.user?.nickname || '...'}...`"
          @keydown.enter.exact.prevent="submitThisReply"
        ></textarea>
        <div class="flex gap-2 mt-2">
          <button
            class="bg-primary-500 text-white px-3 py-1 rounded text-sm hover:bg-primary-600 transition-colors disabled:opacity-60"
            :disabled="!replyText.trim()"
            @click="submitThisReply"
          >
            回复
          </button>
          <button
            class="bg-slate-100 text-slate-600 px-3 py-1 rounded text-sm hover:bg-slate-200 transition-colors"
            @click="emit('cancelReply')"
          >
            取消
          </button>
        </div>
      </div>

      <!-- 子评论：递归分支展示 -->
      <div v-if="comment.replies?.length" class="mt-3 space-y-3 border-l-2 border-slate-100 pl-4">
        <CommentNode
          v-for="child in comment.replies"
          :key="child.id"
          :comment="child"
          :resource="resource"
          :depth="depth + 1"
          :reply-to-id="replyToId"
          @reply="(c) => emit('reply', c)"
          @replied="(pid, r) => emit('replied', pid, r)"
          @submit-reply="(pid, content) => emit('submitReply', pid, content)"
          @cancel-reply="emit('cancelReply')"
        />
      </div>
    </div>
  </div>
</template>
