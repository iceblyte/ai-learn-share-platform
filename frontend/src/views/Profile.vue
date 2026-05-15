<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useUserStore } from '@/store/user'
import { userApi } from '@/api/user'
import type { Resource } from '@/types'
import { useRouter } from 'vue-router'

const userStore = useUserStore()
const router = useRouter()
const activeTab = ref('favorites')
const favorites = ref<Resource[]>([])
const myResources = ref<Resource[]>([])
const stats = ref({ publishedCount: 0, totalViews: 0, totalLikes: 0, avgRating: 0, totalFavorites: 0 })
const loading = ref(false)

onMounted(async () => {
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
  await userStore.fetchUserInfo()
  loadFavorites()
  loadStats()
})

async function loadFavorites() {
  activeTab.value = 'favorites'
  loading.value = true
  try {
    const res = await userApi.getFavorites()
    favorites.value = res.data.data || []
  } catch {} finally {
    loading.value = false
  }
}

async function loadMyResources() {
  activeTab.value = 'published'
  loading.value = true
  try {
    const res = await userApi.getMyResources()
    myResources.value = res.data.data || []
  } catch {} finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    const res = await userApi.getStatistics()
    stats.value = res.data.data
  } catch {}
}

function goToResource(id: number) {
  router.push(`/resource/${id}`)
}

function getStatusLabel(status: string) {
  const map: Record<string, string> = { PUBLISHED: '已发布', PENDING: '审核中', REJECTED: '已拒绝', DRAFT: '草稿' }
  return map[status] || status
}

function getStatusClass(status: string) {
  const map: Record<string, string> = {
    PUBLISHED: 'bg-green-100 text-green-700',
    PENDING: 'bg-amber-100 text-amber-700',
    REJECTED: 'bg-red-100 text-red-700',
    DRAFT: 'bg-slate-100 text-slate-600',
  }
  return map[status] || 'bg-slate-100 text-slate-600'
}
</script>

