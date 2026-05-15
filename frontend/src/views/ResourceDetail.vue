<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { resourceApi } from '@/api/resource'
import { commentApi } from '@/api/comment'
import { useUserStore } from '@/store/user'
import type { Resource, Comment } from '@/types'

const route = useRoute()
const userStore = useUserStore()
const id = Number(route.params.id)

const resource = ref<Resource | null>(null)
const comments = ref<Comment[]>([])
const newComment = ref('')
const replyTo = ref<number | null>(null)
const replyContent = ref('')
const myRating = ref(0)
const loading = ref(true)

onMounted(async () => {
  try {
    const [resRes, comRes] = await Promise.all([
      resourceApi.getDetail(id),
      commentApi.getList(id),
    ])
    resource.value = resRes.data.data
    comments.value = comRes.data.data.records
  } finally {
    loading.value = false
  }
})

async function handleLike() {
  if (!userStore.isLoggedIn) return
  const res = await resourceApi.like(id)
  if (resource.value) {
    resource.value.likeCount = res.data.data.likeCount
  }
}

async function handleFavorite() {
  if (!userStore.isLoggedIn) return
  const res = await resourceApi.favorite(id)
  if (resource.value) {
    resource.value.favoriteCount = res.data.data.favoriteCount
  }
}

async function handleRate(score: number) {
  if (!userStore.isLoggedIn) return
  myRating.value = score
  const res = await resourceApi.rate(id, score)
  if (resource.value) {
    resource.value.avgRating = res.data.data.avgRating
    resource.value.ratingCount = res.data.data.ratingCount
  }
}

async function submitComment() {
  if (!newComment.value.trim()) return
  const res = await commentApi.create(id, newComment.value)
  comments.value.unshift(res.data.data)
  newComment.value = ''
  if (resource.value) resource.value.commentCount++
}

async function submitReply(parentId: number) {
  if (!replyContent.value.trim()) return
  const res = await commentApi.create(id, replyContent.value, parentId)
  const parent = comments.value.find(c => c.id === parentId)
  if (parent) {
    if (!parent.replies) parent.replies = []
    parent.replies.push(res.data.data)
  }
  replyTo.value = null
  replyContent.value = ''
}
</script>

