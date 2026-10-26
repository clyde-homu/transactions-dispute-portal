<script setup lang="ts">
import { useI18n } from 'vue-i18n';

import EmptyState from '@/components/EmptyState.vue';
import { usePageTitle } from '@/composables/usePageTitle';

const { t } = useI18n();
usePageTitle('pages.disputeNew.title');
</script>

<template>
  <q-page padding class="page-content">
    <h1 class="text-h5 q-mt-none">{{ t('pages.disputeNew.title') }}</h1>
    <EmptyState
      icon="edit_note"
      :title="t('pages.disputeNew.emptyTitle')"
      :description="t('pages.disputeNew.emptyDescription')"
    />
  </q-page>
</template>
