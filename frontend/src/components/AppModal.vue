<script setup lang="ts">
defineProps<{
  visible: boolean
  title?: string
  width?: string
}>()

const emit = defineEmits<{
  close: []
  confirm: []
}>()
</script>

<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition-all duration-200"
      leave-active-class="transition-all duration-150"
      enter-from-class="opacity-0"
      leave-to-class="opacity-0"
    >
      <div v-if="visible" class="fixed inset-0 z-[9998] flex items-center justify-center p-4" @click.self="emit('close')">
        <!-- Backdrop -->
        <div class="absolute inset-0 bg-black/40 backdrop-blur-sm" @click="emit('close')"></div>

        <!-- Dialog -->
        <Transition
          enter-active-class="transition-all duration-300 ease-out"
          leave-active-class="transition-all duration-200 ease-in"
          enter-from-class="opacity-0 scale-95 translate-y-2"
          leave-to-class="opacity-0 scale-95 translate-y-2"
          appear
        >
          <div
            class="relative bg-white rounded-2xl shadow-2xl overflow-hidden"
            :style="{ width: width || '420px', maxWidth: '90vw' }"
          >
            <!-- Header -->
            <div v-if="title" class="flex items-center justify-between px-6 py-4 border-b border-slate-100">
              <h3 class="text-lg font-semibold text-slate-800">{{ title }}</h3>
              <button @click="emit('close')" class="p-1 rounded-lg hover:bg-slate-100 transition-colors">
                <svg class="w-5 h-5 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
                </svg>
              </button>
            </div>

            <!-- Body -->
            <div class="px-6 py-4">
              <slot />
            </div>

            <!-- Footer -->
            <div v-if="$slots.footer" class="px-6 py-4 bg-slate-50 border-t border-slate-100 flex items-center justify-end gap-3">
              <slot name="footer" />
            </div>
          </div>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>
