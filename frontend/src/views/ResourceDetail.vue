<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { marked } from 'marked'
import { resourceApi } from '@/api/resource'
import { commentApi } from '@/api/comment'
import { aiApi } from '@/api/ai'
import { tagApi } from '@/api/category'
import { useUserStore } from '@/store/user'
import {
  clearPageContext,
  setResourcePageContext,
  setSelectedText,
} from '@/store/pageContext'
import AppToast from '@/components/AppToast.vue'
import CommentNode from '@/components/CommentNode.vue'
import type { Resource, Comment, Tag } from '@/types'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const id = computed(() => Number(route.params.id))

const resource = ref<Resource | null>(null)
const comments = ref<Comment[]>([])
const newComment = ref('')
// 当前正在回复的评论 id（多级嵌套：可以是树中任意节点）
const replyTo = ref<number | null>(null)
const myRating = ref(0)
const liked = ref(false)
const favorited = ref(false)
const hoverRating = ref(0)
const loading = ref(true)
const relatedResources = ref<Resource[]>([])
const hotTags = ref<Tag[]>([])
const toast = ref<InstanceType<typeof AppToast>>()

async function loadResource() {
  loading.value = true
  try {
    const [resRes, comRes] = await Promise.all([
      resourceApi.getDetail(id.value),
      commentApi.getList(id.value),
    ])
    resource.value = resRes.data.data
    comments.value = comRes.data.data.records

    // 扩展一：登记页面上下文，供 AI 悬浮助手携带（后端按 id 二次富化）
    setResourcePageContext(id.value)
    setSelectedText('')

    if (userStore.isLoggedIn) {
      try {
        const intRes = await resourceApi.getInteractions(id.value)
        liked.value = intRes.data.data.liked
        favorited.value = intRes.data.data.favorited
        myRating.value = intRes.data.data.myRating || 0
      } catch {}
    }

    try {
      const [recRes, tagRes] = await Promise.all([
        aiApi.getRecommendations(),
        tagApi.getHot(),
      ])
      relatedResources.value = normalizeRecommendations(recRes.data.data).map((r: any) => r.resource).filter((r: Resource) => r.id !== id.value).slice(0, 4)
      hotTags.value = tagRes.data.data || []
    } catch {}
  } finally {
    loading.value = false
  }
}

onMounted(loadResource)
watch(id, loadResource)

// 正文渲染完成后提取大纲；滚动时高亮当前章节
watch(resource, async (val) => {
  if (val) {
    await nextTick()
    buildToc()
  }
})

// 扩展一：划词提问——监听正文选区，进入 AI 助手的"解释这一段"入口
const contentRef = ref<HTMLElement | null>(null)

function handleSelectionChange() {
  const selection = window.getSelection()
  if (!selection || selection.isCollapsed || !contentRef.value) {
    setSelectedText('')
    return
  }
  if (selection.rangeCount > 0 && selection.containsNode(contentRef.value, true)) {
    setSelectedText(selection.toString())
  } else {
    setSelectedText('')
  }
}

onMounted(() => {
  document.addEventListener('selectionchange', handleSelectionChange)
  window.addEventListener('scroll', handleScrollSpy, { passive: true })
})

onBeforeUnmount(() => {
  document.removeEventListener('selectionchange', handleSelectionChange)
  window.removeEventListener('scroll', handleScrollSpy)
  clearPageContext()
})

function normalizeRecommendations(data: any): any[] {
  if (Array.isArray(data)) return data
  return data?.records || []
}

function renderMarkdown(text: string): string {
  if (!text) return ''
  try {
    const renderer = new marked.Renderer()
    renderer.link = function ({ href, text }: { href: string; text: string }) {
      return `<a href="${href}" target="_blank" rel="noopener noreferrer">${text}</a>`
    }
    return marked.parse(text, { breaks: true, renderer }) as string
  } catch {
    return text
  }
}

// ---- 文档大纲：从渲染后的正文提取 h1-h4，点击跳转 + 滚动高亮 ----
interface TocItem {
  id: string
  text: string
  level: number
}

const toc = ref<TocItem[]>([])
const activeHeadingId = ref('')

function buildToc() {
  toc.value = []
  activeHeadingId.value = ''
  if (!contentRef.value) return
  const headings = contentRef.value.querySelectorAll('h1, h2, h3, h4')
  const items: TocItem[] = []
  headings.forEach((h, index) => {
    const anchorId = `heading-anchor-${index}`
    h.id = anchorId
    // 锚点跳转时给吸顶导航留出空间
    h.classList.add('scroll-mt-24')
    items.push({
      id: anchorId,
      text: (h.textContent || '').trim(),
      level: Number(h.tagName.substring(1)),
    })
  })
  toc.value = items
  if (items.length) activeHeadingId.value = items[0].id
}

