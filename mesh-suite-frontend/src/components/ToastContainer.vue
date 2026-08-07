<template>
  <Teleport to="body">
    <div class="toast-container">
      <div
        v-for="toast in toasts"
        :key="toast.id"
        class="toast"
        :class="`toast-${toast.type}`"
        :data-test="`toast-${toast.type}`"
      >
        {{ toast.message }}
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { useToast } from '@/composables/useToast'

const { toasts } = useToast()
</script>

<style scoped>
.toast-container {
  position: fixed;
  top: 20px;
  right: 20px;
  z-index: 1000;
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-family: var(--pm-font);
}

.toast {
  padding: 12px 16px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  min-width: 240px;
  animation: toast-in 0.2s ease-out;
}

.toast-success {
  background: var(--pm-success-bg);
  color: var(--pm-success);
  border: 1px solid var(--pm-success);
}

.toast-error {
  background: var(--pm-error-bg);
  color: var(--pm-error);
  border: 1px solid var(--pm-error);
}

@keyframes toast-in {
  from {
    opacity: 0;
    transform: translateX(20px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}
</style>
