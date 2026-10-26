<script setup lang="ts">
import { useI18n } from 'vue-i18n';

import EmptyState from '@/components/EmptyState.vue';
import { usePageTitle } from '@/composables/usePageTitle';

const { t } = useI18n();
usePageTitle('pages.notFound.title');
</script>

<template>
  <q-page padding class="page-content">
    <h1 class="text-h5 q-mt-none">{{ t('pages.notFound.title') }}</h1>
    <EmptyState
      icon="explore_off"
      :title="t('pages.notFound.emptyTitle')"
      :description="t('pages.notFound.emptyDescription')"
    >
      <q-btn
        color="primary"
        unelevated
        no-caps
        :to="{ name: 'transactions' }"
        :label="t('pages.notFound.action')"
      />
    </EmptyState>
  </q-page>
</template>