<template>
  <div class="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-8" v-if="userStore.userInfo">
    <!-- Profile Card -->
    <div class="card p-6 mb-8">
      <div class="flex items-center gap-6">
        <div class="w-20 h-20 rounded-full bg-primary-100 flex items-center justify-center text-3xl font-bold text-primary-600">
          {{ userStore.userInfo.nickname?.[0] || userStore.userInfo.username[0] }}
        </div>
        <div>
          <h1 class="text-2xl font-bold text-slate-800">{{ userStore.userInfo.nickname || userStore.userInfo.username }}</h1>
          <p class="text-slate-500">@{{ userStore.userInfo.username }}</p>
          <p v-if="userStore.userInfo.bio" class="text-sm text-slate-600 mt-1">{{ userStore.userInfo.bio }}</p>
          <div class="flex items-center gap-6 mt-3 text-sm">
            <span class="text-slate-500">发布 <strong class="text-slate-800">{{ stats.publishedCount }}</strong></span>
            <span class="text-slate-500">获赞 <strong class="text-slate-800">{{ stats.totalLikes }}</strong></span>
            <span class="text-slate-500">收藏 <strong class="text-slate-800">{{ stats.totalFavorites }}</strong></span>
          </div>
        </div>
      </div>
    </div>

    <!-- Stats Cards -->
    <div class="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
      <div class="card p-4 text-center">
        <p class="text-2xl font-bold text-primary-600">{{ stats.totalViews }}</p>
        <p class="text-sm text-slate-500">总浏览</p>
      </div>
      <div class="card p-4 text-center">
        <p class="text-2xl font-bold text-red-500">{{ stats.totalLikes }}</p>
        <p class="text-sm text-slate-500">总点赞</p>
      </div>
      <div class="card p-4 text-center">
        <p class="text-2xl font-bold text-amber-500">{{ stats.avgRating.toFixed(1) }}</p>
        <p class="text-sm text-slate-500">平均评分</p>
      </div>
      <div class="card p-4 text-center">
        <p class="text-2xl font-bold text-green-500">{{ stats.totalFavorites }}</p>
        <p class="text-sm text-slate-500">被收藏</p>
      </div>
    </div>

    <!-- Tabs -->
    <div class="flex gap-4 mb-6 border-b border-slate-200">
      <button
        @click="loadFavorites()"
        :class="['pb-3 px-1 text-sm font-medium border-b-2 transition-colors', activeTab === 'favorites' ? 'border-primary-600 text-primary-600' : 'border-transparent text-slate-500 hover:text-slate-700']"
      >
        我的收藏
      </button>
      <button
        @click="loadMyResources()"
        :class="['pb-3 px-1 text-sm font-medium border-b-2 transition-colors', activeTab === 'published' ? 'border-primary-600 text-primary-600' : 'border-transparent text-slate-500 hover:text-slate-700']"
      >
        我的发布
      </button>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="flex justify-center py-12">
      <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600"></div>
    </div>

    <!-- Favorites List -->
    <div v-else-if="activeTab === 'favorites'" class="space-y-4">
      <div v-if="favorites.length === 0" class="text-center py-12 text-slate-400">
        <p class="text-lg mb-2">暂无收藏的资源</p>
        <p class="text-sm">浏览资源时点击收藏按钮即可添加</p>
      </div>
      <div
        v-for="res in favorites"
        :key="res.id"
        class="card p-5 cursor-pointer hover:shadow-md transition-shadow"
        @click="goToResource(res.id)"
      >
        <div class="flex items-start gap-4">
          <div class="w-16 h-16 rounded-lg bg-gradient-to-br from-primary-100 to-primary-200 flex items-center justify-center flex-shrink-0">
            <svg class="w-8 h-8 text-primary-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" />
            </svg>
          </div>
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-2 mb-1">
              <span class="px-2 py-0.5 bg-primary-100 text-primary-700 text-xs rounded-full">{{ res.category?.name }}</span>
            </div>
            <h3 class="font-semibold text-slate-800 mb-1">{{ res.title }}</h3>
            <p class="text-sm text-slate-500 line-clamp-2">{{ res.aiSummary || res.description }}</p>
            <div class="flex items-center gap-4 mt-2 text-xs text-slate-400">
              <span>{{ res.publisher?.nickname }}</span>
              <span>⭐ {{ res.avgRating?.toFixed(1) }}</span>
              <span>❤️ {{ res.likeCount }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- My Resources List -->
    <div v-else-if="activeTab === 'published'" class="space-y-4">
      <div v-if="myResources.length === 0" class="text-center py-12 text-slate-400">
        <p class="text-lg mb-2">暂无发布的资源</p>
        <p class="text-sm">点击"发布资源"分享你的学习资料</p>
      </div>
      <div
        v-for="res in myResources"
        :key="res.id"
        class="card p-5 cursor-pointer hover:shadow-md transition-shadow"
        @click="goToResource(res.id)"
      >
        <div class="flex items-start gap-4">
          <div class="w-16 h-16 rounded-lg bg-gradient-to-br from-green-100 to-green-200 flex items-center justify-center flex-shrink-0">
            <svg class="w-8 h-8 text-green-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
            </svg>
          </div>
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-2 mb-1">
              <span :class="['px-2 py-0.5 text-xs rounded-full', getStatusClass(res.status)]">{{ getStatusLabel(res.status) }}</span>
              <span class="text-xs text-slate-400">{{ res.createdAt?.split('T')[0] }}</span>
            </div>
            <h3 class="font-semibold text-slate-800 mb-1">{{ res.title }}</h3>
            <div class="flex items-center gap-4 text-xs text-slate-400">
              <span>👁 {{ res.viewCount }}</span>
              <span>❤️ {{ res.likeCount }}</span>
              <span>⭐ {{ res.avgRating?.toFixed(1) || '-' }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
