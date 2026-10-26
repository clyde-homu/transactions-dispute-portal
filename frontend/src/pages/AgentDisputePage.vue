<script setup lang="ts">
import { useQuasar } from 'quasar';
import { computed, nextTick, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRoute } from 'vue-router';

import { getAgentDispute } from '@/api/agent';
import type { RequestInfoRequest, ResolveDisputeRequest } from '@/api/types';
import AgentActionBar from '@/components/AgentActionBar.vue';
import AgentRequestInfoDialog from '@/components/AgentRequestInfoDialog.vue';
import AgentResolveDialog from '@/components/AgentResolveDialog.vue';
import DisputeTimeline from '@/components/DisputeTimeline.vue';
import EmptyState from '@/components/EmptyState.vue';
import ErrorState from '@/components/ErrorState.vue';
import MoneyAmount from '@/components/MoneyAmount.vue';
import StatusChip from '@/components/StatusChip.vue';
import { useAgentActions, type ActionOutcome } from '@/composables/useAgentActions';
import { useApiErrorMessage } from '@/composables/useApiErrorMessage';
import { useAsyncData } from '@/composables/useAsyncData';
import { usePageTitle } from '@/composables/usePageTitle';
import { formatDate, formatDateTime } from '@/utils/datetime';

const { t } = useI18n();
const $q = useQuasar();
const route = useRoute();
const describeError = useApiErrorMessage();
usePageTitle('pages.agentDispute.title');

const disputeId = computed(() => {
  const param = route.params.id;
  return (Array.isArray(param) ? param[0] : param) ?? null;
});

const {
  data: dispute,
  loading,
  error,
  retry,
} = useAsyncData(
  () => disputeId.value,
  (id, signal) => getAgentDispute(id, signal),
);

// A made-up id is a dead end, not a fault the agent can retry.
const notFound = computed(
  () => error.value?.code === 'NOT_FOUND' || error.value?.code === 'VALIDATION_FAILED',
);

const actions = useAgentActions({ dispute, reload: retry });

// What the server offers is all that is shown: no status or role logic is repeated here.
const availableActions = computed(() => dispute.value?.availableActions ?? []);
const hasActions = computed(() => availableActions.value.length > 0);

const isDesktop = computed(() => $q.screen.gt.sm);
const stickyActions = computed(() => !isDesktop.value && hasActions.value);

const requestInfoOpen = ref(false);
const resolveOpen = ref(false);

// The buttons that opened a dialog may be gone once the status changes, so give focus a new home.
async function focusHeading(): Promise<void> {
  await nextTick();
  document.getElementById('agent-dispute-heading')?.focus();
}

async function claim(): Promise<void> {
  if ((await actions.claim()) !== 'failed') await focusHeading();
}

async function sendRequestInfo(request: RequestInfoRequest): Promise<void> {
  const outcome: ActionOutcome = await actions.requestInfo(request);
  if (outcome !== 'failed') {
    requestInfoOpen.value = false;
    await focusHeading();
  }
}

async function sendResolve(request: ResolveDisputeRequest): Promise<void> {
  const outcome: ActionOutcome = await actions.resolve(request);
  if (outcome !== 'failed') {
    resolveOpen.value = false;
    await focusHeading();
  }
}

function openRequestInfo(): void {
  actions.clearFieldErrors();
  requestInfoOpen.value = true;
}

function openResolve(): void {
  actions.clearFieldErrors();
  resolveOpen.value = true;
}
</script>

