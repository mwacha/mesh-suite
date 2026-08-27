<template>
  <AppShell title="Permissões de Acesso">
    <PageHeader title="Permissões de Acesso" />
    <PermissionsTabs active="perfis" />

    <PermissionProfilesListView />
  </AppShell>
</template>

<script setup lang="ts">
import AppShell from '@/components/AppShell.vue'
import PageHeader from '@/components/PageHeader.vue'
import PermissionsTabs from '@/components/PermissionsTabs.vue'
import PermissionProfilesListView from '@/views/PermissionProfilesListView.vue'
</script>
