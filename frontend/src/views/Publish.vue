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

const tagInput = ref('')

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

async function handleSubmit() {
  if (!form.value.title || !form.value.categoryId || !form.value.description) {
    error.value = '请填写必填字段'
    return
  }
  loading.value = true
  error.value = ''
  try {
    await resourceApi.create({
      title: form.value.title,
      categoryId: form.value.categoryId,
      tags: form.value.tags,
      description: form.value.description,
      resourceType: form.value.resourceType,
      externalUrl: form.value.externalUrl || undefined,
    })
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

      <button type="submit" class="btn-primary w-full py-3" :disabled="loading">
        {{ loading ? '发布中...' : '发布资源' }}
      </button>
    </form>
  </div>
</template>