<template>
  <q-page padding class="page-content" :class="{ 'page-content--sticky-actions': stickyActions }">
    <q-btn
      flat
      no-caps
      icon="arrow_back"
      class="q-mb-sm"
      :label="t('agentDispute.back')"
      :to="{ name: 'agent-queue' }"
      data-testid="back"
    />

    <h1 class="text-h5 q-mt-none">{{ t('pages.agentDispute.title') }}</h1>

    <div
      v-if="loading && !dispute"
      role="status"
      aria-busy="true"
      :aria-label="t('agentDispute.loading')"
      data-testid="detail-skeleton"
    >
      <q-skeleton type="rect" height="320px" />
      <q-skeleton type="rect" height="160px" class="q-mt-md" />
    </div>

    <EmptyState
      v-else-if="notFound"
      icon="search_off"
      :title="t('agentDispute.notFoundTitle')"
      :description="t('agentDispute.notFoundDescription')"
    >
      <q-btn
        color="primary"
        unelevated
        no-caps
        :to="{ name: 'agent-queue' }"
        :label="t('agentDispute.back')"
      />
    </EmptyState>

    <ErrorState
      v-else-if="error"
      :title="t('agentDispute.errorTitle')"
      :message="describeError(error)"
      @retry="retry"
    />

    <template v-else-if="dispute">
      <q-card flat bordered :aria-label="t('agentDispute.summary')">
        <q-card-section>
          <div class="row items-center q-gutter-sm">
            <h2
              id="agent-dispute-heading"
              tabindex="-1"
              class="text-h6 q-my-none reference"
              data-testid="reference"
            >
              {{ dispute.reference }}
            </h2>
            <StatusChip :status="dispute.status" audience="agent" data-testid="status" />
          </div>
          <p class="q-mt-sm q-mb-none" data-testid="reason">{{ dispute.reasonLabel }}</p>
        </q-card-section>
        <q-separator />
        <q-card-section>
          <dl class="detail-list q-my-none">
            <div class="detail-list__row">
              <dt class="muted">{{ t('agentDispute.fields.customer') }}</dt>
              <dd class="q-ma-none" data-testid="customer">{{ dispute.customerName }}</dd>
            </div>
            <div class="detail-list__row">
              <dt class="muted">{{ t('agentDispute.fields.merchant') }}</dt>
              <dd class="q-ma-none" data-testid="merchant">{{ dispute.merchantName }}</dd>
            </div>
            <div class="detail-list__row">
              <dt class="muted">{{ t('agentDispute.fields.disputedAmount') }}</dt>
              <dd class="q-ma-none text-weight-medium" data-testid="disputed-amount">
                <MoneyAmount :amount="dispute.disputedAmount" :currency="dispute.currency" />
              </dd>
            </div>
            <div class="detail-list__row">
              <dt class="muted">{{ t('agentDispute.fields.transaction') }}</dt>
              <dd class="q-ma-none" data-testid="transaction">
                {{
                  t('agentDispute.transactionValue', {
                    reference: dispute.transaction.reference,
                    date: formatDate(dispute.transaction.postedAt),
                  })
                }}
                &middot;
                <MoneyAmount
                  :amount="dispute.transaction.amount"
                  :currency="dispute.transaction.currency"
                  :direction="dispute.transaction.direction"
                />
              </dd>
            </div>
            <div class="detail-list__row">
              <dt class="muted">{{ t('agentDispute.fields.assignedAgent') }}</dt>
              <dd class="q-ma-none" data-testid="assigned-agent">
                <span v-if="dispute.assignedAgentName">{{ dispute.assignedAgentName }}</span>
                <span v-else class="muted">{{ t('agentDispute.unassigned') }}</span>
              </dd>
            </div>
            <div class="detail-list__row">
              <dt class="muted">{{ t('agentDispute.fields.raised') }}</dt>
              <dd class="q-ma-none">{{ formatDateTime(dispute.createdAt) }}</dd>
            </div>
            <div class="detail-list__row">
              <dt class="muted">{{ t('agentDispute.fields.updated') }}</dt>
              <dd class="q-ma-none">{{ formatDateTime(dispute.updatedAt) }}</dd>
            </div>
            <div class="detail-list__row">
              <dt class="muted">{{ t('agentDispute.fields.description') }}</dt>
              <dd class="q-ma-none detail-list__text" data-testid="description">
                {{ dispute.description }}
              </dd>
            </div>
          </dl>
        </q-card-section>
        <q-separator />
        <q-card-section v-if="!hasActions || isDesktop">
          <AgentActionBar
            v-if="hasActions"
            :actions="availableActions"
            :busy="actions.busy.value"
            @claim="claim"
            @request-info="openRequestInfo"
            @resolve="openResolve"
          />
          <p v-else class="muted q-my-none" data-testid="no-actions">
            {{ t('agentDispute.actions.none') }}
          </p>
        </q-card-section>
      </q-card>

      <q-card flat bordered class="q-mt-md">
        <q-card-section>
          <h2 class="text-h6 q-mt-none">{{ t('agentDispute.timeline.heading') }}</h2>
          <DisputeTimeline :events="dispute.timeline" variant="agent" />
        </q-card-section>
      </q-card>

      <q-page-sticky v-if="stickyActions" position="bottom" expand>
        <div
          class="sticky-actions full-width q-pa-sm"
          :class="$q.dark.isActive ? 'bg-dark' : 'bg-white'"
          data-testid="sticky-actions"
        >
          <AgentActionBar
            :actions="availableActions"
            :busy="actions.busy.value"
            @claim="claim"
            @request-info="openRequestInfo"
            @resolve="openResolve"
          />
        </div>
      </q-page-sticky>

      <AgentRequestInfoDialog
        v-model="requestInfoOpen"
        :busy="actions.busy.value === 'REQUEST_INFO'"
        :server-errors="actions.fieldErrors.value"
        @submit="sendRequestInfo"
      />
      <AgentResolveDialog
        v-model="resolveOpen"
        :busy="actions.busy.value === 'RESOLVE'"
        :server-errors="actions.fieldErrors.value"
        @submit="sendResolve"
      />
    </template>
  </q-page>
</template>

<style scoped lang="scss">
.reference {
  font-family: ui-monospace, 'SFMono-Regular', Menlo, Consolas, monospace;
}

// Room for the sticky action bar, so it never covers the last lines of the page.
.page-content--sticky-actions {
  padding-bottom: 6rem;
}

.sticky-actions {
  border-top: 1px solid var(--app-border);
}

.detail-list__row {
  display: grid;
  grid-template-columns: 11rem 1fr;
  gap: 0.25rem 1rem;
  padding: 0.4rem 0;

  @media (width <= 599px) {
    grid-template-columns: 1fr;
  }
}

.detail-list__text {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
