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

    // Try loading recommendations (requires auth)
    try {
      const recRes = await aiApi.getRecommendations()
      recommendations.value = recRes.data.data.records
    } catch {}
  } finally {
    loading.value = false
  }
})

function goToResource(id: number) {
  router.push(`/resource/${id}`)
}

function goToCategory(id: number) {
  router.push({ name: 'Search', query: { categoryId: id } })
}
</script>

<template>
  <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
    <!-- Hero Section -->
    <section class="text-center py-12 mb-8">
      <h1 class="text-4xl font-bold text-slate-800 mb-4">AI 个性化学习资源平台</h1>
      <p class="text-lg text-slate-600 mb-6">发现、分享、智能推荐优质学习资源</p>
      <div class="flex justify-center gap-4">
        <router-link to="/search" class="btn-primary px-8 py-3 text-lg">开始探索</router-link>
        <router-link to="/register" class="btn-secondary px-8 py-3 text-lg">加入社区</router-link>
      </div>
    </section>

    <!-- Categories -->
    <section class="mb-12">
      <h2 class="text-2xl font-semibold text-slate-800 mb-6">资源分类</h2>
      <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-5 gap-4">
        <button
          v-for="cat in categories"
          :key="cat.id"
          @click="goToCategory(cat.id)"
          class="card p-4 text-center hover:bg-primary-50 cursor-pointer"
        >
          <div class="text-2xl mb-2">
            {{ cat.name === '计算机科学' ? '💻' : cat.name === '数学' ? '📐' : cat.name === '语言学习' ? '🌍' : cat.name === '专业课' ? '📚' : '📝' }}
          </div>
          <p class="text-sm font-medium text-slate-700">{{ cat.name }}</p>
        </button>
      </div>
    </section>

    <!-- AI Recommendations -->
    <section v-if="recommendations.length > 0" class="mb-12">
      <h2 class="text-2xl font-semibold text-slate-800 mb-2">为你推荐</h2>
      <p class="text-sm text-slate-500 mb-6">基于你的学习兴趣，AI 为你精选</p>
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        <div
          v-for="rec in recommendations"
          :key="rec.resource.id"
          class="card p-5 cursor-pointer"
          @click="goToResource(rec.resource.id)"
        >
          <h3 class="font-semibold text-slate-800 mb-2 line-clamp-1">{{ rec.resource.title }}</h3>
          <p class="text-sm text-slate-500 mb-3 line-clamp-2">{{ rec.resource.aiSummary || rec.resource.description }}</p>
          <div class="bg-primary-50 rounded-lg px-3 py-2 mb-3">
            <p class="text-xs text-primary-700">
              <span class="font-medium">AI 推荐理由：</span>{{ rec.recommendReason }}
            </p>
          </div>
          <div class="flex items-center justify-between text-xs text-slate-400">
            <span>{{ rec.resource.publisher?.nickname }}</span>
            <span>{{ rec.resource.avgRating?.toFixed(1) }} 分</span>
          </div>
        </div>
      </div>
    </section>

    <!-- Hot Resources -->
    <section class="mb-12">
      <h2 class="text-2xl font-semibold text-slate-800 mb-6">热门资源</h2>
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        <div
          v-for="res in hotResources"
          :key="res.id"
          class="card p-5 cursor-pointer"
          @click="goToResource(res.id)"
        >
          <div class="flex items-start justify-between mb-3">
            <span class="inline-block px-2 py-1 bg-primary-100 text-primary-700 text-xs rounded-full">
              {{ res.category?.name }}
            </span>
            <span class="text-xs text-slate-400">🔥 {{ res.hotScore }}</span>
          </div>
          <h3 class="font-semibold text-slate-800 mb-2 line-clamp-1">{{ res.title }}</h3>
          <p class="text-sm text-slate-500 mb-3 line-clamp-2">{{ res.aiSummary || res.description }}</p>
          <div class="flex flex-wrap gap-1 mb-3">
            <span
              v-for="tag in res.tags?.slice(0, 3)"
              :key="tag.id"
              class="px-2 py-0.5 bg-slate-100 text-slate-600 text-xs rounded"
            >
              {{ tag.name }}
            </span>
          </div>
          <div class="flex items-center justify-between text-xs text-slate-400">
            <div class="flex items-center space-x-3">
              <span>👁 {{ res.viewCount }}</span>
              <span>❤️ {{ res.likeCount }}</span>
              <span>⭐ {{ res.avgRating?.toFixed(1) }}</span>
            </div>
            <span>{{ res.publisher?.nickname }}</span>
          </div>
        </div>
      </div>
    </section>

    <!-- Latest Resources -->
    <section>
      <h2 class="text-2xl font-semibold text-slate-800 mb-6">最新发布</h2>
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        <div
          v-for="res in latestResources"
          :key="res.id"
          class="card p-5 cursor-pointer"
          @click="goToResource(res.id)"
        >
          <div class="flex items-start justify-between mb-3">
            <span class="inline-block px-2 py-1 bg-emerald-100 text-emerald-700 text-xs rounded-full">
              {{ res.category?.name }}
            </span>
          </div>
          <h3 class="font-semibold text-slate-800 mb-2 line-clamp-1">{{ res.title }}</h3>
          <p class="text-sm text-slate-500 mb-3 line-clamp-2">{{ res.aiSummary || res.description }}</p>
          <div class="flex items-center justify-between text-xs text-slate-400">
            <span>{{ res.publisher?.nickname }}</span>
            <span>{{ new Date(res.createdAt).toLocaleDateString() }}</span>
          </div>
        </div>
      </div>
    </section>

    <!-- Loading -->
    <div v-if="loading" class="flex justify-center py-20">
      <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600"></div>
    </div>
  </div>
</template>
