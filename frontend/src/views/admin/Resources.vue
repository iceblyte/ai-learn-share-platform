<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/api/request'
import type { Resource } from '@/types'
import AppModal from '@/components/AppModal.vue'

const router = useRouter()
const resources = ref<Resource[]>([])
const loading = ref(true)

const showAuditModal = ref(false)
const auditTarget = ref<Resource | null>(null)
const auditAction = ref<'APPROVE' | 'REJECT'>('APPROVE')

onMounted(() => loadPending())

async function loadPending() {
  loading.value = true
  try {
    const res = await request.get('/admin/resources/pending', { params: { page: 1, size: 50 } })
    resources.value = res.data.data?.records || []
  } catch {} finally {
    loading.value = false
  }
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
    resources.value = resources.value.filter(r => r.id !== auditTarget.value!.id)
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

    <div v-if="loading" class="flex justify-center py-12">
      <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600"></div>
    </div>

    <div v-else-if="resources.length === 0" class="text-center py-12 text-slate-400">
      <svg class="w-16 h-16 mx-auto text-slate-300 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"/>
      </svg>
      <p class="text-lg mb-1">暂无待审核资源</p>
      <p class="text-sm">所有资源均已处理</p>
    </div>

    <div v-else class="space-y-3">
      <div
        v-for="res in resources"
        :key="res.id"
        class="bg-white rounded-xl shadow-sm border border-slate-200 p-5 hover:shadow-md transition-shadow"
      >
        <div class="flex items-start justify-between">
          <div class="flex-1 min-w-0">
            <h3 class="font-semibold text-slate-800">{{ res.title }}</h3>
            <p class="text-sm text-slate-500 mt-1 line-clamp-2">{{ res.aiSummary || res.description }}</p>
            <div class="flex items-center gap-3 mt-2 text-xs text-slate-400">
              <span>{{ res.publisher?.nickname || '未知用户' }}</span>
              <span>{{ formatTimeAgo(res.createdAt) }}</span>
              <span v-if="res.category" class="px-2 py-0.5 bg-primary-50 text-primary-600 rounded">{{ res.category.name }}</span>
            </div>
          </div>
          <div class="flex items-center gap-2 flex-shrink-0 ml-4">
            <button
              @click="router.push(`/resource/${res.id}`)"
              class="px-3 py-1.5 text-xs bg-slate-100 text-slate-600 rounded-lg hover:bg-slate-200 transition-colors"
            >
              查看
            </button>
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
          </div>
        </div>
      </div>
    </div>
  </div>

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
