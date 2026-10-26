<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';

import type { DisputeStatus } from '@/api/types';
import { STATUS_APPEARANCE } from '@/utils/disputeStatus';

const props = withDefaults(
  defineProps<{
    status: DisputeStatus;
    /** Optional lead-in such as "Disputed", shown as "Disputed · In review". */
    prefix?: string;
    /** The back office words some statuses differently ("Waiting for customer", not "Action needed"). */
    audience?: 'customer' | 'agent';
  }>(),
  { audience: 'customer' },
);

const { t } = useI18n();

const appearance = computed(() => STATUS_APPEARANCE[props.status]);
const label = computed(() => {
  const statusLabel = t(
    `${props.audience === 'agent' ? 'agentStatus' : 'disputeStatus'}.${props.status}`,
  );
  return props.prefix ? `${props.prefix} · ${statusLabel}` : statusLabel;
});
</script>

<template>
  <q-chip
    dense
    square
    text-color="white"
    class="status-chip"
    :icon="appearance.icon"
    :color="appearance.color"
    :label="label"
    :data-status="status"
  />
</template>
