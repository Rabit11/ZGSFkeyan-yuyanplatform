<script setup lang="ts">
defineProps<{ title: string; desc?: string; color?: string }>()
</script>

<template>
  <div class="page-container page-header-container">
    <h2 class="page-title">{{ title }}</h2>
    <div v-if="desc" class="page-desc">{{ desc }}</div>
    <slot />
  </div>
</template>

<style scoped>
.page-header-container {
  display: flex;
  flex-direction: column;
}
</style>
