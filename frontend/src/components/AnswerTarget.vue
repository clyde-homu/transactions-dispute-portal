<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';

import { formatDate, isPastDay } from '@/utils/datetime';

const props = defineProps<{
  /** When the bank aims to have given the customer an answer. */
  dueAt: string;
}>();

const { t } = useI18n();

// A missed target is not announced to the customer as a failure; the wording just stays honest.
const message = computed(() =>
  t(isPastDay(props.dueAt) ? 'answerTarget.past' : 'answerTarget.upcoming', {
    date: formatDate(props.dueAt),
  }),
);
</script>

<template>
  <p class="answer-target">
    <q-icon name="schedule" size="xs" aria-hidden="true" />
    <span data-testid="answer-target-text">{{ message }}</span>
  </p>
</template>

<style scoped lang="scss">
.answer-target {
  display: flex;
  gap: 0.5rem;
  align-items: center;
  margin: 0;
}
</style>
