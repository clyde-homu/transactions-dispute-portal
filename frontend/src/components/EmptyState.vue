<script setup lang="ts">
defineProps<{
  icon: string;
  title: string;
  description?: string;
}>();
</script>

<template>
  <section class="empty-state column items-center text-center q-pa-lg">
    <q-icon :name="icon" size="56px" color="primary" aria-hidden="true" />
    <h2 class="text-h6 q-mt-md q-mb-xs">{{ title }}</h2>
    <p v-if="description" class="empty-state__description muted q-mb-none">{{ description }}</p>
    <div v-if="$slots.default" class="q-mt-md">
      <slot />
    </div>
  </section>
</template>

<style scoped lang="scss">
.empty-state__description {
  max-width: 36rem;
}
</style>