function scrollToHeading(anchorId: string) {
  document.getElementById(anchorId)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function handleScrollSpy() {
  if (!toc.value.length) return
  let current = toc.value[0].id
  for (const item of toc.value) {
    const el = document.getElementById(item.id)
    if (el && el.getBoundingClientRect().top <= 120) {
      current = item.id
    }
  }
  activeHeadingId.value = current
}

async function handleLike() {
  if (!userStore.isLoggedIn) {
    toast.value?.show('请先登录后再操作', 'warning')
    return
  }
  const res = await resourceApi.like(id.value)
  liked.value = res.data.data.liked
  if (resource.value) {
    resource.value.likeCount = res.data.data.likeCount
  }
  toast.value?.show(liked.value ? '点赞成功' : '已取消点赞', 'success')
}

async function handleFavorite() {
  if (!userStore.isLoggedIn) {
    toast.value?.show('请先登录后再操作', 'warning')
    return
  }
  const res = await resourceApi.favorite(id.value)
  favorited.value = res.data.data.favorited
  if (resource.value) {
    resource.value.favoriteCount = res.data.data.favoriteCount
  }
  toast.value?.show(favorited.value ? '收藏成功' : '已取消收藏', 'success')
}

async function handleRate(score: number) {
  if (!userStore.isLoggedIn) {
    toast.value?.show('请先登录后再评分', 'warning')
    return
  }
  myRating.value = score
  const res = await resourceApi.rate(id.value, score)
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

async function submitComment() {
  if (!userStore.isLoggedIn) {
    toast.value?.show('请先登录后再评论', 'warning')
    return
  }
  if (!newComment.value.trim()) return
  try {
    const res = await commentApi.create(id.value, newComment.value)
    comments.value.unshift(res.data.data)
    newComment.value = ''
    if (resource.value) {
      resource.value.commentCount = (resource.value.commentCount || 0) + 1
    }
    toast.value?.show('评论成功', 'success')
  } catch (error: any) {
    toast.value?.show(error?.message || '评论失败', 'error')
  }
}

function startReply(comment: Comment) {
  replyTo.value = comment.id
}

/** 把新回复插入评论树的指定父节点下（多级嵌套） */
function insertReply(list: Comment[], parentId: number, reply: Comment): boolean {
  for (const node of list) {
    if (node.id === parentId) {
      if (!node.replies) node.replies = []
      node.replies.push(reply)
      return true
    }
    if (node.replies?.length && insertReply(node.replies, parentId, reply)) {
      return true
    }
  }
  return false
}

async function submitReply(parentId: number, content: string) {
  if (!userStore.isLoggedIn) {
    toast.value?.show('请先登录后再回复', 'warning')
    return
  }
  if (!content.trim()) return
  try {
    const res = await commentApi.create(id.value, content.trim(), parentId)
    // 直接挂到被回复的评论下（多级嵌套），不再拼接"回复 @昵称"前缀
    insertReply(comments.value, parentId, res.data.data)
    if (resource.value) {
      resource.value.commentCount = (resource.value.commentCount || 0) + 1
    }
    toast.value?.show('回复成功', 'success')
  } catch (error: any) {
    toast.value?.show(error?.message || '回复失败', 'error')
  }
  replyTo.value = null
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
            <div ref="contentRef" class="prose prose-slate max-w-none prose-headings:scroll-mt-20 prose-img:rounded-lg prose-a:text-primary-600" v-html="renderMarkdown(resource.description)"></div>
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

          <!-- Comments：置于主列内，拉高 flex 行高度，保证右栏吸顶全程有效 -->
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

              <!-- Comment List：多级嵌套，递归分支展示 -->
              <div class="space-y-4">
                <CommentNode
                  v-for="comment in comments"
                  :key="comment.id"
                  :comment="comment"
                  :resource="resource"
                  :depth="0"
                  :reply-to-id="replyTo"
                  @reply="startReply"
                  @submit-reply="submitReply"
                  @cancel-reply="replyTo = null"
                />
              </div>
            </div>
          </section>
        </div>

        <!-- Sidebar：整体吸顶，滚动时固定可见 -->
        <aside class="w-80 flex-shrink-0 hidden lg:block">
          <div class="sticky top-20 space-y-4 max-h-[calc(100vh-6rem)] overflow-y-auto pb-2 scroll-smooth">
            <!-- 文档大纲（markdown 标题导航） -->
            <div v-if="toc.length" class="bg-white rounded-xl shadow-sm border border-slate-200 p-4">
              <div class="flex items-center gap-2 mb-3">
                <svg class="w-4 h-4 text-primary-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 10h16M4 14h10M4 18h10"/>
                </svg>
                <h3 class="text-sm font-semibold">文档大纲</h3>
              </div>
              <ul class="space-y-0.5 border-l border-slate-100">
                <li v-for="item in toc" :key="item.id">
                  <button
                    @click="scrollToHeading(item.id)"
                    class="block w-full truncate rounded-r px-2 py-1 text-left text-xs transition-colors border-l-2"
                    :class="activeHeadingId === item.id
                      ? 'border-primary-500 bg-primary-50 text-primary-600 font-medium'
                      : 'border-transparent text-slate-500 hover:text-primary-500 hover:bg-slate-50'"
                    :style="{ paddingLeft: `${(item.level - 1) * 10 + 8}px` }"
                    :title="item.text"
                  >
                    {{ item.text }}
                  </button>
                </li>
              </ul>
            </div>

            <!-- Related Recommendations -->
            <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-4">
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
                  :to="`/search?tag=${tag.name}`"
                  class="text-xs bg-slate-100 text-slate-600 px-2 py-1 rounded hover:bg-primary-100 hover:text-primary-600"
                >
                  {{ tag.name }}
                </router-link>
              </div>
            </div>
          </div>
        </aside>
      </div>
    </main>
  </div>

  <!-- Loading -->
  <div v-else-if="loading" class="flex justify-center py-20">
    <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-600"></div>
  </div>
</template>
