<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { searchApi } from '@/api/search'
import { categoryApi, tagApi } from '@/api/category'
import { useUserStore } from '@/store/user'
import type { Resource, Category, Tag, SearchParams } from '@/types'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const keyword = ref((route.query.q as string) || '')
const isNlMode = ref(false)
const results = ref<Resource[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)
const categories = ref<Category[]>([])
const hotTags = ref<Tag[]>([])
const selectedCategory = ref<number | null>((route.query.categoryId as any) || null)
const sortBy = ref('hot')
const toast = ref<{ message: string; type: 'info' | 'error' | 'success' } | null>(null)

function showToast(message: string, type: 'info' | 'error' | 'success' = 'info') {
  toast.value = { message, type }
  setTimeout(() => { toast.value = null }, 3000)
}
const minRating = ref<number | null>(null)
const selectedTypes = ref<string[]>([])
const searchHistory = ref<string[]>([])
const parsedIntent = ref<Record<string, any> | null>(null)

onMounted(async () => {
  const [catRes, tagRes] = await Promise.all([
    categoryApi.getTree(),
    tagApi.getHot(),
  ])
  categories.value = catRes.data.data
  hotTags.value = tagRes.data.data
  if (userStore.isLoggedIn) loadHistory()
  if (keyword.value) handleSearch()
})

async function loadHistory() {
  try {
    const res = await searchApi.getHistory()
    searchHistory.value = res.data.data || []
  } catch {}
}

async function clearHistory() {
  try {
    await searchApi.clearHistory()
    searchHistory.value = []
  } catch {}
}

async function handleSearch() {
  if (!keyword.value.trim() && !selectedCategory.value) return
  loading.value = true
  parsedIntent.value = null
  try {
    if (isNlMode.value) {
      if (!userStore.isLoggedIn) {
        showToast('AI 搜索需要登录，请先登录后再使用', 'error')
        setTimeout(() => router.push('/login'), 1500)
        return
      }
      const res = await searchApi.nlSearch(keyword.value)
      const data = res.data.data as any
      results.value = data.results || []
      total.value = results.value.length
      parsedIntent.value = data.parsedIntent || null
    } else {
      const params: SearchParams = {
        keyword: keyword.value || undefined,
        categoryId: selectedCategory.value || undefined,
        sortBy: sortBy.value as any,
        page: page.value,
        size: 12,
      }
      const res = await searchApi.search(params)
      results.value = res.data.data.records
      total.value = res.data.data.total
    }
  } catch (e: any) {
    // 403 or auth errors on NL search
    if (!userStore.isLoggedIn) {
      alert('AI 搜索需要登录，请先登录后再使用')
      router.push('/login')
    }
  } finally {
    loading.value = false
  }
  if (userStore.isLoggedIn) loadHistory()
}

function goToResource(id: number) {
  router.push(`/resource/${id}`)
}

function getIntentLabel(key: string): string {
  const labels: Record<string, string> = {
    keywords: '关键词',
    category: '分类',
    sortBy: '排序',
    limit: '数量',
    tags: '标签',
  }
  return labels[key] || key
}

function getIntentValue(key: string, value: any): string {
  if (key === 'sortBy') {
    const sortLabels: Record<string, string> = {
      hot: '最热',
      latest: '最新',
      rating: '评分最高',
      view: '最多浏览',
    }
    return sortLabels[value] || value
  }
  if (key === 'limit') return `前 ${value} 个`
  if (Array.isArray(value)) return value.join(', ')
  return String(value)
}
</script>

