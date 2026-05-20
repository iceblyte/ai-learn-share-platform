<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { resourceApi } from '@/api/resource'
import { categoryApi, tagApi } from '@/api/category'
import type { Category, Tag } from '@/types'
import { marked } from 'marked'
import AppModal from '@/components/AppModal.vue'

const router = useRouter()
const route = useRoute()
const categories = ref<Category[]>([])
const tags = ref<Tag[]>([])
const loading = ref(false)
const isDrafting = ref(false)
const error = ref('')
const isEdit = ref(false)
const editId = ref<number | null>(null)
const editStatus = ref('')

const form = ref({
  title: '',
  categoryId: null as number | null,
  tags: [] as string[],
  description: '',
  resourceType: 'LINK' as 'FILE' | 'LINK',
  externalUrl: '',
})

const selectedFiles = ref<File[]>([])
const coverFile = ref<File | null>(null)
const coverPreview = ref('')
const existingCoverUrl = ref('')
const uploadProgress = ref(0)
const isDragging = ref(false)
const showPreview = ref(false)
const showDraftModal = ref(false)
const textareaRef = ref<HTMLTextAreaElement | null>(null)

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

  // Edit mode: load existing resource
  const editParam = route.query.edit
  if (editParam) {
    isEdit.value = true
    editId.value = Number(editParam)
    await loadResource(editId.value)
  }
})

async function loadResource(id: number) {
  loading.value = true
  try {
    const res = await resourceApi.getDetail(id)
    const data = res.data.data
    form.value.title = data.title
    form.value.categoryId = data.category?.id || null
    form.value.tags = data.tags?.map(t => t.name) || []
    form.value.description = data.description
    form.value.resourceType = data.resourceType
    form.value.externalUrl = data.externalUrl || ''
    editStatus.value = data.status
    console.log('[Publish] loadResource - status from API:', data.status, 'editStatus:', editStatus.value)
    if (data.coverImageUrl) {
      existingCoverUrl.value = data.coverImageUrl
      coverPreview.value = data.coverImageUrl
    }
  } catch (e: any) {
    error.value = '加载资源失败: ' + (e.message || '未知错误')
  } finally {
    loading.value = false
  }
}

function addTag(tag: string) {
  if (tag && !form.value.tags.includes(tag) && form.value.tags.length < 10) {
    form.value.tags.push(tag)
    tagInput.value = ''
  }
}

function removeTag(index: number) {
  form.value.tags.splice(index, 1)
}

function insertMarkdown(prefix: string, suffix: string = '') {
  const textarea = textareaRef.value
  if (!textarea) return

  const start = textarea.selectionStart
  const end = textarea.selectionEnd
  const selected = form.value.description.substring(start, end)
  const replacement = prefix + (selected || '文本') + suffix

  form.value.description =
    form.value.description.substring(0, start) +
    replacement +
    form.value.description.substring(end)

  textarea.focus()
  const newCursorPos = start + prefix.length + (selected ? selected.length : 2)
  setTimeout(() => {
    textarea.setSelectionRange(newCursorPos, newCursorPos)
  }, 0)
}

function insertHeading() { insertMarkdown('## ') }
function insertBold() { insertMarkdown('**', '**') }
function insertItalic() { insertMarkdown('*', '*') }
function insertList() { insertMarkdown('\n- ') }
function insertCode() { insertMarkdown('`', '`') }
function insertLink() { insertMarkdown('[', '](url)') }

