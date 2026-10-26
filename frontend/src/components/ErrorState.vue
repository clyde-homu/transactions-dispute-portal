<script setup lang="ts">
import { useI18n } from 'vue-i18n';

defineProps<{
  title: string;
  message: string;
}>();

defineEmits<{ retry: [] }>();

const { t } = useI18n();
</script>

<template>
  <section class="error-state column items-center text-center q-pa-lg" role="alert">
    <q-icon name="error_outline" size="56px" color="negative" aria-hidden="true" />
    <h2 class="text-h6 q-mt-md q-mb-xs">{{ title }}</h2>
    <p class="error-state__message muted q-mb-md">{{ message }}</p>
    <q-btn
      color="primary"
      unelevated
      no-caps
      icon="refresh"
      :label="t('common.retry')"
      data-testid="retry"
      @click="$emit('retry')"
    />
  </section>
</template>

<style scoped lang="scss">
.error-state__message {
  max-width: 36rem;
}
</style>
