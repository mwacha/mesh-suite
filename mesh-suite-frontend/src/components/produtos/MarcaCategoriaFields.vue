<template>
  <TextField v-model="marca" label="Marca" />
  <TextField v-model="categoria" label="Categoria" />
</template>

<script setup lang="ts">
import TextField from '@/components/TextField.vue'

const marca = defineModel<string>('marca', { required: true })
const categoria = defineModel<string>('categoria', { required: true })
</script>
