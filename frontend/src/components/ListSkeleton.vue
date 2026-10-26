<script setup lang="ts">
withDefaults(
  defineProps<{
    /** Announced to screen readers while the content loads, for example "Loading transactions". */
    label: string;
    rows?: number;
  }>(),
  { rows: 6 },
);
</script>

<template>
  <div class="list-skeleton" role="status" aria-busy="true" :aria-label="label">
    <div v-for="row in rows" :key="row" class="list-skeleton__row row items-center no-wrap q-py-sm">
      <div class="col">
        <q-skeleton type="text" width="55%" />
        <q-skeleton type="text" width="35%" height="0.8rem" />
      </div>
      <q-skeleton type="text" width="5rem" />
    </div>
  </div>
</template>
