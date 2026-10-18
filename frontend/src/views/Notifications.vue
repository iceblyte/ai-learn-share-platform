<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { notificationApi, type NotificationItem } from '@/api/notification'

/** 站内通知中心：分页列表、点击跳转对应内容、单条/全部已读 */
const router = useRouter()

const items = ref<NotificationItem[]>([])
const page = ref(1)
const size = 15
const total = ref(0)
const loading = ref(true)

onMounted(load)

async function load() {
  loading.value = true
  try {
    const res = await notificationApi.list(page.value, size)
    items.value = res.data.data.records
    total.value = res.data.data.total
  } catch {} finally {
    loading.value = false
  }
}

function goTo(p: number) {
  const tp = Math.max(1, Math.ceil(total.value / size))
  if (p < 1 || p > tp || p === page.value) return
  page.value = p
  load()
}

const TYPE_META: Record<string, { text: string; cls: string }> = {
  FOLLOW: { text: '关注', cls: 'bg-blue-100 text-blue-700' },
  LIKE_RESOURCE: { text: '点赞', cls: 'bg-red-100 text-red-700' },
  LIKE_COMMENT: { text: '点赞', cls: 'bg-red-100 text-red-700' },
  FAVORITE: { text: '收藏', cls: 'bg-amber-100 text-amber-700' },
  COMMENT: { text: '评论', cls: 'bg-green-100 text-green-700' },
  REPLY: { text: '回复', cls: 'bg-green-100 text-green-700' },
  MENTION: { text: '提及', cls: 'bg-purple-100 text-purple-700' },
  AI_SUMMARY: { text: 'AI', cls: 'bg-primary-100 text-primary-700' },
}

function typeMeta(t: string) {
  return TYPE_META[t] || { text: '通知', cls: 'bg-slate-100 text-slate-600' }
}

/** 通知正文：用户名 + 动作描述 */
function describe(n: NotificationItem) {
  const actor = n.actorNickname || '有人'
  switch (n.type) {
    case 'FOLLOW': return `${actor} 关注了你`
    case 'LIKE_RESOURCE': return `${actor} 赞了你的资源`
    case 'LIKE_COMMENT': return `${actor} 赞了你的评论`
    case 'FAVORITE': return `${actor} 收藏了你的资源`
    case 'COMMENT': return `${actor} 评论了你的资源`
    case 'REPLY': return `${actor} 回复了你的评论`
    case 'MENTION': return `${actor} ${n.content || '在评论中提到了你'}`
    case 'AI_SUMMARY': return n.content || 'AI 总结已生成'
    default: return n.content || '你有新的通知'
  }
}

async function handleClick(n: NotificationItem) {
  if (!n.isRead) {
    try { await notificationApi.markRead(n.id) } catch {}
  }
  // 跳转：关注 → 触发者主页；其余 → 关联资源
  if (n.type === 'FOLLOW' && n.actorId) {
    router.push(`/user/${n.actorId}`)
    return
  }
  if (n.resourceId) {
    router.push(`/resource/${n.resourceId}`)
  }
}

async function markAll() {
  try {
    await notificationApi.markAllRead()
    items.value.forEach(i => { i.isRead = 1 })
  } catch {}
}

function formatTime(s: string) {
  const d = new Date(s)
  const diff = Date.now() - d.getTime()
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  return d.toLocaleDateString('zh-CN') + ' ' + d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}
</script>

<template>
  <div class="max-w-3xl mx-auto px-4 py-8">
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-2xl font-bold text-slate-800">通知中心</h1>
      <button
        @click="markAll"
        class="px-3 py-1.5 text-sm text-slate-600 border border-slate-300 rounded-lg hover:bg-slate-50"
      >
        全部已读
      </button>
    </div>

    <div v-if="loading" class="flex justify-center py-16">
      <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600"></div>
    </div>

    <div v-else-if="items.length === 0" class="text-center py-16 text-slate-400">
      <svg class="w-14 h-14 mx-auto text-slate-300 mb-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1h6z"/>
      </svg>
      <p class="text-lg mb-1">暂无通知</p>
      <p class="text-sm">被关注、被点赞、被收藏、被 @ 时会在这里提醒你</p>
    </div>

    <div v-else class="bg-white rounded-xl shadow-sm border border-slate-200 divide-y divide-slate-100">
      <button
        v-for="n in items"
        :key="n.id"
        @click="handleClick(n)"
        :class="['w-full text-left flex items-start gap-3 p-4 hover:bg-slate-50 transition-colors', !n.isRead ? 'bg-primary-50/40' : '']"
      >
        <div class="w-9 h-9 rounded-full flex items-center justify-center overflow-hidden flex-shrink-0">
          <img v-if="n.actorAvatarUrl" :src="n.actorAvatarUrl" class="w-full h-full object-cover" />
          <div v-else class="w-full h-full bg-primary-100 flex items-center justify-center">
            <span class="text-sm font-medium text-primary-600">{{ n.actorNickname?.[0] || '系' }}</span>
          </div>
        </div>
        <div class="flex-1 min-w-0">
          <div class="flex items-center gap-2">
            <span :class="['text-xs px-1.5 py-0.5 rounded font-medium', typeMeta(n.type).cls]">{{ typeMeta(n.type).text }}</span>
            <p :class="['text-sm truncate', !n.isRead ? 'font-medium text-slate-800' : 'text-slate-600']">{{ describe(n) }}</p>
            <span v-if="!n.isRead" class="w-2 h-2 rounded-full bg-red-500 flex-shrink-0"></span>
          </div>
          <p v-if="n.resourceTitle" class="text-xs text-slate-400 mt-1 truncate">「{{ n.resourceTitle }}」</p>
          <p v-if="n.content && n.type !== 'MENTION' && n.type !== 'AI_SUMMARY'" class="text-xs text-slate-400 mt-0.5 truncate">{{ n.content }}</p>
          <p class="text-xs text-slate-400 mt-0.5">{{ formatTime(n.createdAt) }}</p>
        </div>
      </button>
    </div>

    <div v-if="total > size" class="flex items-center justify-center gap-3 mt-5">
      <button
        @click="goTo(page - 1)"
        :disabled="page <= 1"
        class="px-3 py-1.5 text-sm border border-slate-300 rounded-lg bg-white hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed"
      >上一页</button>
      <span class="text-sm text-slate-500">{{ page }} / {{ Math.max(1, Math.ceil(total / size)) }}</span>
      <button
        @click="goTo(page + 1)"
        :disabled="page >= Math.ceil(total / size)"
        class="px-3 py-1.5 text-sm border border-slate-300 rounded-lg bg-white hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed"
      >下一页</button>
    </div>
  </div>
</template>
