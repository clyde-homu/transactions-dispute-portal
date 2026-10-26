<script setup lang="ts">
import { useI18n } from 'vue-i18n';

import type { DisputeStatus, TransactionStatus } from '@/api/types';

import StatusChip from './StatusChip.vue';

defineProps<{
  status: TransactionStatus;
  disputeStatus: DisputeStatus | null;
}>();

const { t } = useI18n();
</script>

<template>
  <span v-if="status === 'PENDING' || disputeStatus" class="transaction-badges">
    <q-chip
      v-if="status === 'PENDING'"
      dense
      square
      text-color="white"
      color="grey-8"
      icon="schedule"
      :label="t('transactionStatus.PENDING')"
      data-testid="pending-badge"
    />
    <StatusChip
      v-if="disputeStatus"
      :status="disputeStatus"
      :prefix="t('transactions.badge.disputed')"
      data-testid="dispute-badge"
    />
  </span>
</template>

<style scoped lang="scss">
.transaction-badges {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 0.25rem;
  align-items: center;
}
</style>
