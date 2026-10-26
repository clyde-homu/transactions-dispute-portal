<script setup lang="ts">
import { computed } from 'vue';

import { formatMoney, type MoneyDirection } from '@/utils/money';

const props = withDefaults(
  defineProps<{
    /** Decimal string from the API, e.g. "1250.00". */
    amount: string;
    currency?: string;
    /** When set to DEBIT the amount gets a leading minus; CREDIT is shown plain. */
    direction?: MoneyDirection;
  }>(),
  { currency: 'ZAR' },
);

const formatted = computed(() => formatMoney(props.amount, props.currency, props.direction));
</script>

<template>
  <span class="money">{{ formatted }}</span>
</template>
