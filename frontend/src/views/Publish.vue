<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { resourceApi } from '@/api/resource'
import { categoryApi, tagApi } from '@/api/category'
import type { Category, Tag } from '@/types'

const router = useRouter()
const categories = ref<Category[]>([])
const tags = ref<Tag[]>([])
const loading = ref(false)
const error = ref('')

const form = ref({
  title: '',
  categoryId: null as number | null,
  tags: [] as string[],
  description: '',
  resourceType: 'LINK' as 'FILE' | 'LINK',
  externalUrl: '',
})

const selectedFiles = ref<File[]>([])
const uploadProgress = ref(0)
const isDragging = ref(false)

const tagInput = ref('')

const ALLOWED_EXTENSIONS = ['.pdf', '.docx', '.pptx', '.mp4', '.zip']
const MAX_FILE_SIZE = 500 * 1024 * 1024 // 500MB

onMounted(async () => {
  const [catRes, tagRes] = await Promise.all([
    categoryApi.getTree(),
    tagApi.getHot(),
  ])
  categories.value = catRes.data.data
  tags.value = tagRes.data.data
})

function addTag(tag: string) {
  if (tag && !form.value.tags.includes(tag) && form.value.tags.length < 10) {
    form.value.tags.push(tag)
    tagInput.value = ''
  }
}

function removeTag(index: number) {
  form.value.tags.splice(index, 1)
}

function validateFile(file: File): string | null {
  const ext = '.' + file.name.split('.').pop()?.toLowerCase()
  if (!ALLOWED_EXTENSIONS.includes(ext)) {
    return `不支持的文件类型: ${ext}，仅支持 PDF/DOCX/PPT/MP4/ZIP`
  }
  if (file.size > MAX_FILE_SIZE) {
    return `文件大小超过限制（最大500MB）`
  }
  return null
}

function handleFileSelect(event: Event) {
  const input = event.target as HTMLInputElement
  if (input.files) {
    addFiles(Array.from(input.files))
  }
}

function handleDrop(event: DragEvent) {
  isDragging.value = false
  if (event.dataTransfer?.files) {
    addFiles(Array.from(event.dataTransfer.files))
  }
}

function addFiles(files: File[]) {
  for (const file of files) {
    const err = validateFile(file)
    if (err) {
      error.value = err
      return
    }
  }
  error.value = ''
  selectedFiles.value.push(...files)
}

