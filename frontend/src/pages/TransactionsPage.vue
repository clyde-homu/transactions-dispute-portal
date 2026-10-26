<script setup lang="ts">
import { useI18n } from 'vue-i18n';

import EmptyState from '@/components/EmptyState.vue';
import { usePageTitle } from '@/composables/usePageTitle';

const { t } = useI18n();
usePageTitle('pages.transactions.title');
</script>

<template>
  <q-page padding class="page-content">
    <h1 class="text-h5 q-mt-none">{{ t('pages.transactions.title') }}</h1>
    <EmptyState
      icon="receipt_long"
      :title="t('pages.transactions.emptyTitle')"
      :description="t('pages.transactions.emptyDescription')"
    />
  </q-page>
</template>
