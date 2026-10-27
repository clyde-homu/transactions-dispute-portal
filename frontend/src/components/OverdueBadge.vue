<script setup lang="ts">
import { useI18n } from 'vue-i18n';

// Back office only. Customers see a target date, never a late flag (ADR 0007).
const { t } = useI18n();
</script>

<template>
  <q-chip
    dense
    square
    text-color="white"
    color="negative"
    icon="alarm"
    class="overdue-badge"
    :label="t('overdue.badge')"
  />
</template>