<template>
  <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6 relative">
    <!-- Toast Notification -->
    <Transition
      enter-active-class="transition-all duration-300 ease-out"
      enter-from-class="opacity-0 -translate-y-4"
      enter-to-class="opacity-100 translate-y-0"
      leave-active-class="transition-all duration-200 ease-in"
      leave-from-class="opacity-100 translate-y-0"
      leave-to-class="opacity-0 -translate-y-4"
    >
      <div
        v-if="toast"
        :class="[
          'absolute top-0 left-1/2 -translate-x-1/2 z-50 flex items-center gap-3 px-5 py-3 rounded-xl shadow-lg text-sm font-medium',
          toast.type === 'error' ? 'bg-red-50 text-red-700 border border-red-200' :
          toast.type === 'success' ? 'bg-emerald-50 text-emerald-700 border border-emerald-200' :
          'bg-blue-50 text-blue-700 border border-blue-200'
        ]"
      >
        <svg v-if="toast.type === 'error'" class="w-5 h-5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>
        <svg v-else-if="toast.type === 'success'" class="w-5 h-5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>
        <svg v-else class="w-5 h-5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>
        <span>{{ toast.message }}</span>
        <button @click="toast = null" class="ml-2 opacity-60 hover:opacity-100 transition-opacity">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/></svg>
        </button>
      </div>
    </Transition>

    <!-- Search Bar -->
    <div class="mb-6">
      <div class="flex items-center gap-3 mb-3">
        <button
          @click="isNlMode = false"
          :class="['px-4 py-2 rounded-lg text-sm font-medium transition-colors', !isNlMode ? 'bg-primary-600 text-white' : 'bg-slate-100 text-slate-600']"
        >
          关键词搜索
        </button>
        <button
          @click="isNlMode = true"
          :class="['px-4 py-2 rounded-lg text-sm font-medium transition-colors', isNlMode ? 'bg-primary-600 text-white' : 'bg-slate-100 text-slate-600']"
        >
          🤖 AI 自然语言搜索
        </button>
      </div>
      <div class="flex gap-3">
        <div class="relative flex-1">
          <input
            v-model="keyword"
            :placeholder="isNlMode ? '例如：推荐关于Java并发编程且评分最高的前5个资源' : '搜索学习资源...'"
            class="w-full pl-10 pr-20 py-2 border border-slate-300 rounded-full text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
            @keyup.enter="handleSearch"
          />
          <svg class="absolute left-3 top-2.5 w-5 h-5 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/>
          </svg>
          <span v-if="isNlMode" class="absolute right-12 top-2 text-xs bg-purple-100 text-purple-600 px-2 py-0.5 rounded-full font-medium">AI</span>
          <button
            @click="handleSearch"
            class="absolute right-2 top-1.5 bg-primary-500 text-white px-3 py-1 rounded-full text-xs hover:bg-primary-600 transition-colors"
          >
            搜索
          </button>
        </div>
      </div>

      <!-- Search History -->
      <div v-if="searchHistory.length > 0 && !parsedIntent" class="flex items-center gap-2 flex-wrap mt-3">
        <span class="text-xs text-slate-400">搜索历史:</span>
        <button
          v-for="item in searchHistory"
          :key="item"
          @click="keyword = item; handleSearch()"
          class="px-2 py-1 bg-slate-50 text-slate-500 text-xs rounded hover:bg-primary-50 hover:text-primary-600 transition-colors"
        >
          {{ item }}
        </button>
        <button @click="clearHistory()" class="text-xs text-slate-400 hover:text-red-500 ml-1">清空</button>
      </div>
    </div>

    <!-- AI Parsed Intent -->
    <div v-if="parsedIntent" class="bg-gradient-to-r from-purple-50 to-primary-50 border border-purple-200 rounded-xl p-4 mb-6">
      <div class="flex items-start gap-3">
        <div class="w-8 h-8 bg-purple-500 rounded-lg flex items-center justify-center flex-shrink-0">
          <svg class="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"/>
          </svg>
        </div>
        <div>
          <h3 class="text-sm font-semibold text-purple-800">AI 搜索解析</h3>
          <p class="text-xs text-purple-600 mt-1">已将你的自然语言查询解析为以下结构化条件：</p>
          <div class="flex flex-wrap items-center gap-2 mt-2">
            <span
              v-for="(value, key) in parsedIntent"
              :key="key"
              class="inline-flex items-center gap-1 text-xs bg-white border border-purple-200 text-purple-700 px-2 py-1 rounded-md"
            >
              <span class="text-purple-400">{{ getIntentLabel(key as string) }}:</span>
              {{ getIntentValue(key as string, value) }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <div class="flex gap-6">
      <!-- Sidebar Filters -->
      <aside class="w-64 flex-shrink-0 hidden lg:block">
        <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-4 sticky top-24">
          <h3 class="text-sm font-semibold mb-3">筛选条件</h3>

          <!-- Category Filter -->
          <div class="mb-4">
            <h4 class="text-xs font-medium text-slate-500 mb-2">分类</h4>
            <div class="space-y-1.5">
              <button
                @click="selectedCategory = null; handleSearch()"
                :class="['w-full text-left px-3 py-2 rounded text-sm transition-colors', selectedCategory === null ? 'bg-primary-50 text-primary-700 font-medium' : 'text-slate-600 hover:bg-slate-50']"
              >
                全部分类
              </button>
              <button
                v-for="cat in categories"
                :key="cat.id"
                @click="selectedCategory = cat.id; handleSearch()"
                :class="['w-full text-left px-3 py-2 rounded text-sm transition-colors', selectedCategory === cat.id ? 'bg-primary-50 text-primary-700 font-medium' : 'text-slate-600 hover:bg-slate-50']"
              >
                {{ cat.name }}
              </button>
            </div>
          </div>

          <!-- Tag Filter -->
          <div class="mb-4">
            <h4 class="text-xs font-medium text-slate-500 mb-2">标签</h4>
            <div class="flex flex-wrap gap-1.5">
              <button
                v-for="tag in hotTags.slice(0, 10)"
                :key="tag.id"
                @click="keyword = tag.name; handleSearch()"
                :class="[
                  'px-2 py-1 text-xs rounded-md cursor-pointer transition-colors',
                  keyword === tag.name
                    ? 'bg-primary-100 text-primary-600'
                    : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                ]"
              >
                {{ tag.name }}
              </button>
            </div>
          </div>

          <!-- Rating Filter -->
          <div class="mb-4">
            <h4 class="text-xs font-medium text-slate-500 mb-2">评分</h4>
            <div class="space-y-1.5">
              <label class="flex items-center gap-2 text-sm cursor-pointer">
                <input type="radio" name="rating" :checked="minRating === 4" @change="minRating = 4; handleSearch()" class="w-4 h-4 text-primary-500 border-slate-300 focus:ring-primary-500">
                <span class="flex items-center gap-1">
                  <svg class="w-4 h-4 text-amber-400" fill="currentColor" viewBox="0 0 24 24"><path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/></svg>
                  4 星及以上
                </span>
              </label>
              <label class="flex items-center gap-2 text-sm cursor-pointer">
                <input type="radio" name="rating" :checked="minRating === 4.5" @change="minRating = 4.5; handleSearch()" class="w-4 h-4 text-primary-500 border-slate-300 focus:ring-primary-500">
                <span class="flex items-center gap-1">
                  <svg class="w-4 h-4 text-amber-400" fill="currentColor" viewBox="0 0 24 24"><path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/></svg>
                  4.5 星及以上
                </span>
              </label>
              <label class="flex items-center gap-2 text-sm cursor-pointer">
                <input type="radio" name="rating" :checked="minRating === null" @change="minRating = null; handleSearch()" class="w-4 h-4 text-primary-500 border-slate-300 focus:ring-primary-500">
                <span>不限</span>
              </label>
            </div>
          </div>

          <!-- Resource Type Filter -->
          <div class="mb-4">
            <h4 class="text-xs font-medium text-slate-500 mb-2">资源类型</h4>
            <div class="space-y-1.5">
              <label class="flex items-center gap-2 text-sm cursor-pointer">
                <input type="checkbox" value="笔记" v-model="selectedTypes" @change="handleSearch()" class="w-4 h-4 text-primary-500 rounded border-slate-300 focus:ring-primary-500">
                <span>笔记</span>
              </label>
              <label class="flex items-center gap-2 text-sm cursor-pointer">
                <input type="checkbox" value="视频" v-model="selectedTypes" @change="handleSearch()" class="w-4 h-4 text-primary-500 rounded border-slate-300 focus:ring-primary-500">
                <span>视频</span>
              </label>
              <label class="flex items-center gap-2 text-sm cursor-pointer">
                <input type="checkbox" value="电子书" v-model="selectedTypes" @change="handleSearch()" class="w-4 h-4 text-primary-500 rounded border-slate-300 focus:ring-primary-500">
                <span>电子书</span>
              </label>
            </div>
          </div>

          <button @click="selectedCategory = null; minRating = null; selectedTypes = []; sortBy = 'hot'; handleSearch()" class="w-full text-xs text-primary-500 border border-primary-300 py-1.5 rounded-lg hover:bg-primary-50">清除所有筛选</button>
        </div>
      </aside>

      <!-- Results -->
      <div class="flex-1">
        <div class="flex items-center justify-between mb-4">
          <p class="text-sm text-slate-500">找到 <span class="font-semibold text-slate-800">{{ total }}</span> 个结果</p>
          <div class="flex items-center gap-2">
            <span class="text-xs text-slate-500">排序：</span>
            <button
              @click="sortBy = 'rating'; handleSearch()"
              :class="['text-xs px-3 py-1 rounded-full transition-colors', sortBy === 'rating' ? 'bg-primary-500 text-white' : 'bg-slate-100 text-slate-600 hover:bg-slate-200']"
            >
              评分最高
            </button>
            <button
              @click="sortBy = 'latest'; handleSearch()"
              :class="['text-xs px-3 py-1 rounded-full transition-colors', sortBy === 'latest' ? 'bg-primary-500 text-white' : 'bg-slate-100 text-slate-600 hover:bg-slate-200']"
            >
              最新
            </button>
            <button
              @click="sortBy = 'hot'; handleSearch()"
              :class="['text-xs px-3 py-1 rounded-full transition-colors', sortBy === 'hot' ? 'bg-primary-500 text-white' : 'bg-slate-100 text-slate-600 hover:bg-slate-200']"
            >
              最热
            </button>
          </div>
        </div>

        <div v-if="loading" class="flex justify-center py-20">
          <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-primary-600"></div>
        </div>

        <div v-else-if="results.length === 0" class="text-center py-20 text-slate-400">
          <svg class="w-16 h-16 mx-auto mb-4 text-slate-300" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/></svg>
          <p class="text-lg mb-2">暂无搜索结果</p>
          <p v-if="selectedCategory || keyword" class="text-sm mb-4">
            当前筛选：
            <span v-if="keyword" class="font-medium text-slate-500">"{{ keyword }}"</span>
            <span v-if="keyword && selectedCategory"> + </span>
            <span v-if="selectedCategory" class="font-medium text-slate-500">{{ categories.flatMap(c => c.children || [c]).find(c => c.id === selectedCategory)?.name || '该分类' }}</span>
          </p>
          <button
            @click="keyword = ''; selectedCategory = null; minRating = null; sortBy = 'hot'; handleSearch()"
            class="px-4 py-2 text-sm text-primary-600 border border-primary-300 rounded-lg hover:bg-primary-50 transition-colors"
          >
            清除所有筛选
          </button>
        </div>

        <div v-else class="space-y-3">
          <div
            v-for="res in results"
            :key="res.id"
            class="bg-white rounded-xl shadow-sm border border-slate-200 p-4 hover:shadow-md transition-shadow cursor-pointer"
            @click="goToResource(res.id)"
          >
            <div class="flex items-start gap-4">
              <div class="w-20 h-20 rounded-lg bg-gradient-to-br from-primary-100 to-primary-200 flex items-center justify-center flex-shrink-0">
                <svg class="w-10 h-10 text-primary-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/>
                </svg>
              </div>
              <div class="flex-1 min-w-0">
                <div class="flex items-start justify-between">
                  <h3 class="font-medium text-base">{{ res.title }}</h3>
                  <div class="flex items-center gap-1 flex-shrink-0">
                    <svg class="w-4 h-4 text-amber-400" fill="currentColor" viewBox="0 0 24 24"><path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/></svg>
                    <span class="text-sm font-semibold">{{ res.avgRating?.toFixed(1) }}</span>
                    <span class="text-xs text-slate-400">({{ res.ratingCount }})</span>
                  </div>
                </div>
                <p class="text-sm text-slate-500 mt-1 line-clamp-2">{{ res.aiSummary || res.description }}</p>
                <div class="flex items-center gap-2 mt-2">
                  <span v-for="tag in res.tags?.slice(0, 3)" :key="tag.id" class="text-xs bg-blue-100 text-blue-600 px-1.5 py-0.5 rounded">
                    {{ tag.name }}
                  </span>
                </div>
                <div class="flex items-center gap-4 mt-2 text-xs text-slate-400">
                  <span class="flex items-center gap-1">
                    <div class="w-4 h-4 bg-primary-100 rounded-full flex items-center justify-center"><span class="text-[8px] text-primary-600">{{ res.publisher?.nickname?.[0] || '用' }}</span></div>
                    {{ res.publisher?.nickname }}
                  </span>
                  <span>{{ res.createdAt?.split('T')[0] }}</span>
                  <span class="flex items-center gap-0.5">
                    <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"/></svg>
                    {{ res.viewCount }}
                  </span>
                  <span class="flex items-center gap-0.5">
                    <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z"/></svg>
                    {{ res.likeCount }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