function renderMarkdown(text: string): string {
  if (!text) return ''
  const renderer = new marked.Renderer()
  renderer.link = function ({ href, text }: { href: string; text: string }) {
    return `<a href="${href}" target="_blank" rel="noopener noreferrer">${text}</a>`
  }
  return marked.parse(text, { breaks: true, renderer }) as string
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

function handleCoverSelect(event: Event) {
  const input = event.target as HTMLInputElement
  if (input.files?.[0]) {
    coverFile.value = input.files[0]
    coverPreview.value = URL.createObjectURL(input.files[0])
    existingCoverUrl.value = ''
  }
}

function removeCover() {
  coverFile.value = null
  coverPreview.value = ''
  existingCoverUrl.value = ''
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

async function handleSubmit() {
  console.log('[Publish] >>> handleSubmit called, isDrafting:', isDrafting.value)
  if (isDrafting.value) return
  if (!form.value.title || !form.value.categoryId || !form.value.description) {
    error.value = '请填写必填字段'
    return
  }
  if (form.value.resourceType === 'FILE' && selectedFiles.value.length === 0 && !isEdit.value) {
    error.value = '请上传至少一个文件'
    return
  }
  loading.value = true
  error.value = ''
  uploadProgress.value = 0
  try {
    const formData = new FormData()
    const data: any = {
      title: form.value.title,
      categoryId: form.value.categoryId,
      tags: form.value.tags,
      description: form.value.description,
      resourceType: form.value.resourceType,
      externalUrl: form.value.externalUrl || undefined,
    }
    // 编辑已有资源时，始终显式设置状态
    // 草稿 → PUBLISHED，已发布资源保持 PUBLISHED
    if (isEdit.value && editStatus.value === 'DRAFT') {
      data.status = 'PUBLISHED'
    } else if (isEdit.value) {
      data.status = editStatus.value || 'PUBLISHED'
    }
    console.log('[Publish] handleSubmit - isEdit:', isEdit.value, 'editStatus:', editStatus.value, 'sendStatus:', data.status)
    if (existingCoverUrl.value) {
      data.coverImageUrl = existingCoverUrl.value
    }
    formData.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }))
    if (form.value.resourceType === 'FILE' && selectedFiles.value.length > 0) {
      selectedFiles.value.forEach(file => formData.append('files', file))
    }
    if (coverFile.value) {
      formData.append('coverImage', coverFile.value)
    }

    if (isEdit.value && editId.value) {
      await resourceApi.update(editId.value, formData)
    } else {
      await resourceApi.create(formData)
    }
    router.push('/')
  } catch (e: any) {
    error.value = e.message || '发布失败'
  } finally {
    loading.value = false
  }
}

function handleDraft() {
  showDraftModal.value = true
}

async function confirmDraft() {
  console.log('[Publish] >>> confirmDraft called, isDrafting:', isDrafting.value)
  if (isDrafting.value) return
  isDrafting.value = true
  showDraftModal.value = false
  loading.value = true
  error.value = ''
  try {
    const formData = new FormData()
    const data: any = {
      title: form.value.title || '无标题草稿',
      categoryId: form.value.categoryId,
      tags: form.value.tags,
      description: form.value.description || '',
      resourceType: form.value.resourceType,
      externalUrl: form.value.externalUrl || undefined,
      status: 'DRAFT',
    }
    if (existingCoverUrl.value) {
      data.coverImageUrl = existingCoverUrl.value
    }
    formData.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }))
    if (form.value.resourceType === 'FILE' && selectedFiles.value.length > 0) {
      selectedFiles.value.forEach(file => formData.append('files', file))
    }
    if (coverFile.value) {
      formData.append('coverImage', coverFile.value)
    }

    if (isEdit.value && editId.value) {
      await resourceApi.update(editId.value, formData)
    } else {
      await resourceApi.create(formData)
    }
    router.push('/profile')
  } catch (e: any) {
    error.value = e.message || '保存草稿失败'
  } finally {
    loading.value = false
    isDrafting.value = false
  }
}

function handlePreview() {
  showPreview.value = !showPreview.value
}
</script>

