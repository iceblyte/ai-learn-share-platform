<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import request from '@/api/request'
import type { Resource } from '@/types'
import AppModal from '@/components/AppModal.vue'
import { renderMarkdown } from '@/utils/markdown'

const resources = ref<Resource[]>([])
const loading = ref(true)

// 分页
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

/** 状态筛选：PENDING/PUBLISHED/REJECTED/DRAFT/''（全部），默认待审核 */
const statusFilter = ref<string>('PENDING')
const STATUS_TABS: { value: string; label: string }[] = [
  { value: 'PENDING', label: '待审核' },
  { value: 'PUBLISHED', label: '已发布' },
  { value: 'REJECTED', label: '已拒绝' },
  { value: 'DRAFT', label: '草稿' },
  { value: '', label: '全部' },
]
const STATUS_BADGE: Record<string, { text: string; cls: string }> = {
  PENDING: { text: '待审核', cls: 'bg-amber-100 text-amber-700' },
  PUBLISHED: { text: '已发布', cls: 'bg-green-100 text-green-700' },
  REJECTED: { text: '已拒绝', cls: 'bg-red-100 text-red-700' },
  DRAFT: { text: '草稿', cls: 'bg-slate-100 text-slate-600' },
}

const showAuditModal = ref(false)
const auditTarget = ref<Resource | null>(null)
const auditAction = ref<'APPROVE' | 'REJECT'>('APPROVE')

// Bug 修复：查看改为弹窗预览，不再跳转资源详情（待审核资源前台不可见）
const showViewModal = ref(false)
const viewTarget = ref<Resource | null>(null)

function openViewModal(resource: Resource) {
  viewTarget.value = resource
  showViewModal.value = true
}

/** 弹窗内描述按 markdown 渲染（已 DOMPurify 净化） */
const viewDescriptionHtml = computed(() => renderMarkdown(viewTarget.value?.description || ''))

onMounted(() => loadResources())

async function loadResources() {
  loading.value = true
  try {
    // /admin/resources 支持可选 status：空 = 全部资源（含通过/拒绝/草稿）
    const res = await request.get('/admin/resources', {
      params: { page: page.value, size: pageSize.value, status: statusFilter.value || undefined },
    })
    resources.value = res.data.data?.records || []
    total.value = res.data.data?.total || 0
  } catch {} finally {
    loading.value = false
  }
}

function switchStatus(value: string) {
  if (statusFilter.value === value) return
  statusFilter.value = value
  page.value = 1
  loadResources()
}

function goToPage(p: number) {
  if (p < 1 || p > totalPages.value || p === page.value) return
  page.value = p
  loadResources()
}

function confirmAudit(resource: Resource, action: 'APPROVE' | 'REJECT') {
  auditTarget.value = resource
  auditAction.value = action
  showAuditModal.value = true
}

async function executeAudit() {
  if (!auditTarget.value) return
  try {
    await request.put(`/admin/resources/${auditTarget.value.id}/audit`, { action: auditAction.value })
    // 审核后当前页可能只剩空页，回退一页
    if (resources.value.length === 1 && page.value > 1) page.value--
    await loadResources()
  } catch {}
  showAuditModal.value = false
  auditTarget.value = null
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
  return `${days} 天前`
}
</script>

