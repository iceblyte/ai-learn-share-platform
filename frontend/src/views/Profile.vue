<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useUserStore } from '@/store/user'
import { userApi } from '@/api/user'
import { resourceApi } from '@/api/resource'
import type { Resource } from '@/types'
import { useRouter } from 'vue-router'
import AppModal from '@/components/AppModal.vue'

const userStore = useUserStore()
const router = useRouter()
const activeTab = ref('published')
const favorites = ref<Resource[]>([])
const myResources = ref<Resource[]>([])
const stats = ref({ publishedCount: 0, totalViews: 0, totalLikes: 0, avgRating: 0, totalFavorites: 0 })
const loading = ref(false)

const profileForm = ref({
  nickname: '',
  bio: '',
})
const editMode = ref(false)
const avatarFile = ref<File | null>(null)
const avatarPreview = ref('')
const showDeleteModal = ref(false)
const deleteTargetId = ref<number | null>(null)

function handleAvatarSelect(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  avatarFile.value = file
  avatarPreview.value = URL.createObjectURL(file)
}

async function uploadAvatar() {
  if (!avatarFile.value) return
  const formData = new FormData()
  formData.append('file', avatarFile.value)
  try {
    await userApi.uploadAvatar(formData)
    await userStore.fetchUserInfo()
    avatarFile.value = null
  } catch {}
}

function confirmDelete(id: number) {
  deleteTargetId.value = id
  showDeleteModal.value = true
}

async function executeDelete() {
  if (!deleteTargetId.value) return
  try {
    await resourceApi.delete(deleteTargetId.value)
    myResources.value = myResources.value.filter(r => r.id !== deleteTargetId.value)
  } catch {}
  showDeleteModal.value = false
  deleteTargetId.value = null
}

function editResource(id: number) {
  router.push(`/publish?edit=${id}`)
}

onMounted(async () => {
  if (!userStore.isLoggedIn) {
    router.push('/login')
    return
  }
  await userStore.fetchUserInfo()
  profileForm.value.nickname = userStore.userInfo?.nickname || ''
  profileForm.value.bio = userStore.userInfo?.bio || ''
  loadMyResources()
  loadStats()
})

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

async function loadStats() {
  try {
    const res = await userApi.getStatistics()
    stats.value = res.data.data
  } catch {}
}