<template>
  <div class="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8" v-if="resource">
    <!-- Header -->
    <div class="mb-8">
      <div class="flex items-center gap-2 mb-3">
        <span class="px-3 py-1 bg-primary-100 text-primary-700 text-sm rounded-full">{{ resource.category?.name }}</span>
        <span v-for="tag in resource.tags" :key="tag.id" class="px-2 py-0.5 bg-slate-100 text-slate-600 text-xs rounded">
          {{ tag.name }}
        </span>
      </div>
      <h1 class="text-3xl font-bold text-slate-800 mb-4">{{ resource.title }}</h1>
      <div class="flex items-center space-x-4 text-sm text-slate-500">
        <span>{{ resource.publisher?.nickname }}</span>
        <span>{{ new Date(resource.createdAt).toLocaleDateString() }}</span>
        <span>👁 {{ resource.viewCount }}</span>
      </div>
    </div>

    <!-- AI Summary -->
    <div v-if="resource.aiSummary" class="bg-primary-50 border border-primary-200 rounded-xl p-5 mb-8">
      <h3 class="text-sm font-semibold text-primary-700 mb-2">🤖 AI 智能摘要</h3>
      <p class="text-slate-700">{{ resource.aiSummary }}</p>
    </div>

    <!-- Actions -->
    <div class="flex items-center gap-4 mb-8 pb-6 border-b border-slate-200">
      <button @click="handleLike" class="flex items-center gap-2 px-4 py-2 rounded-lg bg-slate-100 hover:bg-red-50 text-slate-600 hover:text-red-600 transition-colors">
        ❤️ {{ resource.likeCount }}
      </button>
      <button @click="handleFavorite" class="flex items-center gap-2 px-4 py-2 rounded-lg bg-slate-100 hover:bg-amber-50 text-slate-600 hover:text-amber-600 transition-colors">
        ⭐ {{ resource.favoriteCount }}
      </button>
      <div class="flex items-center gap-1 ml-4">
        <span class="text-sm text-slate-500 mr-2">评分：</span>
        <button v-for="s in 5" :key="s" @click="handleRate(s)" class="text-2xl" :class="s <= myRating ? 'text-amber-400' : 'text-slate-300'">★</button>
        <span class="text-sm text-slate-500 ml-2">{{ resource.avgRating?.toFixed(1) }} ({{ resource.ratingCount }}人)</span>
      </div>
    </div>

    <!-- Description -->
    <div class="prose prose-slate max-w-none mb-12">
      <div v-html="resource.description" class="whitespace-pre-wrap"></div>
    </div>

    <!-- Download / Link -->
    <div v-if="resource.resourceType === 'LINK' && resource.externalUrl" class="mb-12">
      <a :href="resource.externalUrl" target="_blank" class="btn-primary px-8 py-3">
        🔗 访问资源链接
      </a>
    </div>

    <!-- Comments -->
    <section>
      <h2 class="text-xl font-semibold text-slate-800 mb-6">评论 ({{ resource.commentCount }})</h2>

      <!-- New Comment -->
      <div v-if="userStore.isLoggedIn" class="mb-8">
        <textarea v-model="newComment" class="input-field h-24 resize-none" placeholder="写下你的评论..."></textarea>
        <button @click="submitComment" class="btn-primary mt-3">发表评论</button>
      </div>
      <div v-else class="mb-8 p-4 bg-slate-50 rounded-lg text-center">
        <router-link to="/login" class="text-primary-600 hover:underline">登录</router-link> 后发表评论
      </div>

      <!-- Comment List -->
      <div class="space-y-6">
        <div v-for="comment in comments" :key="comment.id" class="border-b border-slate-100 pb-6">
          <div class="flex items-start gap-3">
            <img :src="comment.user?.avatar || '/default-avatar.png'" class="w-10 h-10 rounded-full" alt="" />
            <div class="flex-1">
              <div class="flex items-center gap-2 mb-1">
                <span class="font-medium text-slate-700 text-sm">{{ comment.user?.nickname }}</span>
                <span class="text-xs text-slate-400">{{ new Date(comment.createdAt).toLocaleString() }}</span>
              </div>
              <p class="text-slate-600 text-sm mb-2">{{ comment.content }}</p>
              <button v-if="userStore.isLoggedIn" @click="replyTo = comment.id" class="text-xs text-primary-600 hover:underline">回复</button>

              <!-- Replies -->
              <div v-if="comment.replies?.length" class="mt-4 ml-4 space-y-3">
                <div v-for="reply in comment.replies" :key="reply.id" class="flex items-start gap-2">
                  <img :src="reply.user?.avatar || '/default-avatar.png'" class="w-7 h-7 rounded-full" alt="" />
                  <div>
                    <span class="font-medium text-slate-700 text-xs">{{ reply.user?.nickname }}</span>
                    <span class="text-xs text-slate-400 ml-2">{{ new Date(reply.createdAt).toLocaleString() }}</span>
                    <p class="text-slate-600 text-sm mt-1">{{ reply.content }}</p>
                  </div>
                </div>
              </div>

              <!-- Reply Form -->
              <div v-if="replyTo === comment.id" class="mt-3">
                <textarea v-model="replyContent" class="input-field h-16 resize-none text-sm" placeholder="回复..."></textarea>
                <div class="flex gap-2 mt-2">
                  <button @click="submitReply(comment.id)" class="btn-primary text-sm px-3 py-1">回复</button>
                  <button @click="replyTo = null" class="btn-secondary text-sm px-3 py-1">取消</button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>

  <!-- Loading -->
  <div v-else-if="loading" class="flex justify-center py-20">
    <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600"></div>
  </div>
</template>
