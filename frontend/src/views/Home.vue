<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { resourceApi } from '@/api/resource'
import { categoryApi } from '@/api/category'
import { aiApi } from '@/api/ai'
import type { Resource, Category, Recommendation } from '@/types'

const router = useRouter()
const categories = ref<Category[]>([])
const hotResources = ref<Resource[]>([])
const latestResources = ref<Resource[]>([])
const recommendations = ref<Recommendation[]>([])
const loading = ref(true)
const activeCategory = ref<number | null>(null)
const categoryResources = ref<Resource[]>([])
const categoryLoading = ref(false)
const gradients = [
  'from-blue-400 to-blue-600',
  'from-green-400 to-emerald-600',
  'from-purple-400 to-violet-600',
  'from-amber-400 to-orange-600',
  'from-red-400 to-rose-600',
  'from-cyan-400 to-teal-600',
]

onMounted(async () => {
  try {
    const [catRes, hotRes, latestRes] = await Promise.all([
      categoryApi.getTree(),
      resourceApi.getHot(),
      resourceApi.getLatest(),
    ])
    categories.value = catRes.data.data
    hotResources.value = hotRes.data.data
    latestResources.value = latestRes.data.data

    try {
      const recRes = await aiApi.getRecommendations()
      recommendations.value = normalizeRecommendations(recRes.data.data)
    } catch {}
  } finally {
    loading.value = false
  }
})

function goToResource(id: number) {
  router.push(`/resource/${id}`)
}

async function goToCategory(id: number | null) {
  activeCategory.value = id
  if (id === null) {
    categoryResources.value = []
    return
  }
  categoryLoading.value = true
  try {
    const res = await resourceApi.getList({ categoryId: id, size: 8 })
    categoryResources.value = res.data.data?.records || []
  } catch {
    categoryResources.value = []
  } finally {
    categoryLoading.value = false
  }
}

function getGradient(index: number) {
  return gradients[index % gradients.length]
}

function normalizeRecommendations(data: any): Recommendation[] {
  if (Array.isArray(data)) return data
  return data?.records || []
}

