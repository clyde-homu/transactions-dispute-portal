<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';

import EmptyState from '@/components/EmptyState.vue';
import { usePageTitle } from '@/composables/usePageTitle';
import { homeLocationFor } from '@/router/home';
import { useAuthStore } from '@/stores/auth';

const { t } = useI18n();
const auth = useAuthStore();
usePageTitle('pages.notFound.title');

const isAgent = computed(() => auth.user?.role === 'AGENT');
</script>

<template>
  <q-page padding class="page-content">
    <h1 class="text-h5 q-mt-none">{{ t('pages.notFound.title') }}</h1>
    <EmptyState
      icon="explore_off"
      :title="t('pages.notFound.emptyTitle')"
      :description="
        isAgent ? t('pages.notFound.agentDescription') : t('pages.notFound.emptyDescription')
      "
    >
      <q-btn
        color="primary"
        unelevated
        no-caps
        :to="homeLocationFor(auth.user?.role ?? 'CUSTOMER')"
        :label="isAgent ? t('pages.notFound.agentAction') : t('pages.notFound.action')"
      />
    </EmptyState>
  </q-page>
</template>
