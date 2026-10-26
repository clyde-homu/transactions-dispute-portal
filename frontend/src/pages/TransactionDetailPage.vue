<script setup lang="ts">
import { useI18n } from 'vue-i18n';

import EmptyState from '@/components/EmptyState.vue';
import { usePageTitle } from '@/composables/usePageTitle';

const { t } = useI18n();
usePageTitle('pages.transactionDetail.title');
</script>

<template>
  <q-page padding class="page-content">
    <h1 class="text-h5 q-mt-none">{{ t('pages.transactionDetail.title') }}</h1>
    <EmptyState
      icon="description"
      :title="t('pages.transactionDetail.emptyTitle')"
      :description="t('pages.transactionDetail.emptyDescription')"
    />
  </q-page>
</template>