function formatTimeAgo(dateStr: string): string {
  const now = Date.now()
  const date = new Date(dateStr).getTime()
  const diff = now - date
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days} 天前`
  return new Date(dateStr).toLocaleDateString()
}
</script>

<template>
  <div>
    <!-- Category Navigation -->
    <nav class="bg-white border-b border-slate-200">
      <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div class="flex items-center gap-6 h-12 overflow-x-auto text-sm">
          <button
            @click="goToCategory(null)"
            :class="[
              'whitespace-nowrap pb-0.5 font-medium transition-colors',
              activeCategory === null
                ? 'text-primary-600 border-b-2 border-primary-500'
                : 'text-slate-600 hover:text-primary-500'
            ]"
          >
            全部
          </button>
          <button
            v-for="cat in categories"
            :key="cat.id"
            @click="goToCategory(cat.id)"
            :class="[
              'whitespace-nowrap pb-0.5 transition-colors',
              activeCategory === cat.id
                ? 'text-primary-600 font-medium border-b-2 border-primary-500'
                : 'text-slate-600 hover:text-primary-500'
            ]"
          >
            {{ cat.name }}
          </button>
        </div>
      </div>
    </nav>

    <main class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">

      <!-- Category Filtered Resources -->
      <section v-if="activeCategory !== null" class="mb-10">
        <div class="flex items-center justify-between mb-4">
          <h2 class="text-lg font-semibold">
            {{ categories.find(c => c.id === activeCategory)?.name || '分类资源' }}
          </h2>
          <button
            @click="goToCategory(null)"
            class="text-sm text-slate-500 hover:text-primary-500"
          >
            清除筛选
          </button>
        </div>
        <div v-if="categoryLoading" class="flex justify-center py-12">
          <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600"></div>
        </div>
        <div v-else-if="categoryResources.length === 0" class="text-center py-12 text-slate-400">
          该分类下暂无资源
        </div>
        <div v-else class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <div
            v-for="(res, index) in categoryResources"
            :key="res.id"
            class="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden hover:shadow-md transition-shadow cursor-pointer"
            @click="goToResource(res.id)"
          >
            <div class="h-32 bg-gradient-to-br flex items-center justify-center overflow-hidden" :class="getGradient(index)">
              <img v-if="res.coverImageUrl" :src="res.coverImageUrl" :alt="res.title" class="w-full h-full object-cover" loading="lazy"/>
              <svg v-else class="w-12 h-12 text-white opacity-80" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/>
              </svg>
            </div>
            <div class="p-3">
              <h3 class="font-medium text-sm truncate">{{ res.title }}</h3>
              <p class="text-xs text-slate-500 mt-1 line-clamp-2">{{ res.aiSummary || res.description }}</p>
              <div class="flex items-center gap-1 mt-2">
                <span v-for="tag in res.tags?.slice(0, 2)" :key="tag.id" class="text-xs bg-blue-100 text-blue-600 px-1.5 py-0.5 rounded">
                  {{ tag.name }}
                </span>
              </div>
              <div class="flex items-center justify-between mt-3 pt-2 border-t border-slate-100">
                <span class="text-xs text-slate-400">{{ res.publisher?.nickname }}</span>
                <span class="text-xs text-slate-400 flex items-center gap-0.5">
                  <svg class="w-3.5 h-3.5 text-amber-400" fill="currentColor" viewBox="0 0 24 24">
                    <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/>
                  </svg>
                  {{ res.avgRating?.toFixed(1) }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- AI Recommendation Section -->
      <section v-if="recommendations.length > 0" class="mb-10">
        <div class="flex items-center justify-between mb-4">
          <div class="flex items-center gap-2">
            <div class="w-6 h-6 bg-gradient-to-br from-purple-500 to-primary-500 rounded-md flex items-center justify-center">
              <svg class="w-4 h-4 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"/>
              </svg>
            </div>
            <h2 class="text-lg font-semibold">为你推荐</h2>
            <span class="text-xs bg-purple-100 text-purple-600 px-2 py-0.5 rounded-full">AI 精选</span>
          </div>
        </div>
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          <div
            v-for="rec in recommendations"
            :key="rec.resource.id"
            class="bg-white rounded-xl shadow-sm border border-slate-200 p-4 hover:shadow-md transition-shadow cursor-pointer"
            @click="goToResource(rec.resource.id)"
          >
            <div class="flex items-start gap-3">
              <div class="w-16 h-16 bg-gradient-to-br from-purple-100 to-primary-100 rounded-lg flex items-center justify-center flex-shrink-0 overflow-hidden">
                <img v-if="rec.resource.coverImageUrl" :src="rec.resource.coverImageUrl" :alt="rec.resource.title" class="w-full h-full object-cover" loading="lazy"/>
                <svg v-else class="w-8 h-8 text-purple-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"/>
                </svg>
              </div>
              <div class="flex-1 min-w-0">
                <h3 class="font-medium text-sm truncate">{{ rec.resource.title }}</h3>
                <p class="text-xs text-slate-500 mt-1 line-clamp-2">{{ rec.resource.aiSummary || rec.resource.description }}</p>
                <div class="flex items-center gap-2 mt-2">
                  <span
                    v-for="tag in rec.resource.tags?.slice(0, 2)"
                    :key="tag.id"
                    class="text-xs bg-blue-100 text-blue-600 px-1.5 py-0.5 rounded"
                  >
                    {{ tag.name }}
                  </span>
                </div>
              </div>
            </div>
            <div class="mt-3 pt-3 border-t border-slate-100 flex items-center gap-2">
              <svg class="w-4 h-4 text-purple-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"/>
              </svg>
              <p class="text-xs text-slate-500">{{ rec.recommendReason }}</p>
            </div>
          </div>
        </div>
      </section>

      <!-- Hot Resources -->
      <section class="mb-10">
        <div class="flex items-center justify-between mb-4">
          <div class="flex items-center gap-2">
            <svg class="w-5 h-5 text-red-500" fill="currentColor" viewBox="0 0 24 24">
              <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/>
            </svg>
            <h2 class="text-lg font-semibold">热门资源</h2>
          </div>
          <button
            @click="router.push({ name: 'Search', query: { sortBy: 'hot' } })"
            class="text-sm text-primary-500 hover:text-primary-600"
          >
            查看更多 &rarr;
          </button>
        </div>
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <div
            v-for="(res, index) in hotResources"
            :key="res.id"
            class="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden hover:shadow-md transition-shadow cursor-pointer"
            @click="goToResource(res.id)"
          >
            <div class="h-32 bg-gradient-to-br flex items-center justify-center overflow-hidden" :class="getGradient(index)">
              <img v-if="res.coverImageUrl" :src="res.coverImageUrl" :alt="res.title" class="w-full h-full object-cover" loading="lazy"/>
              <svg v-else class="w-12 h-12 text-white opacity-80" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/>
              </svg>
            </div>
            <div class="p-3">
              <h3 class="font-medium text-sm truncate">{{ res.title }}</h3>
              <p class="text-xs text-slate-500 mt-1 line-clamp-2">{{ res.aiSummary || res.description }}</p>
              <div class="flex items-center gap-1 mt-2">
                <span
                  v-for="tag in res.tags?.slice(0, 2)"
                  :key="tag.id"
                  class="text-xs bg-blue-100 text-blue-600 px-1.5 py-0.5 rounded"
                >
                  {{ tag.name }}
                </span>
              </div>
              <div class="flex items-center justify-between mt-3 pt-2 border-t border-slate-100">
                <div class="flex items-center gap-1">
                  <div class="w-5 h-5 bg-primary-100 rounded-full flex items-center justify-center">
                    <span class="text-[10px] font-medium text-primary-600">
                      {{ res.publisher?.nickname?.[0] || '用' }}
                    </span>
                  </div>
                  <span class="text-xs text-slate-500">{{ res.publisher?.nickname }}</span>
                </div>
                <div class="flex items-center gap-2 text-xs text-slate-400">
                  <span class="flex items-center gap-0.5">
                    <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z"/>
                    </svg>
                    {{ res.likeCount }}
                  </span>
                  <span class="flex items-center gap-0.5">
                    <svg class="w-3.5 h-3.5 text-amber-400" fill="currentColor" viewBox="0 0 24 24">
                      <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/>
                    </svg>
                    {{ res.avgRating?.toFixed(1) }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- Latest Resources -->
      <section>
        <div class="flex items-center justify-between mb-4">
          <h2 class="text-lg font-semibold">最新资源</h2>
          <button
            @click="router.push({ name: 'Search', query: { sortBy: 'latest' } })"
            class="text-sm text-primary-500 hover:text-primary-600"
          >
            查看更多 &rarr;
          </button>
        </div>
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <div
            v-for="(res, index) in latestResources"
            :key="res.id"
            class="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden hover:shadow-md transition-shadow cursor-pointer"
            @click="goToResource(res.id)"
          >
            <div class="h-32 bg-gradient-to-br flex items-center justify-center overflow-hidden" :class="getGradient(index + 3)">
              <img v-if="res.coverImageUrl" :src="res.coverImageUrl" :alt="res.title" class="w-full h-full object-cover" loading="lazy"/>
              <svg v-else class="w-12 h-12 text-white opacity-80" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"/>
              </svg>
            </div>
            <div class="p-3">
              <h3 class="font-medium text-sm truncate">{{ res.title }}</h3>
              <p class="text-xs text-slate-500 mt-1 line-clamp-2">{{ res.aiSummary || res.description }}</p>
              <div class="flex items-center gap-1 mt-2">
                <span
                  v-for="tag in res.tags?.slice(0, 2)"
                  :key="tag.id"
                  class="text-xs bg-blue-100 text-blue-600 px-1.5 py-0.5 rounded"
                >
                  {{ tag.name }}
                </span>
              </div>
              <div class="flex items-center justify-between mt-3 pt-2 border-t border-slate-100">
                <span class="text-xs text-slate-400">{{ formatTimeAgo(res.createdAt) }}</span>
                <span class="text-xs text-slate-400 flex items-center gap-0.5">
                  <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/>
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"/>
                  </svg>
                  {{ res.viewCount }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- Loading -->
      <div v-if="loading" class="flex justify-center py-20">
        <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600"></div>
      </div>
    </main>
  </div>
</template>
