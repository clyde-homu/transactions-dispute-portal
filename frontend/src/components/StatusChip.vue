<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';

import type { DisputeStatus } from '@/api/types';

const props = defineProps<{
  status: DisputeStatus;
  /** Optional lead-in such as "Disputed", shown as "Disputed · In review". */
  prefix?: string;
}>();

const { t } = useI18n();

// Each status has its own icon as well as a colour and a word, so none of the three has to carry
// the meaning alone. Every colour pairs with white text at AA contrast.
const APPEARANCE: Record<DisputeStatus, { icon: string; color: string }> = {
  SUBMITTED: { icon: 'send', color: 'info' },
  UNDER_REVIEW: { icon: 'manage_search', color: 'secondary' },
  AWAITING_CUSTOMER_INFO: { icon: 'priority_high', color: 'warning' },
  RESOLVED_UPHELD: { icon: 'check_circle', color: 'positive' },
  RESOLVED_DECLINED: { icon: 'cancel', color: 'negative' },
  WITHDRAWN: { icon: 'undo', color: 'grey-8' },
};

const appearance = computed(() => APPEARANCE[props.status]);
const label = computed(() => {
  const statusLabel = t(`disputeStatus.${props.status}`);
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
