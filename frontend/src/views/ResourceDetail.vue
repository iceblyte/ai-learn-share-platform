<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { marked } from 'marked'
import { resourceApi } from '@/api/resource'
import { commentApi } from '@/api/comment'
import { aiApi } from '@/api/ai'
import { tagApi } from '@/api/category'
import { useUserStore } from '@/store/user'
import AppToast from '@/components/AppToast.vue'
import type { Resource, Comment, Tag } from '@/types'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const id = Number(route.params.id)

const resource = ref<Resource | null>(null)
const comments = ref<Comment[]>([])
const newComment = ref('')
const replyTo = ref<number | null>(null)
const replyContent = ref('')
const myRating = ref(0)
const liked = ref(false)
const favorited = ref(false)
const hoverRating = ref(0)
const loading = ref(true)
const relatedResources = ref<Resource[]>([])
const hotTags = ref<Tag[]>([])
const toast = ref<InstanceType<typeof AppToast>>()

onMounted(async () => {
  try {
    const [resRes, comRes] = await Promise.all([
      resourceApi.getDetail(id),
      commentApi.getList(id),
    ])
    resource.value = resRes.data.data
    comments.value = comRes.data.data.records

    // Fetch user interaction status if logged in
    if (userStore.isLoggedIn) {
      try {
        const intRes = await resourceApi.getInteractions(id)
        liked.value = intRes.data.data.liked
        favorited.value = intRes.data.data.favorited
      } catch {}
    }

    // Load related recommendations and hot tags
    try {
      const [recRes, tagRes] = await Promise.all([
        aiApi.getRecommendations(),
        tagApi.getHot(),
      ])
      relatedResources.value = (recRes.data.data?.records || []).map((r: any) => r.resource).filter((r: Resource) => r.id !== id).slice(0, 4)
      hotTags.value = tagRes.data.data || []
    } catch {}
  } finally {
    loading.value = false
  }
})

function renderMarkdown(text: string): string {
  if (!text) return ''
  try {
    return marked.parse(text, { breaks: true }) as string
  } catch {
    return text
  }
}

async function handleLike() {
  if (!userStore.isLoggedIn) {
    toast.value?.show('请先登录后再操作', 'warning')
    return
  }
  const res = await resourceApi.like(id)
  liked.value = res.data.data.liked
  if (resource.value) {
    resource.value.likeCount = res.data.data.likeCount
  }
}

async function handleFavorite() {
  if (!userStore.isLoggedIn) {
    toast.value?.show('请先登录后再操作', 'warning')
    return
  }
  const res = await resourceApi.favorite(id)
  favorited.value = res.data.data.favorited
  if (resource.value) {
    resource.value.favoriteCount = res.data.data.favoriteCount
  }
}

async function handleRate(score: number) {
  if (!userStore.isLoggedIn) {
    toast.value?.show('请先登录后再评分', 'warning')
    return
  }
  myRating.value = score
  const res = await resourceApi.rate(id, score)
  if (resource.value) {
    resource.value.avgRating = res.data.data.avgRating
    resource.value.ratingCount = res.data.data.ratingCount
  }
  toast.value?.show('评分成功', 'success')
}

async function handleShare() {
  const url = window.location.href
  try {
    await navigator.clipboard.writeText(url)
    toast.value?.show('链接已复制到剪贴板', 'success')
  } catch {
    // Fallback for older browsers
    const input = document.createElement('input')
    input.value = url
    document.body.appendChild(input)
    input.select()
    document.execCommand('copy')
    document.body.removeChild(input)
    toast.value?.show('链接已复制到剪贴板', 'success')
  }
}