function removeFile(index: number) {
  selectedFiles.value.splice(index, 1)
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

async function handleSubmit() {
  if (!form.value.title || !form.value.categoryId || !form.value.description) {
    error.value = '请填写必填字段'
    return
  }
  if (form.value.resourceType === 'FILE' && selectedFiles.value.length === 0) {
    error.value = '请上传至少一个文件'
    return
  }
  loading.value = true
  error.value = ''
  uploadProgress.value = 0
  try {
    const formData = new FormData()
    const data = {
      title: form.value.title,
      categoryId: form.value.categoryId,
      tags: form.value.tags,
      description: form.value.description,
      resourceType: form.value.resourceType,
      externalUrl: form.value.externalUrl || undefined,
    }
    formData.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }))
    if (form.value.resourceType === 'FILE') {
      selectedFiles.value.forEach(file => formData.append('files', file))
    }
    await resourceApi.create(formData)
    router.push('/')
  } catch (e: any) {
    error.value = e.message || '发布失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="max-w-3xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
    <h1 class="text-2xl font-bold text-slate-800 mb-8">发布资源</h1>

    <div v-if="error" class="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-600">
      {{ error }}
    </div>

    <form @submit.prevent="handleSubmit" class="space-y-6">
      <div>
        <label class="block text-sm font-medium text-slate-700 mb-1">标题 *</label>
        <input v-model="form.title" class="input-field" placeholder="资源标题" required />
      </div>

      <div>
        <label class="block text-sm font-medium text-slate-700 mb-1">分类 *</label>
        <select v-model="form.categoryId" class="input-field" required>
          <option :value="null">请选择分类</option>
          <template v-for="cat in categories" :key="cat.id">
            <option :value="cat.id">{{ cat.name }}</option>
            <option v-for="child in cat.children" :key="child.id" :value="child.id">&nbsp;&nbsp;{{ child.name }}</option>
          </template>
        </select>
      </div>

      <div>
        <label class="block text-sm font-medium text-slate-700 mb-1">标签 (最多10个)</label>
        <div class="flex flex-wrap gap-2 mb-2">
          <span v-for="(tag, i) in form.tags" :key="i" class="flex items-center gap-1 px-2 py-1 bg-primary-100 text-primary-700 text-sm rounded">
            {{ tag }}
            <button type="button" @click="removeTag(i)" class="text-primary-400 hover:text-primary-600">&times;</button>
          </span>
        </div>
        <div class="flex gap-2">
          <input v-model="tagInput" class="input-field flex-1" placeholder="输入标签名" @keyup.enter.prevent="addTag(tagInput)" />
          <button type="button" @click="addTag(tagInput)" class="btn-secondary">添加</button>
        </div>
        <div class="flex flex-wrap gap-2 mt-2">
          <button
            v-for="tag in tags.slice(0, 10)"
            :key="tag.id"
            type="button"
            @click="addTag(tag.name)"
            class="px-2 py-0.5 bg-slate-100 text-slate-600 text-xs rounded hover:bg-primary-100 hover:text-primary-700"
          >
            + {{ tag.name }}
          </button>
        </div>
      </div>

      <div>
        <label class="block text-sm font-medium text-slate-700 mb-1">描述 *</label>
        <textarea v-model="form.description" class="input-field h-48 resize-none" placeholder="详细描述资源内容 (支持 Markdown)" required></textarea>
      </div>

      <div>
        <label class="block text-sm font-medium text-slate-700 mb-2">资源类型</label>
        <div class="flex gap-4">
          <label class="flex items-center gap-2 cursor-pointer">
            <input type="radio" v-model="form.resourceType" value="LINK" class="text-primary-600" />
            <span>外部链接</span>
          </label>
          <label class="flex items-center gap-2 cursor-pointer">
            <input type="radio" v-model="form.resourceType" value="FILE" class="text-primary-600" />
            <span>文件上传</span>
          </label>
        </div>
      </div>

      <div v-if="form.resourceType === 'LINK'">
        <label class="block text-sm font-medium text-slate-700 mb-1">资源链接</label>
        <input v-model="form.externalUrl" class="input-field" placeholder="https://..." />
      </div>

      <div v-if="form.resourceType === 'FILE'">
        <label class="block text-sm font-medium text-slate-700 mb-1">上传文件 *</label>
        <div
          class="border-2 border-dashed rounded-xl p-8 text-center transition-colors cursor-pointer"
          :class="isDragging ? 'border-primary-500 bg-primary-50' : 'border-slate-300 hover:border-primary-400'"
          @dragover.prevent="isDragging = true"
          @dragleave="isDragging = false"
          @drop.prevent="handleDrop"
          @click="($refs.fileInput as HTMLInputElement).click()"
        >
          <input ref="fileInput" type="file" multiple accept=".pdf,.docx,.pptx,.mp4,.zip" class="hidden" @change="handleFileSelect" />
          <div class="text-slate-400 mb-2">
            <svg class="w-10 h-10 mx-auto" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" />
            </svg>
          </div>
          <p class="text-sm text-slate-500">拖拽文件到此处或点击选择</p>
          <p class="text-xs text-slate-400 mt-1">支持 PDF / DOCX / PPT / MP4 / ZIP，最大 500MB</p>
        </div>

        <div v-if="selectedFiles.length > 0" class="mt-3 space-y-2">
          <div v-for="(file, i) in selectedFiles" :key="i" class="flex items-center gap-3 p-3 bg-slate-50 rounded-lg">
            <svg class="w-5 h-5 text-slate-400 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
            </svg>
            <div class="flex-1 min-w-0">
              <p class="text-sm text-slate-700 truncate">{{ file.name }}</p>
              <p class="text-xs text-slate-400">{{ formatSize(file.size) }}</p>
            </div>
            <button type="button" @click="removeFile(i)" class="text-slate-400 hover:text-red-500">
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
        </div>

        <div v-if="uploadProgress > 0 && uploadProgress < 100" class="mt-3">
          <div class="h-2 bg-slate-200 rounded-full overflow-hidden">
            <div class="h-full bg-primary-500 transition-all" :style="{ width: uploadProgress + '%' }"></div>
          </div>
          <p class="text-xs text-slate-400 mt-1">上传中 {{ uploadProgress }}%</p>
        </div>
      </div>

      <button type="submit" class="btn-primary w-full py-3" :disabled="loading">
        {{ loading ? '发布中...' : '发布资源' }}
      </button>
    </form>
  </div>
</template>
