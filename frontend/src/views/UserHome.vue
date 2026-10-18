<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { userApi } from '@/api/user'
import { useUserStore } from '@/store/user'
import FollowUserList from '@/components/FollowUserList.vue'
import type { PublicUserProfile, Resource } from '@/types'

/** 用户公开主页：资料 + TA 的资源 / 粉丝 / 关注 三个 tab */
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const userId = computed(() => Number(route.params.id))
const profile = ref<PublicUserProfile | null>(null)
const loading = ref(true)
const notFound = ref(false)
const followSubmitting = ref(false)

const activeTab = ref<'resources' | 'followers' | 'following'>('resources')
const tabQuery = computed(() => route.query.tab as string | undefined)

// TA 的资源分页
const resources = ref<Resource[]>([])
const resPage = ref(1)
const resSize = 10
const resTotal = ref(0)
const resLoading = ref(false)

async function loadProfile() {
  loading.value = true
  notFound.value = false
  try {
    const res = await userApi.getPublicProfile(userId.value)
    profile.value = res.data.data
  } catch {
    notFound.value = true
  } finally {
    loading.value = false
  }
}

async function loadResources() {
  resLoading.value = true
  try {
    const res = await userApi.getUserPublishedResources(userId.value, resPage.value, resSize)
    resources.value = res.data.data.records
    resTotal.value = res.data.data.total
  } catch {} finally {
    resLoading.value = false
  }
}

function resGoTo(p: number) {
  const tp = Math.max(1, Math.ceil(resTotal.value / resSize))
  if (p < 1 || p > tp || p === resPage.value) return
  resPage.value = p
  loadResources()
}

async function toggleFollow() {
  if (!userStore.isLoggedIn) {
    router.push({ name: 'Login', query: { redirect: route.fullPath } })
    return
  }
  if (!profile.value || profile.value.isSelf) return
  followSubmitting.value = true
  try {
    const res = await userApi.toggleFollow(profile.value.id)
    profile.value.isFollowing = res.data.data.following
    profile.value.followers = res.data.data.followers
  } catch {} finally {
    followSubmitting.value = false
  }
}

function goResource(id: number) {
  router.push(`/resource/${id}`)
}

function switchTab(tab: 'resources' | 'followers' | 'following') {
  activeTab.value = tab
  router.replace({ query: { ...route.query, tab } })
}

onMounted(() => {
  if (tabQuery.value === 'followers' || tabQuery.value === 'following') {
    activeTab.value = tabQuery.value
  }
  loadProfile().then(() => {
    if (!notFound.value) loadResources()
  })
})

watch(userId, () => {
  loadProfile().then(() => {
    resPage.value = 1
    if (!notFound.value) loadResources()
  })
})

function formatDate(s?: string) {
  if (!s) return ''
  return new Date(s).toLocaleDateString('zh-CN', { year: 'numeric', month: 'long' })
}

function getRoleLabel(role?: string) {
  const map: Record<string, string> = { USER: '普通用户', PUBLISHER: '发布者', ADMIN: '管理员' }
  return map[role || ''] || role || ''
}
</script>