async function updateProfile() {
  try {
    await userApi.updateProfile({
      nickname: profileForm.value.nickname,
      bio: profileForm.value.bio,
    })
    await userStore.fetchUserInfo()
    editMode.value = false
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

const gradients = [
  'from-blue-400 to-blue-600',
  'from-green-400 to-emerald-600',
  'from-purple-400 to-violet-600',
  'from-amber-400 to-orange-600',
  'from-red-400 to-rose-600',
]

function getGradient(index: number) {
  return gradients[index % gradients.length]
}
</script>

<template>
  <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8" v-if="userStore.userInfo">
    <div class="flex gap-8">
      <!-- Profile Sidebar -->
      <aside class="w-72 flex-shrink-0">
        <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-6 sticky top-24">
          <!-- Avatar -->
          <div class="text-center">
            <div class="w-20 h-20 bg-primary-100 rounded-full flex items-center justify-center mx-auto overflow-hidden">
              <img v-if="userStore.userInfo.avatar" :src="userStore.userInfo.avatar" class="w-full h-full object-cover" />
              <span v-else class="text-2xl font-bold text-primary-600">
                {{ userStore.userInfo.nickname?.[0] || userStore.userInfo.username[0] }}
              </span>
            </div>
            <h2 class="text-lg font-bold mt-3">{{ userStore.userInfo.nickname || userStore.userInfo.username }}</h2>
            <p class="text-sm text-slate-500">@{{ userStore.userInfo.username }}</p>
            <span class="inline-block text-xs bg-primary-100 text-primary-600 px-2 py-0.5 rounded-full mt-2">
              {{ userStore.userInfo.role === 'ADMIN' ? '管理员' : '资源发布者' }}
            </span>
          </div>
          <p v-if="userStore.userInfo.bio" class="text-sm text-slate-600 text-center mt-3">
            {{ userStore.userInfo.bio }}
          </p>

          <!-- Stats -->
          <div class="grid grid-cols-3 gap-2 mt-4 pt-4 border-t border-slate-100">
            <div class="text-center">
              <p class="text-lg font-bold text-slate-800">{{ stats.publishedCount }}</p>
              <p class="text-xs text-slate-400">发布</p>
            </div>
            <div class="text-center">
              <p class="text-lg font-bold text-slate-800">{{ stats.totalLikes }}</p>
              <p class="text-xs text-slate-400">获赞</p>
            </div>
            <div class="text-center">
              <p class="text-lg font-bold text-slate-800">{{ stats.totalFavorites }}</p>
              <p class="text-xs text-slate-400">收藏</p>
            </div>
          </div>

          <!-- Navigation -->
          <nav class="mt-4 pt-4 border-t border-slate-100 space-y-1">
            <button
              @click="editMode = false; loadMyResources()"
              :class="[
                'flex items-center gap-3 px-3 py-2 rounded-lg text-sm w-full transition-colors',
                activeTab === 'published' && !editMode
                  ? 'bg-primary-50 text-primary-600 font-medium'
                  : 'text-slate-600 hover:bg-slate-50'
              ]"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/>
              </svg>
              我的发布
            </button>
            <button
              @click="editMode = false; loadFavorites()"
              :class="[
                'flex items-center gap-3 px-3 py-2 rounded-lg text-sm w-full transition-colors',
                activeTab === 'favorites' && !editMode
                  ? 'bg-primary-50 text-primary-600 font-medium'
                  : 'text-slate-600 hover:bg-slate-50'
              ]"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 5a2 2 0 012-2h10a2 2 0 012 2v16l-7-3.5L5 21V5z"/>
              </svg>
              我的收藏
            </button>
            <button
              @click="activeTab = 'stats'; editMode = false"
              :class="[
                'flex items-center gap-3 px-3 py-2 rounded-lg text-sm w-full transition-colors',
                activeTab === 'stats' && !editMode
                  ? 'bg-primary-50 text-primary-600 font-medium'
                  : 'text-slate-600 hover:bg-slate-50'
              ]"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"/>
              </svg>
              学习统计
            </button>
            <button
              @click="editMode = true"
              :class="[
                'flex items-center gap-3 px-3 py-2 rounded-lg text-sm w-full transition-colors',
                editMode
                  ? 'bg-primary-50 text-primary-600 font-medium'
                  : 'text-slate-600 hover:bg-slate-50'
              ]"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"/>
              </svg>
              编辑资料
            </button>
          </nav>
        </div>
      </aside>

      <!-- Main Content -->
      <div class="flex-1">
        <!-- Tabs -->
        <div class="flex items-center gap-1 mb-6 bg-white rounded-xl shadow-sm border border-slate-200 p-1">
          <button
            @click="editMode = false; loadMyResources()"
            :class="[
              'px-4 py-2 rounded-lg text-sm font-medium transition-colors',
              activeTab === 'published' && !editMode
                ? 'bg-primary-500 text-white'
                : 'text-slate-600 hover:bg-slate-50'
            ]"
          >
            我的发布
          </button>
          <button
            @click="editMode = false; loadFavorites()"
            :class="[
              'px-4 py-2 rounded-lg text-sm font-medium transition-colors',
              activeTab === 'favorites' && !editMode
                ? 'bg-primary-500 text-white'
                : 'text-slate-600 hover:bg-slate-50'
            ]"
          >
            我的收藏
          </button>
          <button
            @click="activeTab = 'stats'; editMode = false"
            :class="[
              'px-4 py-2 rounded-lg text-sm font-medium transition-colors',
              activeTab === 'stats' && !editMode
                ? 'bg-primary-500 text-white'
                : 'text-slate-600 hover:bg-slate-50'
            ]"
          >
            学习统计
          </button>
          <button
            @click="editMode = true"
            :class="[
              'px-4 py-2 rounded-lg text-sm font-medium transition-colors',
              editMode
                ? 'bg-primary-500 text-white'
                : 'text-slate-600 hover:bg-slate-50'
            ]"
          >
            编辑资料
          </button>
        </div>

        <!-- Loading -->
        <div v-if="loading" class="flex justify-center py-12">
          <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600"></div>
        </div>

        <!-- My Resources List -->
        <div v-else-if="activeTab === 'published' && !editMode" class="space-y-3">
          <div v-if="myResources.length === 0" class="text-center py-12 text-slate-400">
            <p class="text-lg mb-2">暂无发布的资源</p>
            <p class="text-sm">点击"发布资源"分享你的学习资料</p>
          </div>
          <div
            v-for="(res, index) in myResources"
            :key="res.id"
            class="bg-white rounded-xl shadow-sm border border-slate-200 p-4 hover:shadow-md transition-shadow cursor-pointer"
            @click="goToResource(res.id)"
          >
            <div class="flex items-start gap-4">
              <div :class="['w-16 h-16 bg-gradient-to-br rounded-lg flex items-center justify-center flex-shrink-0', getGradient(index)]">
                <svg class="w-8 h-8 text-white opacity-80" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/>
                </svg>
              </div>
              <div class="flex-1 min-w-0">
                <div class="flex items-start justify-between">
                  <h3 class="font-medium">{{ res.title }}</h3>
                  <span :class="['text-xs px-2 py-0.5 rounded-full flex-shrink-0', getStatusClass(res.status)]">
                    {{ getStatusLabel(res.status) }}
                  </span>
                </div>
                <p class="text-xs text-slate-500 mt-1">发布于 {{ res.createdAt?.split('T')[0] }}</p>
                <div class="flex items-center gap-4 mt-2 text-xs text-slate-400">
                  <span class="flex items-center gap-0.5">
                    <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/>
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"/>
                    </svg>
                    {{ res.viewCount }}
                  </span>
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
                    {{ res.avgRating?.toFixed(1) || '-' }}
                  </span>
                </div>
              </div>
              <div class="flex items-center gap-2 flex-shrink-0">
                <button
                  @click.stop="editResource(res.id)"
                  class="p-1.5 text-slate-400 hover:text-primary-500 hover:bg-primary-50 rounded"
                >
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"/></svg>
                </button>
                <button
                  @click.stop="confirmDelete(res.id)"
                  class="p-1.5 text-slate-400 hover:text-red-500 hover:bg-red-50 rounded"
                >
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/></svg>
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- Favorites List -->
        <div v-else-if="activeTab === 'favorites' && !editMode" class="space-y-3">
          <div v-if="favorites.length === 0" class="text-center py-12 text-slate-400">
            <p class="text-lg mb-2">暂无收藏的资源</p>
            <p class="text-sm">浏览资源时点击收藏按钮即可添加</p>
          </div>
          <div
            v-for="(res, index) in favorites"
            :key="res.id"
            class="bg-white rounded-xl shadow-sm border border-slate-200 p-4 hover:shadow-md transition-shadow cursor-pointer"
            @click="goToResource(res.id)"
          >
            <div class="flex items-start gap-4">
              <div :class="['w-16 h-16 bg-gradient-to-br rounded-lg flex items-center justify-center flex-shrink-0', getGradient(index + 2)]">
                <svg class="w-8 h-8 text-white opacity-80" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"/>
                </svg>
              </div>
              <div class="flex-1 min-w-0">
                <div class="flex items-center gap-2 mb-1">
                  <span class="px-2 py-0.5 bg-primary-100 text-primary-700 text-xs rounded-full">{{ res.category?.name }}</span>
                </div>
                <h3 class="font-medium">{{ res.title }}</h3>
                <p class="text-xs text-slate-500 mt-1 line-clamp-2">{{ res.aiSummary || res.description }}</p>
                <div class="flex items-center gap-4 mt-2 text-xs text-slate-400">
                  <span>{{ res.publisher?.nickname }}</span>
                  <span class="flex items-center gap-0.5">
                    <svg class="w-3.5 h-3.5 text-amber-400" fill="currentColor" viewBox="0 0 24 24">
                      <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/>
                    </svg>
                    {{ res.avgRating?.toFixed(1) }}
                  </span>
                  <span class="flex items-center gap-0.5">
                    <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z"/>
                    </svg>
                    {{ res.likeCount }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Learning Stats -->
        <div v-else-if="activeTab === 'stats' && !editMode">
          <div class="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">
            <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-4 text-center">
              <p class="text-2xl font-bold text-primary-600">{{ stats.totalViews }}</p>
              <p class="text-sm text-slate-500">总浏览</p>
            </div>
            <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-4 text-center">
              <p class="text-2xl font-bold text-red-500">{{ stats.totalLikes }}</p>
              <p class="text-sm text-slate-500">总点赞</p>
            </div>
            <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-4 text-center">
              <p class="text-2xl font-bold text-amber-500">{{ stats.avgRating.toFixed(1) }}</p>
              <p class="text-sm text-slate-500">平均评分</p>
            </div>
            <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-4 text-center">
              <p class="text-2xl font-bold text-green-500">{{ stats.totalFavorites }}</p>
              <p class="text-sm text-slate-500">被收藏</p>
            </div>
          </div>

          <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-6">
            <h3 class="text-lg font-semibold text-slate-800 mb-4">学习数据概览</h3>
            <div class="space-y-4">
              <div class="flex items-center justify-between py-3 border-b border-slate-100">
                <span class="text-slate-600">发布资源数</span>
                <span class="font-semibold text-slate-800">{{ stats.publishedCount }}</span>
              </div>
              <div class="flex items-center justify-between py-3 border-b border-slate-100">
                <span class="text-slate-600">总浏览量</span>
                <span class="font-semibold text-slate-800">{{ stats.totalViews }}</span>
              </div>
              <div class="flex items-center justify-between py-3 border-b border-slate-100">
                <span class="text-slate-600">总获赞数</span>
                <span class="font-semibold text-slate-800">{{ stats.totalLikes }}</span>
              </div>
              <div class="flex items-center justify-between py-3 border-b border-slate-100">
                <span class="text-slate-600">平均评分</span>
                <span class="font-semibold text-slate-800">{{ stats.avgRating.toFixed(1) }}</span>
              </div>
              <div class="flex items-center justify-between py-3">
                <span class="text-slate-600">被收藏数</span>
                <span class="font-semibold text-slate-800">{{ stats.totalFavorites }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Edit Profile -->
        <div v-else-if="editMode">
          <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-6">
            <h3 class="text-lg font-semibold text-slate-800 mb-6">编辑个人资料</h3>
            <div class="space-y-4">
              <!-- Avatar Upload -->
              <div>
                <label class="block text-sm font-medium text-slate-700 mb-2">头像</label>
                <div class="flex items-center gap-4">
                  <div class="w-20 h-20 rounded-full bg-primary-100 flex items-center justify-center overflow-hidden flex-shrink-0">
                    <img v-if="avatarPreview" :src="avatarPreview" class="w-full h-full object-cover" />
                    <img v-else-if="userStore.userInfo.avatar" :src="userStore.userInfo.avatar" class="w-full h-full object-cover" />
                    <span v-else class="text-2xl font-bold text-primary-600">
                      {{ userStore.userInfo.nickname?.[0] || userStore.userInfo.username[0] }}
                    </span>
                  </div>
                  <div>
                    <label class="cursor-pointer bg-white border border-slate-300 text-slate-600 px-4 py-2 rounded-lg text-sm hover:bg-slate-50 transition-colors inline-block">
                      选择图片
                      <input type="file" accept="image/*" class="hidden" @change="handleAvatarSelect" />
                    </label>
                    <button
                      v-if="avatarFile"
                      @click="uploadAvatar"
                      class="ml-2 bg-primary-500 text-white px-4 py-2 rounded-lg text-sm hover:bg-primary-600 transition-colors"
                    >
                      上传
                    </button>
                    <p class="text-xs text-slate-400 mt-1">支持 JPG、PNG，建议 200x200</p>
                  </div>
                </div>
              </div>
              <div>
                <label class="block text-sm font-medium text-slate-700 mb-1">昵称</label>
                <input
                  v-model="profileForm.nickname"
                  type="text"
                  class="w-full border border-slate-300 rounded-lg px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                  placeholder="请输入昵称"
                />
              </div>
              <div>
                <label class="block text-sm font-medium text-slate-700 mb-1">个人简介</label>
                <textarea
                  v-model="profileForm.bio"
                  class="w-full border border-slate-300 rounded-lg px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent h-24 resize-none"
                  placeholder="介绍一下自己..."
                ></textarea>
              </div>
              <div>
                <label class="block text-sm font-medium text-slate-700 mb-1">用户名</label>
                <input
                  type="text"
                  :value="userStore.userInfo.username"
                  disabled
                  class="w-full border border-slate-200 rounded-lg px-4 py-2.5 text-sm bg-slate-50 text-slate-500"
                />
                <p class="text-xs text-slate-400 mt-1">用户名不可修改</p>
              </div>
              <div class="flex gap-3 pt-4">
                <button
                  @click="updateProfile"
                  class="bg-primary-500 text-white px-6 py-2.5 rounded-lg text-sm font-medium hover:bg-primary-600 transition-colors"
                >
                  保存修改
                </button>
                <button
                  @click="editMode = false"
                  class="bg-slate-100 text-slate-600 px-6 py-2.5 rounded-lg text-sm font-medium hover:bg-slate-200 transition-colors"
                >
                  取消
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Delete Confirmation Modal -->
    <AppModal :visible="showDeleteModal" title="确认删除" @close="showDeleteModal = false" @confirm="executeDelete">
      <template #body>
        <p class="text-sm text-slate-600">确定要删除这个资源吗？此操作不可撤销。</p>
      </template>
      <template #footer>
        <button @click="showDeleteModal = false" class="px-4 py-2 text-sm text-slate-600 border border-slate-300 rounded-lg hover:bg-slate-50">取消</button>
        <button @click="executeDelete" class="px-4 py-2 text-sm text-white bg-red-500 rounded-lg hover:bg-red-600">确认删除</button>
      </template>
    </AppModal>
  </div>
</template>
