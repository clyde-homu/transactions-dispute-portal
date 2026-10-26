<script setup lang="ts">
import { useI18n } from 'vue-i18n';

import EmptyState from '@/components/EmptyState.vue';
import { usePageTitle } from '@/composables/usePageTitle';

const { t } = useI18n();
usePageTitle('pages.disputes.title');
</script>

<template>
  <q-page padding class="page-content">
    <h1 class="text-h5 q-mt-none">{{ t('pages.disputes.title') }}</h1>
    <EmptyState
      icon="folder_open"
      :title="t('pages.disputes.emptyTitle')"
      :description="t('pages.disputes.emptyDescription')"
    />
  </q-page>
</template>