<template>
  <div class="min-h-screen bg-slate-50">
    <div v-if="loading" class="flex justify-center py-24">
      <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600"></div>
    </div>

    <div v-else-if="notFound || !profile" class="text-center py-24">
      <p class="text-lg text-slate-500 mb-2">用户不存在或已注销</p>
      <button @click="router.push('/')" class="text-primary-600 hover:underline text-sm">返回首页</button>
    </div>

    <div v-else class="max-w-4xl mx-auto px-4 py-8">
      <!-- Profile header -->
      <div class="bg-white rounded-2xl shadow-sm border border-slate-200 p-6">
        <div class="flex items-start gap-5">
          <div class="w-20 h-20 rounded-full flex items-center justify-center overflow-hidden flex-shrink-0">
            <img v-if="profile.avatarUrl" :src="profile.avatarUrl" class="w-full h-full object-cover" />
            <div v-else class="w-full h-full bg-primary-100 flex items-center justify-center">
              <span class="text-2xl font-bold text-primary-600">{{ profile.nickname?.[0] || '用' }}</span>
            </div>
          </div>
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-2 flex-wrap">
              <h1 class="text-xl font-bold text-slate-800">{{ profile.nickname || '已注销用户' }}</h1>
              <span v-if="profile.role && profile.role !== 'USER'"
                :class="['text-xs px-2 py-0.5 rounded-full', profile.role === 'ADMIN' ? 'bg-red-100 text-red-700' : 'bg-blue-100 text-blue-700']">
                {{ getRoleLabel(profile.role) }}
              </span>
            </div>
            <p class="text-sm text-slate-500 mt-1">@{{ profile.username }}</p>
            <p class="text-sm text-slate-600 mt-2">{{ profile.bio || '这个人很懒，什么都没写' }}</p>
            <p class="text-xs text-slate-400 mt-2">{{ formatDate(profile.createdAt) }} 加入</p>
          </div>
          <div class="flex-shrink-0">
            <button
              v-if="!profile.isSelf"
              @click="toggleFollow"
              :disabled="followSubmitting"
              :class="[
                'px-5 py-2 text-sm rounded-lg font-medium transition-colors disabled:opacity-50',
                profile.isFollowing
                  ? 'border border-slate-300 text-slate-600 hover:bg-slate-100'
                  : 'bg-primary-600 text-white hover:bg-primary-700',
              ]"
            >
              {{ profile.isFollowing ? '已关注' : '+ 关注' }}
            </button>
            <button
              v-else
              @click="router.push('/profile')"
              class="px-5 py-2 text-sm rounded-lg border border-slate-300 text-slate-600 hover:bg-slate-100"
            >
              编辑资料
            </button>
          </div>
        </div>
        <!-- Stats -->
        <div class="flex items-center gap-8 mt-5 pt-4 border-t border-slate-100 text-sm">
          <button @click="switchTab('resources')" :class="['hover:text-primary-600', activeTab === 'resources' ? 'text-primary-600' : 'text-slate-600']">
            <strong class="text-slate-800">{{ profile.publishedCount }}</strong> 资源
          </button>
          <button @click="switchTab('followers')" :class="['hover:text-primary-600', activeTab === 'followers' ? 'text-primary-600' : 'text-slate-600']">
            <strong class="text-slate-800">{{ profile.followers }}</strong> 粉丝
          </button>
          <button @click="switchTab('following')" :class="['hover:text-primary-600', activeTab === 'following' ? 'text-primary-600' : 'text-slate-600']">
            <strong class="text-slate-800">{{ profile.following }}</strong> 关注
          </button>
        </div>
      </div>

      <!-- Tabs -->
      <div class="flex items-center gap-2 mt-6 mb-4">
        <button
          v-for="t in [
            { key: 'resources', label: `TA 的资源 (${profile.publishedCount})` },
            { key: 'followers', label: `粉丝 (${profile.followers})` },
            { key: 'following', label: `关注 (${profile.following})` },
          ]"
          :key="t.key"
          @click="switchTab(t.key as any)"
          :class="['px-4 py-1.5 rounded-lg text-sm font-medium transition-colors',
            activeTab === t.key ? 'bg-primary-600 text-white' : 'bg-white text-slate-600 border border-slate-200 hover:bg-slate-50']"
        >
          {{ t.label }}
        </button>
      </div>

      <!-- Tab content -->
      <div v-if="activeTab === 'followers' || activeTab === 'following'">
        <FollowUserList :user-id="profile.id" :mode="activeTab" />
      </div>

      <div v-else>
        <div v-if="resLoading" class="flex justify-center py-10">
          <div class="animate-spin rounded-full h-7 w-7 border-b-2 border-primary-600"></div>
        </div>
        <div v-else-if="resources.length === 0" class="text-center py-12 bg-white rounded-xl border border-slate-200">
          <p class="text-sm text-slate-400">还没有发布资源</p>
        </div>
        <div v-else class="space-y-3">
          <div
            v-for="res in resources"
            :key="res.id"
            @click="goResource(res.id)"
            class="bg-white rounded-xl shadow-sm border border-slate-200 p-5 hover:shadow-md transition-shadow cursor-pointer"
          >
            <div class="flex items-center gap-2">
              <h3 class="font-semibold text-slate-800">{{ res.title }}</h3>
              <span v-if="res.category" class="px-2 py-0.5 bg-primary-50 text-primary-600 rounded text-xs">{{ res.category.name }}</span>
            </div>
            <p class="text-sm text-slate-500 mt-1 line-clamp-2">{{ res.aiSummary || res.description }}</p>
            <div class="flex items-center gap-4 mt-2 text-xs text-slate-400">
              <span>👁 {{ res.viewCount }}</span>
              <span>👍 {{ res.likeCount }}</span>
              <span>⭐ {{ res.favoriteCount }}</span>
              <span>💬 {{ res.commentCount }}</span>
              <span>{{ res.createdAt?.split('T')[0] }}</span>
            </div>
          </div>
        </div>
        <div v-if="resTotal > resSize" class="flex items-center justify-center gap-3 mt-4">
          <button
            @click="resGoTo(resPage - 1)"
            :disabled="resPage <= 1"
            class="px-3 py-1.5 text-sm border border-slate-300 rounded-lg bg-white hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed"
          >上一页</button>
          <span class="text-sm text-slate-500">{{ resPage }} / {{ Math.max(1, Math.ceil(resTotal / resSize)) }}</span>
          <button
            @click="resGoTo(resPage + 1)"
            :disabled="resPage >= Math.ceil(resTotal / resSize)"
            class="px-3 py-1.5 text-sm border border-slate-300 rounded-lg bg-white hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed"
          >下一页</button>
        </div>
      </div>
    </div>
  </div>
</template>