<template>
  <div class="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
    <h1 class="text-2xl font-bold text-slate-800 mb-6">{{ isEdit ? '编辑资源' : '发布资源' }}</h1>

    <div v-if="error" class="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-600">
      {{ error }}
    </div>

    <div v-if="loading && isEdit" class="flex justify-center py-20">
      <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-primary-600"></div>
    </div>

    <form v-else @submit.prevent="handleSubmit" class="space-y-6">
      <!-- Title -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-6">
        <label class="block text-sm font-medium text-slate-700 mb-2">
          资源标题 <span class="text-red-500">*</span>
        </label>
        <input
          v-model="form.title"
          type="text"
          placeholder="请输入资源标题，最多 100 字符"
          maxlength="100"
          class="w-full border border-slate-300 rounded-lg px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
          required
        />
        <p class="text-xs text-slate-400 mt-1">{{ form.title.length }}/100</p>
      </div>

      <!-- Category -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-6">
        <label class="block text-sm font-medium text-slate-700 mb-2">
          资源分类 <span class="text-red-500">*</span>
        </label>
        <select
          v-model="form.categoryId"
          class="w-full border border-slate-300 rounded-lg px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent bg-white"
          required
        >
          <option :value="null">请选择分类</option>
          <template v-for="cat in categories" :key="cat.id">
            <option :value="cat.id">{{ cat.name }}</option>
            <option v-for="child in cat.children" :key="child.id" :value="child.id">
              &nbsp;&nbsp;├ {{ child.name }}
            </option>
          </template>
        </select>
      </div>

      <!-- Tags -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-6">
        <label class="block text-sm font-medium text-slate-700 mb-2">
          标签 <span class="text-slate-400 font-normal">(最多 10 个)</span>
        </label>
        <div class="flex flex-wrap items-center gap-2 p-3 border border-slate-300 rounded-lg min-h-[44px]">
          <span
            v-for="(tag, i) in form.tags"
            :key="i"
            class="inline-flex items-center gap-1 text-sm bg-primary-100 text-primary-700 px-2.5 py-1 rounded-md"
          >
            {{ tag }}
            <button
              type="button"
              @click="removeTag(i)"
              class="text-primary-400 hover:text-primary-600"
            >
              <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
              </svg>
            </button>
          </span>
          <input
            v-model="tagInput"
            type="text"
            placeholder="输入标签名称，回车添加..."
            class="flex-1 min-w-[120px] border-none outline-none text-sm py-1"
            @keyup.enter.prevent="addTag(tagInput)"
          />
        </div>
        <div class="flex flex-wrap gap-1.5 mt-2">
          <span class="text-xs text-slate-400">推荐：</span>
          <button
            v-for="tag in tags.slice(0, 8)"
            :key="tag.id"
            type="button"
            @click="addTag(tag.name)"
            class="text-xs bg-slate-100 text-slate-600 px-2 py-0.5 rounded hover:bg-primary-100 hover:text-primary-600 transition-colors"
          >
            + {{ tag.name }}
          </button>
        </div>
      </div>

      <!-- Description with Markdown Toolbar -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-6">
        <label class="block text-sm font-medium text-slate-700 mb-2">
          详细描述 <span class="text-red-500">*</span>
          <span class="text-slate-400 font-normal">(支持 Markdown)</span>
        </label>
        <div class="border border-slate-300 rounded-lg overflow-hidden">
          <!-- Toolbar -->
          <div class="flex items-center gap-1 px-3 py-2 border-b border-slate-200 bg-slate-50">
            <button
              type="button"
              @click="insertBold"
              class="p-1.5 rounded hover:bg-slate-200 transition-colors"
              title="加粗"
            >
              <svg class="w-4 h-4 text-slate-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 4h8a4 4 0 014 4 4 4 0 01-4 4H6z"/>
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 12h9a4 4 0 014 4 4 4 0 01-4 4H6z"/>
              </svg>
            </button>
            <button
              type="button"
              @click="insertItalic"
              class="p-1.5 rounded hover:bg-slate-200 transition-colors"
              title="斜体"
            >
              <svg class="w-4 h-4 text-slate-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 4h4m-2 0v16m-4-4h8"/>
              </svg>
            </button>
            <button
              type="button"
              @click="insertHeading"
              class="p-1.5 rounded hover:bg-slate-200 transition-colors"
              title="标题"
            >
              <svg class="w-4 h-4 text-slate-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h10M4 18h14"/>
              </svg>
            </button>
            <button
              type="button"
              @click="insertList"
              class="p-1.5 rounded hover:bg-slate-200 transition-colors"
              title="列表"
            >
              <svg class="w-4 h-4 text-slate-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 10h16M4 14h16M4 18h16"/>
              </svg>
            </button>
            <button
              type="button"
              @click="insertCode"
              class="p-1.5 rounded hover:bg-slate-200 transition-colors"
              title="代码"
            >
              <svg class="w-4 h-4 text-slate-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 20l4-16m4 4l4 4-4 4M6 16l-4-4 4-4"/>
              </svg>
            </button>
            <button
              type="button"
              @click="insertLink"
              class="p-1.5 rounded hover:bg-slate-200 transition-colors"
              title="链接"
            >
              <svg class="w-4 h-4 text-slate-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1"/>
              </svg>
            </button>
            <div class="w-px h-5 bg-slate-300 mx-1"></div>
            <button
              type="button"
              @click="handlePreview"
              :class="['p-1.5 rounded transition-colors', showPreview ? 'bg-primary-100 text-primary-600' : 'hover:bg-slate-200 text-slate-600']"
              title="预览"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/>
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z"/>
              </svg>
            </button>
          </div>
          <!-- Editor / Preview -->
          <textarea
            v-if="!showPreview"
            ref="textareaRef"
            v-model="form.description"
            rows="10"
            placeholder="请输入资源的详细描述，支持 Markdown 语法..."
            class="w-full px-4 py-3 text-sm focus:outline-none resize-none"
            required
          ></textarea>
          <div
            v-else
            class="px-4 py-3 min-h-[240px] prose prose-sm max-w-none"
            v-html="renderMarkdown(form.description)"
          ></div>
        </div>
      </div>

      <!-- Resource Type -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-6">
        <label class="block text-sm font-medium text-slate-700 mb-3">
          资源类型 <span class="text-red-500">*</span>
        </label>
        <div class="grid grid-cols-2 gap-4">
          <label class="relative cursor-pointer">
            <input type="radio" v-model="form.resourceType" value="FILE" class="peer sr-only">
            <div class="border-2 border-slate-200 rounded-xl p-4 text-center peer-checked:border-primary-500 peer-checked:bg-primary-50 hover:border-slate-300 transition-colors">
              <svg class="w-8 h-8 mx-auto mb-2 text-slate-400 peer-checked:text-primary-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"/>
              </svg>
              <p class="text-sm font-medium text-slate-700">文件上传</p>
              <p class="text-xs text-slate-400 mt-1">PDF/DOCX/PPT/MP4/ZIP</p>
            </div>
          </label>
          <label class="relative cursor-pointer">
            <input type="radio" v-model="form.resourceType" value="LINK" class="peer sr-only">
            <div class="border-2 border-slate-200 rounded-xl p-4 text-center peer-checked:border-primary-500 peer-checked:bg-primary-50 hover:border-slate-300 transition-colors">
              <svg class="w-8 h-8 mx-auto mb-2 text-slate-400 peer-checked:text-primary-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1"/>
              </svg>
              <p class="text-sm font-medium text-slate-700">外部链接</p>
              <p class="text-xs text-slate-400 mt-1">网页/视频/文档链接</p>
            </div>
          </label>
        </div>
      </div>

      <!-- External URL -->
      <div v-if="form.resourceType === 'LINK'" class="bg-white rounded-xl shadow-sm border border-slate-200 p-6">
        <label class="block text-sm font-medium text-slate-700 mb-2">资源链接</label>
        <input
          v-model="form.externalUrl"
          type="url"
          class="w-full border border-slate-300 rounded-lg px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
          placeholder="https://..."
        />
      </div>

      <!-- File Upload -->
      <div v-if="form.resourceType === 'FILE'" class="bg-white rounded-xl shadow-sm border border-slate-200 p-6">
        <label class="block text-sm font-medium text-slate-700 mb-2">
          上传文件 <span class="text-red-500">*</span>
        </label>
        <div
          class="border-2 border-dashed rounded-xl p-8 text-center transition-colors cursor-pointer"
          :class="isDragging ? 'border-primary-500 bg-primary-50' : 'border-slate-300 hover:border-primary-400'"
          @dragover.prevent="isDragging = true"
          @dragleave="isDragging = false"
          @drop.prevent="handleDrop"
          @click="($refs.fileInput as HTMLInputElement).click()"
        >
          <input
            ref="fileInput"
            type="file"
            multiple
            accept=".pdf,.docx,.pptx,.mp4,.zip"
            class="hidden"
            @change="handleFileSelect"
          />
          <div class="text-slate-400 mb-2">
            <svg class="w-10 h-10 mx-auto" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"/>
            </svg>
          </div>
          <p class="text-sm text-slate-500">拖拽文件到此处或点击选择</p>
          <p class="text-xs text-slate-400 mt-1">支持 PDF / DOCX / PPT / MP4 / ZIP，最大 500MB</p>
        </div>

        <div v-if="selectedFiles.length > 0" class="mt-3 space-y-2">
          <div
            v-for="(file, i) in selectedFiles"
            :key="i"
            class="flex items-center gap-3 p-3 bg-slate-50 rounded-lg"
          >
            <svg class="w-5 h-5 text-slate-400 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"/>
            </svg>
            <div class="flex-1 min-w-0">
              <p class="text-sm text-slate-700 truncate">{{ file.name }}</p>
              <p class="text-xs text-slate-400">{{ formatSize(file.size) }}</p>
            </div>
            <button
              type="button"
              @click="removeFile(i)"
              class="text-slate-400 hover:text-red-500 transition-colors"
            >
              <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
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

      <!-- Cover Image -->
      <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-6">
        <label class="block text-sm font-medium text-slate-700 mb-3">封面图片 <span class="text-slate-400 font-normal">(可选)</span></label>
        <div v-if="!coverPreview" class="w-48 h-32 border-2 border-dashed border-slate-300 rounded-xl flex items-center justify-center hover:border-primary-400 transition-colors cursor-pointer" @click="($refs.coverInput as HTMLInputElement).click()">
          <input ref="coverInput" type="file" accept="image/*" class="hidden" @change="handleCoverSelect" />
          <div class="text-center">
            <svg class="w-8 h-8 mx-auto text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"/>
            </svg>
            <p class="text-xs text-slate-400 mt-1">上传封面</p>
          </div>
        </div>
        <div v-else class="relative w-48 h-32 rounded-xl overflow-hidden border border-slate-200">
          <img :src="coverPreview" class="w-full h-full object-cover" />
          <button type="button" @click="removeCover" class="absolute top-1 right-1 w-6 h-6 bg-black/50 rounded-full flex items-center justify-center text-white hover:bg-black/70">
            <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/></svg>
          </button>
        </div>
      </div>

      <!-- AI Summary Preview -->
      <div class="bg-gradient-to-r from-purple-50 to-primary-50 border border-purple-200 rounded-xl p-4">
        <div class="flex items-center gap-2 mb-2">
          <div class="w-5 h-5 bg-purple-500 rounded flex items-center justify-center">
            <svg class="w-3 h-3 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"/>
            </svg>
          </div>
          <span class="text-xs font-semibold text-purple-700">AI 智能摘要</span>
          <span class="text-xs text-purple-500">(发布后自动生成)</span>
        </div>
        <p class="text-sm text-slate-500">发布资源后，AI 将自动分析您的描述内容，生成约 100 字的精准摘要，帮助其他用户快速了解资源内容。</p>
      </div>

      <!-- Submit -->
      <div class="flex items-center justify-between pt-4">
        <button type="button" @click="handleDraft" class="px-6 py-2.5 border border-slate-300 rounded-lg text-sm text-slate-600 hover:bg-slate-50">
          {{ isEdit && editStatus === 'DRAFT' ? '保存更改，暂不发布' : '存为草稿' }}
        </button>
        <div class="flex items-center gap-3">
          <button type="button" @click="handlePreview" class="px-6 py-2.5 border border-primary-300 text-primary-600 rounded-lg text-sm hover:bg-primary-50">
            {{ showPreview ? '编辑' : '预览' }}
          </button>
          <button
            type="submit"
            class="px-8 py-2.5 bg-primary-500 text-white rounded-lg text-sm font-medium hover:bg-primary-600 shadow-sm disabled:opacity-50 disabled:cursor-not-allowed"
            :disabled="loading"
          >
            {{ loading ? '发布中...' : (isEdit && editStatus !== 'DRAFT' ? '保存修改' : '发布资源') }}
          </button>
        </div>
      </div>
    </form>

    <!-- Draft Confirmation Modal -->
    <AppModal :visible="showDraftModal" :title="isEdit && editStatus === 'DRAFT' ? '保存更改' : '存为草稿'" @close="showDraftModal = false">
      <template #body>
        <p class="text-sm text-slate-600">{{ isEdit && editStatus === 'DRAFT' ? '确定保存当前更改吗？资源将保持草稿状态。' : '确定将当前内容保存为草稿吗？你可以在个人中心的"草稿箱"中找到它。' }}</p>
      </template>
      <template #footer>
        <button @click="showDraftModal = false" class="px-4 py-2 text-sm text-slate-600 border border-slate-300 rounded-lg hover:bg-slate-50">取消</button>
        <button @click="confirmDraft" class="px-4 py-2 text-sm text-white bg-primary-500 rounded-lg hover:bg-primary-600">确认保存</button>
      </template>
    </AppModal>
  </div>
</template>
