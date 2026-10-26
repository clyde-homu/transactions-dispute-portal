<script setup lang="ts">
import { Notify } from 'quasar';
import { computed, nextTick, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRoute } from 'vue-router';

import { getDispute, withdrawDispute } from '@/api/disputes';
import { isApiError } from '@/api/errors';
import DisputeTimeline from '@/components/DisputeTimeline.vue';
import EmptyState from '@/components/EmptyState.vue';
import ErrorState from '@/components/ErrorState.vue';
import MoneyAmount from '@/components/MoneyAmount.vue';
import StatusChip from '@/components/StatusChip.vue';
import { useApiErrorMessage } from '@/composables/useApiErrorMessage';
import { useAsyncData } from '@/composables/useAsyncData';
import { usePageTitle } from '@/composables/usePageTitle';
import { formatDateTime } from '@/utils/datetime';
import { parseCents } from '@/utils/money';

const { t } = useI18n();
const route = useRoute();
const describeError = useApiErrorMessage();
usePageTitle('pages.disputeDetail.title');

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
  (id, signal) => getDispute(id, signal),
);

// A made-up or foreign id is a dead end, not a fault the user can retry.
const notFound = computed(
  () => error.value?.code === 'NOT_FOUND' || error.value?.code === 'VALIDATION_FAILED',
);

const isFullAmount = computed(() => {
  const current = dispute.value;
  return (
    current !== null &&
    parseCents(current.disputedAmount) === parseCents(current.transaction.amount)
  );
});

// ---- withdraw ----------------------------------------------------------------------------------

const confirmOpen = ref(false);
const withdrawing = ref(false);

async function withdraw(): Promise<void> {
  const current = dispute.value;
  if (!current || withdrawing.value) return;

  withdrawing.value = true;
  try {
    // The response is the updated dispute, so there is nothing more to fetch.
    dispute.value = await withdrawDispute(current.id);
    confirmOpen.value = false;
    Notify.create({
      type: 'positive',
      message: t('disputeDetail.withdraw.success', { reference: current.reference }),
    });
    // The button that opened the dialog is gone now, so give focus a new home.
    await nextTick();
    document.getElementById('dispute-heading')?.focus();
  } catch (failure) {
    confirmOpen.value = false;
    if (isApiError(failure) && failure.code === 'INVALID_DISPUTE_TRANSITION') {
      // Someone (or something) closed it first; show what it looks like now.
      Notify.create({ type: 'warning', message: t('disputeDetail.withdraw.conflict') });
      await retry();
    } else {
      Notify.create({ type: 'negative', message: describeError(failure) });
    }
  } finally {
    withdrawing.value = false;
  }
}
</script>

