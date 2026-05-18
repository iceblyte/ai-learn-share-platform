<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { tagApi } from '@/api/category'
import type { Tag } from '@/types'
import AppModal from '@/components/AppModal.vue'

const tags = ref<Tag[]>([])
const loading = ref(true)
const showAdd = ref(false)
const newName = ref('')
const editingId = ref<number | null>(null)
const editingName = ref('')
const showDeleteModal = ref(false)
const deleteTargetId = ref<number | null>(null)

onMounted(async () => {
  await loadTags()
})

async function loadTags() {
  loading.value = true
  try {
    const res = await tagApi.getList()
    tags.value = res.data.data || []
  } catch {} finally {
    loading.value = false
  }
}

async function addTag() {
  if (!newName.value.trim()) return
  try {
    await tagApi.create({ name: newName.value.trim() })
    newName.value = ''
    showAdd.value = false
    await loadTags()
  } catch {}
}

async function updateTag(id: number) {
  if (!editingName.value.trim()) return
  try {
    await tagApi.update(id, { name: editingName.value.trim() })
    editingId.value = null
    await loadTags()
  } catch {}
}

function confirmDelete(id: number) {
  deleteTargetId.value = id
  showDeleteModal.value = true
}

async function executeDelete() {
  if (!deleteTargetId.value) return
  try {
    await tagApi.delete(deleteTargetId.value)
    await loadTags()
  } catch {}
  showDeleteModal.value = false
  deleteTargetId.value = null
}

function startEdit(tag: Tag) {
  editingId.value = tag.id
  editingName.value = tag.name
}
</script>

<template>
  <div>
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-2xl font-bold text-slate-800">标签管理</h1>
      <button
        @click="showAdd = !showAdd"
        class="bg-primary-500 text-white px-4 py-2 rounded-lg text-sm hover:bg-primary-600 transition-colors"
      >
        + 新增标签
      </button>
    </div>

    <!-- Add Form -->
    <div v-if="showAdd" class="bg-white rounded-xl shadow-sm border border-slate-200 p-4 mb-4">
      <div class="flex gap-3">
        <input
          v-model="newName"
          placeholder="标签名称"
          class="flex-1 border border-slate-300 rounded-lg px-4 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
          @keyup.enter="addTag"
        />
        <button @click="addTag" class="bg-primary-500 text-white px-4 py-2 rounded-lg text-sm hover:bg-primary-600">添加</button>
        <button @click="showAdd = false" class="bg-slate-100 text-slate-600 px-4 py-2 rounded-lg text-sm hover:bg-slate-200">取消</button>
      </div>
    </div>

    <div v-if="loading" class="flex justify-center py-12">
      <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600"></div>
    </div>

    <div v-else class="bg-white rounded-xl shadow-sm border border-slate-200 p-4">
      <div class="flex flex-wrap gap-2">
        <span
          v-for="tag in tags"
          :key="tag.id"
          class="inline-flex items-center gap-1.5 text-sm bg-slate-100 text-slate-700 px-3 py-1.5 rounded-lg group"
        >
          <template v-if="editingId !== tag.id">
            {{ tag.name }}
            <span class="text-xs text-slate-400">({{ tag.usageCount || 0 }})</span>
            <button @click="startEdit(tag)" class="opacity-0 group-hover:opacity-100 text-slate-400 hover:text-primary-500 transition-opacity">
              <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"/></svg>
            </button>
            <button @click="confirmDelete(tag.id)" class="opacity-0 group-hover:opacity-100 text-slate-400 hover:text-red-500 transition-opacity">
              <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/></svg>
            </button>
          </template>
          <template v-else>
            <input
              v-model="editingName"
              class="w-20 border border-primary-300 rounded px-1 py-0.5 text-sm focus:outline-none"
              @keyup.enter="updateTag(tag.id)"
              @keyup.escape="editingId = null"
            />
          </template>
        </span>
      </div>
    </div>
  </div>

  <!-- Delete Confirmation Modal -->
  <AppModal :visible="showDeleteModal" title="确认删除" @close="showDeleteModal = false" @confirm="executeDelete">
    <template #body>
      <p class="text-sm text-slate-600">确定删除此标签吗？此操作不可撤销。</p>
    </template>
    <template #footer>
      <button @click="showDeleteModal = false" class="px-4 py-2 text-sm text-slate-600 border border-slate-300 rounded-lg hover:bg-slate-50">取消</button>
      <button @click="executeDelete" class="px-4 py-2 text-sm text-white bg-red-500 rounded-lg hover:bg-red-600">确认删除</button>
    </template>
  </AppModal>
</template>
