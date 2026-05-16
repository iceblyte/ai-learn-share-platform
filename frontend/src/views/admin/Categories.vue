<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { categoryApi } from '@/api/category'
import type { Category } from '@/types'

const categories = ref<Category[]>([])
const loading = ref(true)
const showAdd = ref(false)
const newName = ref('')
const editingId = ref<number | null>(null)
const editingName = ref('')

onMounted(async () => {
  await loadCategories()
})

async function loadCategories() {
  loading.value = true
  try {
    const res = await categoryApi.getTree()
    categories.value = res.data.data || []
  } catch {} finally {
    loading.value = false
  }
}

async function addCategory() {
  if (!newName.value.trim()) return
  try {
    await categoryApi.create({ name: newName.value.trim() })
    newName.value = ''
    showAdd.value = false
    await loadCategories()
  } catch {}
}

async function updateCategory(id: number) {
  if (!editingName.value.trim()) return
  try {
    await categoryApi.update(id, { name: editingName.value.trim() })
    editingId.value = null
    await loadCategories()
  } catch {}
}

async function deleteCategory(id: number) {
  if (!confirm('确定删除此分类？子分类也会被删除。')) return
  try {
    await categoryApi.delete(id)
    await loadCategories()
  } catch {}
}

function startEdit(cat: Category) {
  editingId.value = cat.id
  editingName.value = cat.name
}
</script>

<template>
  <div>
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-2xl font-bold text-slate-800">分类管理</h1>
      <button
        @click="showAdd = !showAdd"
        class="bg-primary-500 text-white px-4 py-2 rounded-lg text-sm hover:bg-primary-600 transition-colors"
      >
        + 新增分类
      </button>
    </div>

    <!-- Add Form -->
    <div v-if="showAdd" class="bg-white rounded-xl shadow-sm border border-slate-200 p-4 mb-4">
      <div class="flex gap-3">
        <input
          v-model="newName"
          placeholder="分类名称"
          class="flex-1 border border-slate-300 rounded-lg px-4 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
          @keyup.enter="addCategory"
        />
        <button @click="addCategory" class="bg-primary-500 text-white px-4 py-2 rounded-lg text-sm hover:bg-primary-600">添加</button>
        <button @click="showAdd = false" class="bg-slate-100 text-slate-600 px-4 py-2 rounded-lg text-sm hover:bg-slate-200">取消</button>
      </div>
    </div>

    <div v-if="loading" class="flex justify-center py-12">
      <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600"></div>
    </div>

    <div v-else class="bg-white rounded-xl shadow-sm border border-slate-200">
      <div class="divide-y divide-slate-100">
        <template v-for="cat in categories" :key="cat.id">
          <!-- Parent Category -->
          <div class="flex items-center justify-between p-4 hover:bg-slate-50">
            <div class="flex items-center gap-2">
              <svg class="w-4 h-4 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z"/>
              </svg>
              <span v-if="editingId !== cat.id" class="font-medium">{{ cat.name }}</span>
              <input
                v-else
                v-model="editingName"
                class="border border-primary-300 rounded px-2 py-1 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
                @keyup.enter="updateCategory(cat.id)"
                @keyup.escape="editingId = null"
              />
            </div>
            <div class="flex items-center gap-1">
              <button @click="startEdit(cat)" class="p-1.5 text-slate-400 hover:text-primary-500 hover:bg-primary-50 rounded">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"/></svg>
              </button>
              <button @click="deleteCategory(cat.id)" class="p-1.5 text-slate-400 hover:text-red-500 hover:bg-red-50 rounded">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/></svg>
              </button>
            </div>
          </div>
          <!-- Children -->
          <div
            v-for="child in cat.children"
            :key="child.id"
            class="flex items-center justify-between p-4 pl-12 hover:bg-slate-50"
          >
            <span class="text-sm text-slate-600">{{ child.name }}</span>
            <div class="flex items-center gap-1">
              <button @click="startEdit(child)" class="p-1.5 text-slate-400 hover:text-primary-500 hover:bg-primary-50 rounded">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"/></svg>
              </button>
              <button @click="deleteCategory(child.id)" class="p-1.5 text-slate-400 hover:text-red-500 hover:bg-red-50 rounded">
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/></svg>
              </button>
            </div>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>