async function handleCommentLike(commentId: number) {
  if (!userStore.isLoggedIn) {
    toast.value?.show('请先登录后再操作', 'warning')
    return
  }
  const res = await commentApi.like(commentId)
  const comment = comments.value.find(c => c.id === commentId)
  if (comment) {
    comment.likeCount = res.data.data.likeCount
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

function goToCategory() {
  if (resource.value?.category?.id) {
    router.push({ name: 'Search', query: { categoryId: resource.value.category.id } })
  }
}

function getAvatarUrl(user: any): string | null {
  return user?.avatarUrl || user?.avatar || null
}
</script>

<template>
  <div v-if="resource">
    <AppToast ref="toast" />

    <!-- Breadcrumb -->
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-3">
      <nav class="flex items-center gap-2 text-sm text-slate-500">
        <router-link to="/" class="hover:text-primary-500">首页</router-link>
        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"/>
        </svg>
        <button @click="goToCategory" class="hover:text-primary-500">{{ resource.category?.name }}</button>
        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7"/>
        </svg>
        <span class="text-slate-800">{{ resource.title }}</span>
      </nav>
    </div>

    <main class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pb-12">
      <div class="flex gap-8">
        <!-- Main Content -->
        <div class="flex-1">
          <!-- Resource Header -->
          <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-6 mb-4">
            <div class="flex items-start justify-between">
              <div>
                <h1 class="text-2xl font-bold">{{ resource.title }}</h1>
                <div class="flex items-center gap-3 mt-3">
                  <span class="text-xs bg-blue-100 text-blue-600 px-2 py-0.5 rounded">{{ resource.category?.name }}</span>
                  <span
                    v-for="tag in resource.tags"
                    :key="tag.id"
                    class="text-xs bg-purple-100 text-purple-600 px-2 py-0.5 rounded"
                  >
                    {{ tag.name }}
                  </span>
                </div>
              </div>
              <div class="flex items-center gap-1 bg-amber-50 px-3 py-1.5 rounded-lg">
                <svg class="w-5 h-5 text-amber-400" fill="currentColor" viewBox="0 0 24 24">
                  <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/>
                </svg>
                <span class="text-lg font-bold text-amber-600">{{ resource.avgRating?.toFixed(1) }}</span>
                <span class="text-xs text-amber-500">({{ resource.ratingCount }} 评分)</span>
              </div>
            </div>

            <!-- Publisher Info -->
            <div class="flex items-center gap-3 mt-4 pt-4 border-t border-slate-100">
              <img
                v-if="getAvatarUrl(resource.publisher)"
                :src="getAvatarUrl(resource.publisher)!"
                class="w-10 h-10 rounded-full object-cover"
                alt="avatar"
              />
              <div v-else class="w-10 h-10 bg-primary-100 rounded-full flex items-center justify-center">
                <span class="text-sm font-medium text-primary-600">
                  {{ resource.publisher?.nickname?.[0] || '用' }}
                </span>
              </div>
              <div>
                <p class="text-sm font-medium">{{ resource.publisher?.nickname }}</p>
                <p class="text-xs text-slate-400">发布于 {{ new Date(resource.createdAt).toLocaleDateString() }}</p>
              </div>
              <div class="flex items-center gap-4 ml-auto text-sm text-slate-500">
                <span class="flex items-center gap-1">
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/>
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"/>
                  </svg>
                  {{ resource.viewCount }}
                </span>
                <span class="flex items-center gap-1">
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z"/>
                  </svg>
                  {{ resource.likeCount }}
                </span>
              </div>
            </div>
          </div>

          <!-- AI Summary -->
          <div v-if="resource.aiSummary" class="bg-gradient-to-r from-purple-50 to-primary-50 border border-purple-200 rounded-xl p-5 mb-4">
            <div class="flex items-start gap-3">
              <div class="w-8 h-8 bg-purple-500 rounded-lg flex items-center justify-center flex-shrink-0">
                <svg class="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"/>
                </svg>
              </div>
              <div>
                <h3 class="text-sm font-semibold text-purple-800">AI 智能摘要</h3>
                <p class="text-sm text-purple-700 mt-1">{{ resource.aiSummary }}</p>
              </div>
            </div>
          </div>

          <!-- Actions -->
          <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-4 mb-6">
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-3">
                <!-- Like -->
                <button
                  @click="handleLike"
                  :class="[
                    'flex items-center gap-1.5 px-4 py-2 rounded-lg border transition-colors',
                    liked
                      ? 'border-red-300 bg-red-50 text-red-600'
                      : 'border-slate-200 hover:border-red-300 hover:bg-red-50'
                  ]"
                >
                  <svg class="w-5 h-5" :class="liked ? 'text-red-500' : 'text-slate-400'" :fill="liked ? 'currentColor' : 'none'" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4.318 6.318a4.5 4.5 0 000 6.364L12 20.364l7.682-7.682a4.5 4.5 0 00-6.364-6.364L12 7.636l-1.318-1.318a4.5 4.5 0 00-6.364 0z"/></svg>
                  <span class="text-sm" :class="liked ? 'text-red-600' : 'text-slate-600'">{{ resource.likeCount }}</span>
                </button>
                <!-- Favorite -->
                <button
                  @click="handleFavorite"
                  :class="[
                    'flex items-center gap-1.5 px-4 py-2 rounded-lg border transition-colors',
                    favorited
                      ? 'border-amber-300 bg-amber-50 text-amber-600'
                      : 'border-slate-200 hover:border-amber-300 hover:bg-amber-50'
                  ]"
                >
                  <svg class="w-5 h-5" :class="favorited ? 'text-amber-500' : 'text-slate-400'" :fill="favorited ? 'currentColor' : 'none'" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 5a2 2 0 012-2h10a2 2 0 012 2v16l-7-3.5L5 21V5z"/></svg>
                  <span class="text-sm" :class="favorited ? 'text-amber-600' : 'text-slate-600'">收藏</span>
                </button>
                <!-- Share -->
                <button
                  @click="handleShare"
                  class="flex items-center gap-1.5 px-4 py-2 rounded-lg border border-slate-200 hover:border-primary-300 hover:bg-primary-50 transition-colors"
                >
                  <svg class="w-5 h-5 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8.684 13.342C8.886 12.938 9 12.482 9 12c0-.482-.114-.938-.316-1.342m0 2.684a3 3 0 110-2.684m0 2.684l6.632 3.316m-6.632-6l6.632-3.316m0 0a3 3 0 105.367-2.684 3 3 0 00-5.367 2.684zm0 9.316a3 3 0 105.368 2.684 3 3 0 00-5.368-2.684z"/></svg>
                  <span class="text-sm text-slate-600">分享</span>
                </button>
              </div>
              <!-- Rating -->
              <div class="flex items-center gap-2">
                <span class="text-sm text-slate-500">我的评分：</span>
                <div class="flex items-center gap-0.5" @mouseleave="hoverRating = 0">
                  <button
                    v-for="s in 5"
                    :key="s"
                    @click="handleRate(s)"
                    @mouseenter="hoverRating = s"
                    class="transition-colors"
                    :class="s <= (hoverRating || myRating) ? 'text-amber-400' : 'text-slate-200 hover:text-amber-300'"
                  >
                    <svg class="w-5 h-5" fill="currentColor" viewBox="0 0 24 24"><path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/></svg>
                  </button>
                </div>
              </div>
            </div>
          </div>

          <!-- Description (Markdown rendered) -->
          <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-6 mb-4">
            <h2 class="text-lg font-semibold mb-4">资源描述</h2>
            <div class="prose prose-slate max-w-none prose-headings:scroll-mt-20 prose-img:rounded-lg prose-a:text-primary-600" v-html="renderMarkdown(resource.description)"></div>
          </div>

          <!-- Download / Link -->
          <div v-if="resource.resourceType === 'LINK' && resource.externalUrl" class="mb-4">
            <a
              :href="resource.externalUrl"
              target="_blank"
              rel="noopener noreferrer"
              class="inline-flex items-center gap-2 bg-primary-500 text-white px-6 py-3 rounded-lg hover:bg-primary-600 transition-colors"
            >
              <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14"/>
              </svg>
              访问资源链接
            </a>
          </div>

          <!-- File Download -->
          <div v-if="resource.resourceType === 'FILE' && resource.files?.length" class="mb-4">
            <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-6">
              <h3 class="text-lg font-semibold text-slate-800 mb-4">附件下载</h3>
              <div class="space-y-3">
                <div
                  v-for="file in resource.files"
                  :key="file.id"
                  class="flex items-center justify-between p-3 bg-slate-50 rounded-lg"
                >
                  <div class="flex items-center gap-3">
                    <svg class="w-8 h-8 text-primary-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/>
                    </svg>
                    <div>
                      <p class="text-sm font-medium text-slate-700">{{ file.fileName }}</p>
                      <p class="text-xs text-slate-400">{{ (file.fileSize / 1024 / 1024).toFixed(2) }} MB</p>
                    </div>
                  </div>
                  <a
                    :href="file.fileUrl"
                    download
                    class="text-primary-600 hover:text-primary-700 text-sm font-medium"
                  >
                    下载
                  </a>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Sidebar -->
        <aside class="w-80 flex-shrink-0 hidden lg:block">
          <!-- Related Recommendations -->
          <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-4 mb-4 sticky top-24">
            <div class="flex items-center gap-2 mb-4">
              <svg class="w-5 h-5 text-purple-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"/>
              </svg>
              <h3 class="text-sm font-semibold">相关推荐</h3>
            </div>
            <div v-if="relatedResources.length" class="space-y-3">
              <router-link
                v-for="rel in relatedResources"
                :key="rel.id"
                :to="`/resource/${rel.id}`"
                class="block p-2 rounded-lg hover:bg-slate-50"
              >
                <h4 class="text-sm font-medium truncate">{{ rel.title }}</h4>
                <p class="text-xs text-slate-400 mt-0.5">{{ rel.avgRating?.toFixed(1) }} 分 &middot; {{ rel.viewCount }} 浏览</p>
              </router-link>
            </div>
            <p v-else class="text-xs text-slate-400">暂无相关推荐</p>
          </div>

          <!-- Hot Tags -->
          <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-4">
            <h3 class="text-sm font-semibold mb-3">热门标签</h3>
            <div class="flex flex-wrap gap-2">
              <router-link
                v-for="tag in hotTags.slice(0, 10)"
                :key="tag.id"
                :to="`/search?q=${tag.name}`"
                class="text-xs bg-slate-100 text-slate-600 px-2 py-1 rounded hover:bg-primary-100 hover:text-primary-600"
              >
                {{ tag.name }}
              </router-link>
            </div>
          </div>
        </aside>
      </div>

      <!-- Comments -->
      <section class="mt-8">
        <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-6">
          <h2 class="text-xl font-semibold text-slate-800 mb-6">评论 ({{ resource.commentCount }})</h2>

          <!-- New Comment -->
          <div v-if="userStore.isLoggedIn" class="mb-8">
            <textarea
              v-model="newComment"
              class="w-full border border-slate-300 rounded-lg px-4 py-3 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent h-24 resize-none"
              placeholder="写下你的评论..."
            ></textarea>
            <button
              @click="submitComment"
              class="mt-3 bg-primary-500 text-white px-4 py-2 rounded-lg text-sm hover:bg-primary-600 transition-colors"
            >
              发表评论
            </button>
          </div>
          <div v-else class="mb-8 p-4 bg-slate-50 rounded-lg text-center">
            <router-link to="/login" class="text-primary-600 hover:underline">登录</router-link>
            <span class="text-slate-500"> 后发表评论</span>
          </div>

          <!-- Comment List -->
          <div class="space-y-4">
            <div v-for="comment in comments" :key="comment.id" class="flex gap-3">
              <img
                v-if="getAvatarUrl(comment.user)"
                :src="getAvatarUrl(comment.user)!"
                class="w-8 h-8 rounded-full object-cover flex-shrink-0"
                alt="avatar"
              />
              <div v-else class="w-8 h-8 bg-green-100 rounded-full flex items-center justify-center flex-shrink-0">
                <span class="text-xs font-medium text-green-600">
                  {{ comment.user?.nickname?.[0] || '用' }}
                </span>
              </div>
              <div class="flex-1">
                <div class="flex items-center gap-2">
                  <span class="text-sm font-medium">{{ comment.user?.nickname }}</span>
                  <span class="text-xs text-slate-400">{{ new Date(comment.createdAt).toLocaleString() }}</span>
                </div>
                <p class="text-sm text-slate-600 mt-1">{{ comment.content }}</p>
                <div class="flex items-center gap-3 mt-2">
                  <button
                    @click="handleCommentLike(comment.id)"
                    class="flex items-center gap-1 text-xs text-slate-400 hover:text-primary-500 transition-colors"
                  >
                    <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M14 10h4.764a2 2 0 011.789 2.894l-3.5 7A2 2 0 0115.263 21h-4.017c-.163 0-.326-.02-.485-.06L7 20m7-10V5a2 2 0 00-2-2h-.095c-.5 0-.905.405-.905.905 0 .714-.211 1.412-.608 2.006L7 11v9m7-10h-2M7 20H5a2 2 0 01-2-2v-6a2 2 0 012-2h2.5"/></svg>
                    {{ comment.likeCount || 0 }}
                  </button>
                  <button
                    v-if="userStore.isLoggedIn"
                    @click="replyTo = comment.id"
                    class="text-xs text-slate-400 hover:text-primary-500 transition-colors"
                  >
                    回复
                  </button>
                </div>

                <!-- Replies -->
                <div v-if="comment.replies?.length" class="mt-3 ml-4 pt-3 border-t border-slate-100 space-y-3">
                  <div v-for="reply in comment.replies" :key="reply.id" class="flex gap-3">
                    <img
                      v-if="getAvatarUrl(reply.user)"
                      :src="getAvatarUrl(reply.user)!"
                      class="w-6 h-6 rounded-full object-cover flex-shrink-0"
                      alt="avatar"
                    />
                    <div v-else class="w-6 h-6 bg-primary-100 rounded-full flex items-center justify-center flex-shrink-0">
                      <span class="text-[10px] font-medium text-primary-600">
                        {{ reply.user?.nickname?.[0] || '用' }}
                      </span>
                    </div>
                    <div>
                      <div class="flex items-center gap-2">
                        <span class="text-sm font-medium">{{ reply.user?.nickname }}</span>
                        <span v-if="reply.user?.id === (resource as any)?.publisherId" class="text-xs bg-primary-100 text-primary-600 px-1.5 py-0.5 rounded">作者</span>
                        <span class="text-xs text-slate-400">{{ new Date(reply.createdAt).toLocaleString() }}</span>
                      </div>
                      <p class="text-sm text-slate-600 mt-1">{{ reply.content }}</p>
                    </div>
                  </div>
                </div>

                <!-- Reply Form -->
                <div v-if="replyTo === comment.id" class="mt-3">
                  <textarea
                    v-model="replyContent"
                    class="w-full border border-slate-300 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent h-16 resize-none"
                    placeholder="回复..."
                  ></textarea>
                  <div class="flex gap-2 mt-2">
                    <button
                      @click="submitReply(comment.id)"
                      class="bg-primary-500 text-white px-3 py-1 rounded text-sm hover:bg-primary-600 transition-colors"
                    >
                      回复
                    </button>
                    <button
                      @click="replyTo = null"
                      class="bg-slate-100 text-slate-600 px-3 py-1 rounded text-sm hover:bg-slate-200 transition-colors"
                    >
                      取消
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>
    </main>
  </div>

  <!-- Loading -->
  <div v-else-if="loading" class="flex justify-center py-20">
    <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600"></div>
  </div>
</template>
