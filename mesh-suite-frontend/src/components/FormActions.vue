<template>
  <div class="form-actions">
    <button type="button" class="btn-secondary" data-test="cancelar" @click="$emit('cancel')">Cancelar</button>
    <button type="submit" class="btn-primary" data-test="salvar" :disabled="saving">{{ saveLabel }}</button>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ saving?: boolean; saveLabel?: string }>(), {
  saving: false,
  saveLabel: 'Salvar',
})
defineEmits<{ cancel: [] }>()
</script>

<style scoped>
.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  font-family: var(--pm-font);
}

.btn-primary,
.btn-secondary {
  border-radius: 8px;
  padding: 10px 20px;
  font-size: 13px;
  font-weight: 600;
  font-family: var(--pm-font);
  cursor: pointer;
}

.btn-primary {
  background: var(--pm-accent);
  color: var(--pm-white);
  border: none;
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-secondary {
  background: var(--pm-white);
  color: var(--pm-text-dark);
  border: 1px solid var(--pm-border-light);
}
</style>
