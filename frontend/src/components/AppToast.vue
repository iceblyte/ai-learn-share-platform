<script setup lang="ts">
import { ref, onUnmounted } from 'vue'

interface ToastItem {
  id: number
  message: string
  type: 'info' | 'success' | 'error' | 'warning'
}

const toasts = ref<ToastItem[]>([])
let nextId = 0
let timers = new Map<number, ReturnType<typeof setTimeout>>()

function show(message: string, type: ToastItem['type'] = 'info', duration = 3000) {
  const id = nextId++
  toasts.value.push({ id, message, type })
  const timer = setTimeout(() => remove(id), duration)
  timers.set(id, timer)
}

function remove(id: number) {
  toasts.value = toasts.value.filter(t => t.id !== id)
  const timer = timers.get(id)
  if (timer) {
    clearTimeout(timer)
    timers.delete(id)
  }
}

onUnmounted(() => {
  timers.forEach(t => clearTimeout(t))
  timers.clear()
})

defineExpose({ show })
</script>

<template>
  <div class="fixed top-4 left-1/2 -translate-x-1/2 z-[9999] flex flex-col items-center gap-2 pointer-events-none">
    <TransitionGroup
      enter-active-class="transition-all duration-300 ease-out"
      leave-active-class="transition-all duration-200 ease-in"
      enter-from-class="opacity-0 -translate-y-3 scale-95"
      leave-to-class="opacity-0 -translate-y-3 scale-95"
    >
      <div
        v-for="toast in toasts"
        :key="toast.id"
        class="pointer-events-auto flex items-center gap-3 px-4 py-3 rounded-xl shadow-lg border backdrop-blur-sm min-w-[280px] max-w-[420px]"
        :class="{
          'bg-blue-50/90 border-blue-200 text-blue-800': toast.type === 'info',
          'bg-emerald-50/90 border-emerald-200 text-emerald-800': toast.type === 'success',
          'bg-red-50/90 border-red-200 text-red-800': toast.type === 'error',
          'bg-amber-50/90 border-amber-200 text-amber-800': toast.type === 'warning',
        }"
      >
        <!-- Icon -->
        <svg v-if="toast.type === 'info'" class="w-5 h-5 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/>
        </svg>
        <svg v-else-if="toast.type === 'success'" class="w-5 h-5 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"/>
        </svg>
        <svg v-else-if="toast.type === 'error'" class="w-5 h-5 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/>
        </svg>
        <svg v-else class="w-5 h-5 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 16.5c-.77.833.192 2.5 1.732 2.5z"/>
        </svg>

        <span class="text-sm font-medium flex-1">{{ toast.message }}</span>

        <button @click="remove(toast.id)" class="shrink-0 p-0.5 rounded hover:bg-black/5 transition-colors">
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
          </svg>
        </button>
      </div>
    </TransitionGroup>
  </div>
</template>