<template>
  <div>
    <h1 class="text-2xl font-bold text-slate-800 mb-6">资源审核</h1>

    <!-- 状态筛选 -->
    <div class="flex items-center gap-2 mb-5">
      <button
        v-for="tab in STATUS_TABS"
        :key="tab.value"
        @click="switchStatus(tab.value)"
        :class="['px-4 py-1.5 rounded-lg text-sm font-medium transition-colors',
          statusFilter === tab.value ? 'bg-primary-600 text-white' : 'bg-slate-100 text-slate-600 hover:bg-slate-200']"
      >
        {{ tab.label }}
      </button>
    </div>

    <div v-if="loading" class="flex justify-center py-12">
      <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600"></div>
    </div>

    <div v-else-if="resources.length === 0" class="text-center py-12 text-slate-400">
      <svg class="w-16 h-16 mx-auto text-slate-300 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"/>
      </svg>
      <p class="text-lg mb-1">该状态下暂无资源</p>
      <p class="text-sm">可切换上方状态筛选查看其他资源</p>
    </div>

    <div v-else class="space-y-3">
      <div
        v-for="res in resources"
        :key="res.id"
        class="bg-white rounded-xl shadow-sm border border-slate-200 p-5 hover:shadow-md transition-shadow"
      >
        <div class="flex items-start justify-between">
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-2">
              <h3 class="font-semibold text-slate-800">{{ res.title }}</h3>
              <span
                v-if="STATUS_BADGE[res.status]"
                :class="['px-2 py-0.5 rounded text-xs font-medium', STATUS_BADGE[res.status].cls]"
              >{{ STATUS_BADGE[res.status].text }}</span>
            </div>
            <p class="text-sm text-slate-500 mt-1 line-clamp-2">{{ res.aiSummary || res.description }}</p>
            <div class="flex items-center gap-3 mt-2 text-xs text-slate-400">
              <span>{{ res.publisher?.nickname || '未知用户' }}</span>
              <span>{{ formatTimeAgo(res.createdAt) }}</span>
              <span v-if="res.category" class="px-2 py-0.5 bg-primary-50 text-primary-600 rounded">{{ res.category.name }}</span>
            </div>
          </div>
          <div class="flex items-center gap-2 flex-shrink-0 ml-4">
            <button
              @click="openViewModal(res)"
              class="px-3 py-1.5 text-xs bg-slate-100 text-slate-600 rounded-lg hover:bg-slate-200 transition-colors"
            >
              查看
            </button>
            <template v-if="res.status === 'PENDING'">
              <button
                @click="confirmAudit(res, 'APPROVE')"
                class="px-3 py-1.5 text-xs bg-green-500 text-white rounded-lg hover:bg-green-600 transition-colors"
              >
                通过
              </button>
              <button
                @click="confirmAudit(res, 'REJECT')"
                class="px-3 py-1.5 text-xs bg-red-500 text-white rounded-lg hover:bg-red-600 transition-colors"
              >
                拒绝
              </button>
            </template>
          </div>
        </div>
      </div>

      <!-- 分页 -->
      <div v-if="total > 0" class="flex items-center justify-between pt-4">
        <div class="flex items-center gap-3">
          <p class="text-sm text-slate-500">共 {{ total }} 条，第 {{ page }} / {{ totalPages }} 页</p>
          <label class="flex items-center gap-1 text-sm text-slate-500">
            每页
            <select
              v-model.number="pageSize"
              @change="page = 1; loadResources()"
              class="border border-slate-300 rounded px-1.5 py-1 text-sm bg-white focus:outline-none focus:ring-1 focus:ring-primary-400"
            >
              <option :value="10">10</option>
              <option :value="20">20</option>
              <option :value="50">50</option>
              <option :value="100">100</option>
            </select>
            条
          </label>
        </div>
        <div class="flex items-center gap-2">
          <button
            @click="goToPage(page - 1)"
            :disabled="page <= 1"
            class="px-3 py-1.5 text-sm border border-slate-300 rounded-lg bg-white hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            上一页
          </button>
          <button
            @click="goToPage(page + 1)"
            :disabled="page >= totalPages"
            class="px-3 py-1.5 text-sm border border-slate-300 rounded-lg bg-white hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            下一页
          </button>
        </div>
      </div>
    </div>
  </div>

  <!-- Resource Preview Modal：弹窗展示内容，不跳转 -->
  <AppModal
    :visible="showViewModal"
    :title="viewTarget?.title || '资源预览'"
    width="max-w-3xl"
    @close="showViewModal = false"
  >
    <template #body>
      <div v-if="viewTarget" class="space-y-4 text-sm">
        <div class="flex flex-wrap items-center gap-3 text-xs text-slate-400">
          <span>发布者：{{ viewTarget.publisher?.nickname || '未知用户' }}</span>
          <span v-if="viewTarget.createdAt">{{ new Date(viewTarget.createdAt).toLocaleString() }}</span>
          <span v-if="viewTarget.category" class="px-2 py-0.5 bg-primary-50 text-primary-600 rounded">{{ viewTarget.category.name }}</span>
          <span v-if="viewTarget.resourceType" class="px-2 py-0.5 bg-slate-100 text-slate-500 rounded">{{ viewTarget.resourceType }}</span>
        </div>

        <div v-if="viewTarget.aiSummary">
          <h4 class="mb-1 text-xs font-semibold text-slate-500">AI 摘要</h4>
          <p class="rounded-lg bg-slate-50 px-3 py-2 text-slate-600">{{ viewTarget.aiSummary }}</p>
        </div>

        <div v-if="viewTarget.externalUrl">
          <h4 class="mb-1 text-xs font-semibold text-slate-500">外部链接</h4>
          <a
            :href="viewTarget.externalUrl"
            target="_blank"
            rel="noopener noreferrer"
            class="break-all text-primary-600 hover:underline"
          >{{ viewTarget.externalUrl }}</a>
        </div>

        <div v-if="viewTarget.description">
          <h4 class="mb-1 text-xs font-semibold text-slate-500">资源描述</h4>
          <!-- eslint-disable-next-line vue/no-v-html —— 已 DOMPurify 净化 -->
          <div class="md-preview max-h-80 overflow-y-auto break-words rounded-lg border border-slate-100 px-3 py-2 text-slate-600" v-html="viewDescriptionHtml"></div>
        </div>

        <div v-if="viewTarget.coverImageUrl">
          <h4 class="mb-1 text-xs font-semibold text-slate-500">封面</h4>
          <img :src="viewTarget.coverImageUrl" class="max-h-48 rounded-lg border border-slate-100" alt="cover"/>
        </div>
      </div>
    </template>
    <template #footer>
      <button @click="showViewModal = false" class="px-4 py-2 text-sm text-slate-600 border border-slate-300 rounded-lg hover:bg-slate-50">关闭</button>
      <template v-if="viewTarget?.status === 'PENDING'">
        <button
          @click="showViewModal = false; confirmAudit(viewTarget!, 'APPROVE')"
          class="px-4 py-2 text-sm text-white rounded-lg bg-green-500 hover:bg-green-600"
        >
          通过
        </button>
        <button
          @click="showViewModal = false; confirmAudit(viewTarget!, 'REJECT')"
          class="px-4 py-2 text-sm text-white rounded-lg bg-red-500 hover:bg-red-600"
        >
          拒绝
        </button>
      </template>
    </template>
  </AppModal>

  <!-- Audit Confirmation Modal -->
  <AppModal
    :visible="showAuditModal"
    :title="auditAction === 'APPROVE' ? '确认通过' : '确认拒绝'"
    @close="showAuditModal = false"
    @confirm="executeAudit"
  >
    <template #body>
      <p class="text-sm text-slate-600">
        确定要{{ auditAction === 'APPROVE' ? '通过' : '拒绝' }}资源「{{ auditTarget?.title }}」吗？
      </p>
    </template>
    <template #footer>
      <button @click="showAuditModal = false" class="px-4 py-2 text-sm text-slate-600 border border-slate-300 rounded-lg hover:bg-slate-50">取消</button>
      <button
        @click="executeAudit"
        :class="['px-4 py-2 text-sm text-white rounded-lg', auditAction === 'APPROVE' ? 'bg-green-500 hover:bg-green-600' : 'bg-red-500 hover:bg-red-600']"
      >
        {{ auditAction === 'APPROVE' ? '确认通过' : '确认拒绝' }}
      </button>
    </template>
  </AppModal>
</template>
