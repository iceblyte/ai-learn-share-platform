<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
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
const searchHistory = ref<string[]>([])

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
  try {
    if (isNlMode.value) {
      const res = await searchApi.nlSearch(keyword.value)
      const data = res.data.data as any
      results.value = data.results || []
      total.value = results.value.length
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
  } finally {
    loading.value = false
  }
  if (userStore.isLoggedIn) loadHistory()
}

function goToResource(id: number) {
  router.push(`/resource/${id}`)
}

watch(sortBy, () => { page.value = 1; handleSearch() })
</script>

<template>
  <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
    <!-- Search Bar -->
    <div class="mb-8">
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
        <input
          v-model="keyword"
          :placeholder="isNlMode ? '例如：推荐关于Java并发编程且评分最高的前5个资源' : '搜索资源...'"
          class="input-field flex-1"
          @keyup.enter="handleSearch"
        />
        <button @click="handleSearch" class="btn-primary px-6">搜索</button>
      </div>

      <!-- Search History -->
      <div v-if="searchHistory.length > 0" class="flex items-center gap-2 flex-wrap">
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

    <div class="flex gap-8">
      <!-- Sidebar Filters -->
      <aside class="w-64 flex-shrink-0 hidden lg:block">
        <div class="card p-4 mb-4">
          <h3 class="font-semibold text-slate-700 mb-3">分类筛选</h3>
          <div class="space-y-1">
            <button
              @click="selectedCategory = null; handleSearch()"
              :class="['w-full text-left px-3 py-2 rounded text-sm', selectedCategory === null ? 'bg-primary-50 text-primary-700' : 'text-slate-600 hover:bg-slate-50']"
            >
              全部分类
            </button>
            <button
              v-for="cat in categories"
              :key="cat.id"
              @click="selectedCategory = cat.id; handleSearch()"
              :class="['w-full text-left px-3 py-2 rounded text-sm', selectedCategory === cat.id ? 'bg-primary-50 text-primary-700' : 'text-slate-600 hover:bg-slate-50']"
            >
              {{ cat.name }}
            </button>
          </div>
        </div>

        <div class="card p-4">
          <h3 class="font-semibold text-slate-700 mb-3">热门标签</h3>
          <div class="flex flex-wrap gap-2">
            <button
              v-for="tag in hotTags.slice(0, 15)"
              :key="tag.id"
              @click="keyword = tag.name; handleSearch()"
              class="px-2 py-1 bg-slate-100 text-slate-600 text-xs rounded hover:bg-primary-100 hover:text-primary-700"
            >
              {{ tag.name }}
            </button>
          </div>
        </div>
      </aside>

      <!-- Results -->
      <div class="flex-1">
        <div class="flex items-center justify-between mb-4">
          <p class="text-sm text-slate-500">共找到 {{ total }} 个资源</p>
          <select v-model="sortBy" class="input-field w-auto text-sm">
            <option value="hot">最热</option>
            <option value="latest">最新</option>
            <option value="rating">评分最高</option>
          </select>
        </div>

        <div v-if="loading" class="flex justify-center py-20">
          <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-primary-600"></div>
        </div>

        <div v-else-if="results.length === 0" class="text-center py-20 text-slate-400">
          <p class="text-lg mb-2">暂无搜索结果</p>
          <p class="text-sm">试试其他关键词或浏览分类</p>
        </div>

        <div v-else class="space-y-4">
          <div
            v-for="res in results"
            :key="res.id"
            class="card p-5 cursor-pointer"
            @click="goToResource(res.id)"
          >
            <div class="flex items-start justify-between">
              <div class="flex-1">
                <div class="flex items-center gap-2 mb-2">
                  <span class="px-2 py-0.5 bg-primary-100 text-primary-700 text-xs rounded-full">{{ res.category?.name }}</span>
                  <span v-for="tag in res.tags?.slice(0, 3)" :key="tag.id" class="px-2 py-0.5 bg-slate-100 text-slate-600 text-xs rounded">
                    {{ tag.name }}
                  </span>
                </div>
                <h3 class="text-lg font-semibold text-slate-800 mb-2">{{ res.title }}</h3>
                <p class="text-sm text-slate-500 mb-3 line-clamp-2">{{ res.aiSummary || res.description }}</p>
                <div class="flex items-center space-x-4 text-xs text-slate-400">
                  <span>{{ res.publisher?.nickname }}</span>
                  <span>👁 {{ res.viewCount }}</span>
                  <span>❤️ {{ res.likeCount }}</span>
                  <span>⭐ {{ res.avgRating?.toFixed(1) }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
