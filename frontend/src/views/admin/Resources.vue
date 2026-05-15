<script setup lang="ts">
import { ref, onMounted } from 'vue'
import request from '@/api/request'
import type { Resource } from '@/types'

const resources = ref<Resource[]>([])

onMounted(() => loadPending())

async function loadPending() {
  const res = await request.get('/admin/resources/pending', { params: { page: 1, size: 50 } })
  resources.value = res.data.data.records
}

async function audit(id: number, action: 'APPROVE' | 'REJECT') {
  await request.put(`/admin/resources/${id}/audit`, { action })
  loadPending()
}
</script>

<template>
  <div>
    <h1 class="text-2xl font-bold text-slate-800 mb-6">资源审核</h1>
    <div v-if="resources.length === 0" class="text-center py-12 text-slate-400">暂无待审核资源</div>
    <div v-else class="space-y-4">
      <div v-for="res in resources" :key="res.id" class="card p-5">
        <h3 class="font-semibold text-slate-800 mb-2">{{ res.title }}</h3>
        <p class="text-sm text-slate-500 mb-3 line-clamp-2">{{ res.description }}</p>
        <div class="flex items-center justify-between">
          <span class="text-xs text-slate-400">发布者 ID: {{ res.publisherId }}</span>
          <div class="flex gap-2">
            <button @click="audit(res.id, 'APPROVE')" class="btn-primary text-sm px-3 py-1">通过</button>
            <button @click="audit(res.id, 'REJECT')" class="btn-secondary text-sm px-3 py-1 text-red-600 border-red-300 hover:bg-red-50">拒绝</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