<template>
  <q-page padding class="page-content">
    <q-btn
      flat
      no-caps
      icon="arrow_back"
      class="q-mb-sm"
      :label="t('disputeDetail.back')"
      :to="{ name: 'disputes' }"
      data-testid="back"
    />

    <h1 class="text-h5 q-mt-none">{{ t('pages.disputeDetail.title') }}</h1>

    <div
      v-if="loading && !dispute"
      role="status"
      aria-busy="true"
      :aria-label="t('disputeDetail.loading')"
      data-testid="detail-skeleton"
    >
      <q-skeleton type="rect" height="260px" />
      <q-skeleton type="rect" height="160px" class="q-mt-md" />
    </div>

    <EmptyState
      v-else-if="notFound"
      icon="search_off"
      :title="t('disputeDetail.notFoundTitle')"
      :description="t('disputeDetail.notFoundDescription')"
    >
      <q-btn
        color="primary"
        unelevated
        no-caps
        :to="{ name: 'disputes' }"
        :label="t('disputeDetail.back')"
      />
    </EmptyState>

    <ErrorState
      v-else-if="error"
      :title="t('disputeDetail.errorTitle')"
      :message="describeError(error)"
      @retry="retry"
    />

    <template v-else-if="dispute">
      <q-card flat bordered :aria-label="t('disputeDetail.summary')">
        <q-card-section>
          <div class="row items-center q-gutter-sm">
            <h2
              id="dispute-heading"
              tabindex="-1"
              class="text-h6 q-my-none reference"
              data-testid="reference"
            >
              {{ dispute.reference }}
            </h2>
            <StatusChip :status="dispute.status" data-testid="status" />
          </div>
          <p class="q-mt-sm q-mb-none">{{ dispute.reasonLabel }}</p>
        </q-card-section>
        <q-separator />
        <q-card-section>
          <dl class="detail-list q-my-none">
            <div class="detail-list__row">
              <dt class="muted">{{ t('disputeDetail.fields.disputedAmount') }}</dt>
              <dd class="q-ma-none text-weight-medium" data-testid="disputed-amount">
                <MoneyAmount :amount="dispute.disputedAmount" :currency="dispute.currency" />
                <span v-if="isFullAmount" class="muted text-weight-regular">
                  &middot; {{ t('disputeDetail.fullAmount') }}
                </span>
              </dd>
            </div>
            <div class="detail-list__row">
              <dt class="muted">{{ t('disputeDetail.fields.transactionAmount') }}</dt>
              <dd class="q-ma-none" data-testid="transaction-amount">
                <MoneyAmount
                  :amount="dispute.transaction.amount"
                  :currency="dispute.transaction.currency"
                  :direction="dispute.transaction.direction"
                />
              </dd>
            </div>
            <div class="detail-list__row">
              <dt class="muted">{{ t('disputeDetail.fields.merchant') }}</dt>
              <dd class="q-ma-none">{{ dispute.merchantName }}</dd>
            </div>
            <div class="detail-list__row">
              <dt class="muted">{{ t('disputeDetail.fields.raised') }}</dt>
              <dd class="q-ma-none">{{ formatDateTime(dispute.createdAt) }}</dd>
            </div>
            <div class="detail-list__row">
              <dt class="muted">{{ t('disputeDetail.fields.updated') }}</dt>
              <dd class="q-ma-none">{{ formatDateTime(dispute.updatedAt) }}</dd>
            </div>
            <div class="detail-list__row">
              <dt class="muted">{{ t('disputeDetail.fields.description') }}</dt>
              <dd class="q-ma-none detail-list__text" data-testid="description">
                {{ dispute.description }}
              </dd>
            </div>
          </dl>
        </q-card-section>
        <q-card-actions class="q-px-md q-pb-md">
          <q-btn
            outline
            no-caps
            color="primary"
            icon="receipt_long"
            :to="{ name: 'transaction-detail', params: { id: dispute.transaction.id } }"
            :label="t('disputeDetail.viewTransaction')"
            data-testid="view-transaction"
          />
          <q-btn
            v-if="dispute.canWithdraw"
            outline
            no-caps
            color="negative"
            icon="undo"
            :label="t('disputeDetail.withdraw.button')"
            data-testid="withdraw"
            @click="confirmOpen = true"
          />
        </q-card-actions>
      </q-card>

      <q-card flat bordered class="q-mt-md">
        <q-card-section>
          <h2 class="text-h6 q-mt-none">{{ t('disputeDetail.timeline.heading') }}</h2>
          <DisputeTimeline :events="dispute.timeline" />
        </q-card-section>
      </q-card>

      <q-dialog v-model="confirmOpen" aria-labelledby="withdraw-title">
        <q-card class="confirm-card" data-testid="confirm-dialog">
          <q-card-section>
            <h2 id="withdraw-title" class="text-h6 q-my-none">
              {{ t('disputeDetail.withdraw.title') }}
            </h2>
            <p class="q-mt-sm q-mb-none">
              {{ t('disputeDetail.withdraw.message', { reference: dispute.reference }) }}
            </p>
          </q-card-section>
          <q-card-actions align="right">
            <q-btn
              v-close-popup
              flat
              no-caps
              :disable="withdrawing"
              :label="t('disputeDetail.withdraw.keep')"
              data-testid="keep"
            />
            <q-btn
              color="negative"
              unelevated
              no-caps
              :loading="withdrawing"
              :disable="withdrawing"
              :label="t('disputeDetail.withdraw.confirm')"
              data-testid="confirm-withdraw"
              @click="withdraw"
            />
          </q-card-actions>
        </q-card>
      </q-dialog>
    </template>
  </q-page>
</template>

<style scoped lang="scss">
.reference {
  font-family: ui-monospace, 'SFMono-Regular', Menlo, Consolas, monospace;
}

.confirm-card {
  width: 100%;
  max-width: 28rem;
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
