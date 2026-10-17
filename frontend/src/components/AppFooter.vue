<script setup lang="ts">
import BrandMark from '@/components/BrandMark.vue'
</script>

<template>
  <footer class="bg-white border-t border-slate-200 py-8 mt-12">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
      <div class="flex flex-col md:flex-row items-center justify-between space-y-4 md:space-y-0">
        <div class="flex items-center space-x-2">
          <BrandMark class="h-6 w-6" />
          <span class="text-sm text-slate-500">AI 个性化学习资源分享平台</span>
        </div>
        <div class="flex items-center space-x-6 text-sm text-slate-500">
          <a href="#" class="hover:text-primary-600">关于我们</a>
          <a href="#" class="hover:text-primary-600">使用帮助</a>
          <a href="#" class="hover:text-primary-600">隐私政策</a>
          <a href="#" class="hover:text-primary-600">联系我们</a>
        </div>
        <p class="text-sm text-slate-400">&copy; 2026 AI Learning Platform. All rights reserved.</p>
      </div>
    </div>
  </footer>
</template>
