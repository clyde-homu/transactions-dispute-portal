<script setup lang="ts">
import { useI18n } from 'vue-i18n';

import type { AgentAction } from '@/api/types';

defineProps<{
  /** What the server says this agent may do now; nothing else is offered. */
  actions: readonly AgentAction[];
  /** The action being sent, which shows a spinner while every button is disabled. */
  busy: AgentAction | null;
}>();

defineEmits<{ claim: []; requestInfo: []; resolve: [] }>();

const { t } = useI18n();
</script>

<template>
  <div
    class="row q-gutter-sm items-center"
    role="group"
    :aria-label="t('agentDispute.actions.label')"
  >
    <q-btn
      v-if="actions.includes('CLAIM')"
      color="primary"
      unelevated
      no-caps
      icon="front_hand"
      :label="t('agentDispute.actions.claim')"
      :loading="busy === 'CLAIM'"
      :disable="busy !== null"
      data-testid="action-claim"
      @click="$emit('claim')"
    />
    <q-btn
      v-if="actions.includes('REQUEST_INFO')"
      outline
      color="primary"
      no-caps
      icon="help_outline"
      :label="t('agentDispute.actions.requestInfo')"
      :disable="busy !== null"
      data-testid="action-request-info"
      @click="$emit('requestInfo')"
    />
    <q-btn
      v-if="actions.includes('RESOLVE')"
      color="primary"
      unelevated
      no-caps
      icon="task_alt"
      :label="t('agentDispute.actions.resolve')"
      :disable="busy !== null"
      data-testid="action-resolve"
      @click="$emit('resolve')"
    />
  </div>
</template>
