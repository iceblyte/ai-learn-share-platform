<script setup lang="ts">
import { ref, onMounted } from 'vue'
import request from '@/api/request'

const stats = ref({ totalUsers: 0, totalResources: 0, pendingResources: 0 })

onMounted(async () => {
  const res = await request.get('/admin/statistics')
  stats.value = res.data.data
})
</script>

<template>
  <div>
    <h1 class="text-2xl font-bold text-slate-800 mb-6">仪表盘</h1>
    <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
      <div class="card p-6">
        <p class="text-sm text-slate-500 mb-1">总用户数</p>
        <p class="text-3xl font-bold text-slate-800">{{ stats.totalUsers }}</p>
      </div>
      <div class="card p-6">
        <p class="text-sm text-slate-500 mb-1">已发布资源</p>
        <p class="text-3xl font-bold text-primary-600">{{ stats.totalResources }}</p>
      </div>
      <div class="card p-6">
        <p class="text-sm text-slate-500 mb-1">待审核资源</p>
        <p class="text-3xl font-bold text-amber-500">{{ stats.pendingResources }}</p>
      </div>
    </div>
  </div>
</template>
