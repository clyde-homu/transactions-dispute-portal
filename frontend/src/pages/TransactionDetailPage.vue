<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRoute } from 'vue-router';

import { getTransaction } from '@/api/transactions';
import EmptyState from '@/components/EmptyState.vue';
import ErrorState from '@/components/ErrorState.vue';
import MoneyAmount from '@/components/MoneyAmount.vue';
import TransactionBadges from '@/components/TransactionBadges.vue';
import { useApiErrorMessage } from '@/composables/useApiErrorMessage';
import { useAsyncData } from '@/composables/useAsyncData';
import { usePageTitle } from '@/composables/usePageTitle';
import { formatDateTime } from '@/utils/datetime';

const { t } = useI18n();
const route = useRoute();
const describeError = useApiErrorMessage();
usePageTitle('pages.transactionDetail.title');

const transactionId = computed(() => {
  const param = route.params.id;
  return (Array.isArray(param) ? param[0] : param) ?? null;
});

const {
  data: transaction,
  loading,
  error,
  retry,
} = useAsyncData(
  () => transactionId.value,
  (id, signal) => getTransaction(id, signal),
);

// A made-up or foreign id is a dead end, not a fault the user can retry. The API answers 404
// for someone else's transaction and 400 for an id that is not a UUID at all.
const notFound = computed(
  () => error.value?.code === 'NOT_FOUND' || error.value?.code === 'VALIDATION_FAILED',
);

// Coming from the list, go back to it exactly as it was (filters and page live in its URL).
const history = window.history.state as { back?: unknown } | null;
const backTo =
  typeof history?.back === 'string' && /^\/transactions(\?|$)/.test(history.back)
    ? history.back
    : { name: 'transactions' };

const eligibility = computed(() => transaction.value?.eligibility ?? null);
const canDispute = computed(() => eligibility.value?.eligible === true);
const eligibilityTitle = computed(() => {
  if (canDispute.value) return t('transactionDetail.eligibility.eligibleTitle');
  return transaction.value?.disputeId
    ? t('transactionDetail.eligibility.existingDisputeTitle')
    : t('transactionDetail.eligibility.notEligibleTitle');
});
// The server's wording wins; the local text only covers a refusal that arrived without one.
const refusalMessage = computed(() => {
  const current = eligibility.value;
  if (current?.message) return current.message;
  return current?.reasonCode
    ? t(`transactionDetail.eligibility.reason.${current.reasonCode}`)
    : t('transactionDetail.eligibility.noReason');
});

const fields = computed(() => {
  const current = transaction.value;
  if (!current) return [];
  return [
    { key: 'postedAt', value: formatDateTime(current.postedAt) },
    { key: 'reference', value: current.reference, mono: true },
    { key: 'channel', value: t(`channel.${current.channel}`) },
    { key: 'category', value: current.merchantCategory },
    { key: 'status', value: t(`transactionStatus.${current.status}`) },
    {
      key: 'type',
      value: current.direction === 'DEBIT' ? t('money.debit') : t('money.credit'),
    },
  ];
});
</script>

<template>
  <q-page padding class="page-content">
    <q-btn
      flat
      no-caps
      icon="arrow_back"
      class="q-mb-sm"
      :label="t('transactionDetail.back')"
      :to="backTo"
      data-testid="back"
    />

    <h1 class="text-h5 q-mt-none">{{ t('pages.transactionDetail.title') }}</h1>

    <div
      v-if="loading"
      role="status"
      aria-busy="true"
      :aria-label="t('transactionDetail.loading')"
      data-testid="detail-skeleton"
    >
      <q-skeleton type="rect" height="220px" />
      <q-skeleton type="rect" height="120px" class="q-mt-md" />
    </div>

    <EmptyState
      v-else-if="notFound"
      icon="search_off"
      :title="t('transactionDetail.notFoundTitle')"
      :description="t('transactionDetail.notFoundDescription')"
    >
      <q-btn
        color="primary"
        unelevated
        no-caps
        :to="{ name: 'transactions' }"
        :label="t('transactionDetail.back')"
      />
    </EmptyState>

    <ErrorState
      v-else-if="error"
      :title="t('transactionDetail.errorTitle')"
      :message="describeError(error)"
      @retry="retry"
    />

    <template v-else-if="transaction">
      <q-card flat bordered :aria-label="t('transactionDetail.summary')">
        <q-card-section>
          <h2 class="text-h6 q-my-none" data-testid="merchant">{{ transaction.merchantName }}</h2>
          <div class="text-h4 q-mt-sm">
            <MoneyAmount
              :amount="transaction.amount"
              :currency="transaction.currency"
              :direction="transaction.direction"
            />
          </div>
          <TransactionBadges
            class="q-mt-sm"
            :status="transaction.status"
            :dispute-status="transaction.disputeStatus"
          />
        </q-card-section>
        <q-separator />
        <q-card-section>
          <dl class="detail-list q-my-none">
            <div v-for="field in fields" :key="field.key" class="detail-list__row">
              <dt class="muted">{{ t(`transactionDetail.fields.${field.key}`) }}</dt>
              <dd class="q-ma-none" :class="{ 'detail-list__mono': field.mono }">
                {{ field.value }}
              </dd>
            </div>
          </dl>
        </q-card-section>
      </q-card>

      <q-card
        flat
        bordered
        class="q-mt-md"
        role="region"
        aria-labelledby="eligibility-heading"
        data-testid="eligibility"
      >
        <q-card-section class="row no-wrap items-start">
          <q-icon
            :name="canDispute ? 'check_circle' : 'info'"
            :color="canDispute ? 'positive' : 'info'"
            size="md"
            class="q-mr-md"
            aria-hidden="true"
          />
          <div class="col">
            <h2 id="eligibility-heading" class="text-h6 q-my-none">{{ eligibilityTitle }}</h2>
            <p class="q-mt-sm q-mb-none">
              {{
                canDispute ? t('transactionDetail.eligibility.eligibleDescription') : refusalMessage
              }}
            </p>
          </div>
        </q-card-section>
        <q-card-actions v-if="canDispute || transaction.disputeId" class="q-px-md q-pb-md">
          <q-btn
            v-if="canDispute"
            color="primary"
            unelevated
            no-caps
            icon="report_problem"
            :to="{ name: 'dispute-new', params: { id: transaction.id } }"
            :label="t('transactionDetail.eligibility.disputeButton')"
            data-testid="dispute-button"
          />
          <q-btn
            v-if="transaction.disputeId"
            outline
            no-caps
            color="primary"
            icon="open_in_new"
            :to="{ name: 'dispute-detail', params: { id: transaction.disputeId } }"
            :label="t('transactionDetail.eligibility.viewDispute')"
            data-testid="view-dispute"
          />
        </q-card-actions>
      </q-card>
    </template>
  </q-page>
</template>

<style scoped lang="scss">
.detail-list__row {
  display: grid;
  grid-template-columns: 11rem 1fr;
  gap: 0.25rem 1rem;
  padding: 0.4rem 0;

  @media (width <= 599px) {
    grid-template-columns: 1fr;
  }
}

.detail-list__mono {
  font-family: ui-monospace, 'SFMono-Regular', Menlo, Consolas, monospace;
  overflow-wrap: anywhere;
}
</style>
