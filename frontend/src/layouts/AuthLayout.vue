<script setup lang="ts">
import { useI18n } from 'vue-i18n';

const { t } = useI18n();
</script>

<template>
  <q-layout view="hHh lpR fFf">
    <q-page-container>
      <q-page class="auth-page column items-center justify-center q-pa-md">
        <div class="auth-brand row items-center no-wrap q-mb-lg">
          <q-icon name="verified_user" size="md" color="primary" aria-hidden="true" />
          <span class="text-h5 text-weight-medium q-ml-sm">{{ t('app.name') }}</span>
        </div>
        <main class="full-width row justify-center">
          <router-view />
        </main>
      </q-page>
    </q-page-container>
  </q-layout>
</template>
