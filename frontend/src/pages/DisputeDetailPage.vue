<script setup lang="ts">
import { useI18n } from 'vue-i18n';

import EmptyState from '@/components/EmptyState.vue';
import { usePageTitle } from '@/composables/usePageTitle';

const { t } = useI18n();
usePageTitle('pages.disputeDetail.title');
</script>

<template>
  <q-page padding class="page-content">
    <h1 class="text-h5 q-mt-none">{{ t('pages.disputeDetail.title') }}</h1>
    <EmptyState
      icon="timeline"
      :title="t('pages.disputeDetail.emptyTitle')"
      :description="t('pages.disputeDetail.emptyDescription')"
    />
  </q-page>
</template>
