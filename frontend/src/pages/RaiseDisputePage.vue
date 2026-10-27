<script setup lang="ts">
import { useQuasar } from 'quasar';
import { computed, nextTick, reactive, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRoute } from 'vue-router';

import { getTransaction } from '@/api/transactions';
import type { ReasonCategoryCode } from '@/api/types';
import AnswerTarget from '@/components/AnswerTarget.vue';
import EmptyState from '@/components/EmptyState.vue';
import ErrorState from '@/components/ErrorState.vue';
import MoneyAmount from '@/components/MoneyAmount.vue';
import RadioCardGroup from '@/components/RadioCardGroup.vue';
import { useApiErrorMessage } from '@/composables/useApiErrorMessage';
import { useAsyncData } from '@/composables/useAsyncData';
import { useDisputeForm } from '@/composables/useDisputeForm';
import { useEligibilityMessage } from '@/composables/useEligibilityMessage';
import { usePageTitle } from '@/composables/usePageTitle';
import type { DisputeStep } from '@/schemas/dispute';
import { DESCRIPTION_MAX } from '@/schemas/dispute';
import { useReasonsStore } from '@/stores/reasons';
import { formatDate } from '@/utils/datetime';
import { STATUS_APPEARANCE } from '@/utils/disputeStatus';
import { currencySymbol, formatCents, formatMoney, parseCents } from '@/utils/money';

const { t } = useI18n();
const $q = useQuasar();
const route = useRoute();
const describeError = useApiErrorMessage();
usePageTitle('pages.disputeNew.title');

// ---- the transaction being disputed ----------------------------------------------------------

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

// Only the first load blanks the page; a refresh after a conflict keeps what is on screen.
const firstLoad = computed(() => loading.value && !transaction.value);
const notFound = computed(
  () => error.value?.code === 'NOT_FOUND' || error.value?.code === 'VALIDATION_FAILED',
);
const eligibility = computed(() => transaction.value?.eligibility ?? null);
const eligible = computed(() => eligibility.value?.eligible === true);
const refusalMessage = useEligibilityMessage(eligibility);

// ---- reasons ---------------------------------------------------------------------------------

const reasons = useReasonsStore();
void reasons.load();

const CATEGORY_ICONS: Record<ReasonCategoryCode, string> = {
  FRAUD: 'security',
  AUTHORISATION: 'rule',
  PROCESSING_ERROR: 'sync_problem',
  CONSUMER: 'shopping_bag',
};
const categoryOptions = computed(() =>
  reasons.categories.map((category) => ({
    value: category.category,
    label: category.label,
    icon: CATEGORY_ICONS[category.category],
  })),
);

// ---- the form ----------------------------------------------------------------------------------

// Wrapping in reactive() lets the template read form.partial instead of form.partial.value.
const form = reactive(useDisputeForm(transaction));

// The three stages the success screen describes, reusing the status icons the customer will see.
const NEXT_STEPS = [
  { key: 'submitted', icon: STATUS_APPEARANCE.SUBMITTED.icon },
  { key: 'review', icon: STATUS_APPEARANCE.UNDER_REVIEW.icon },
  { key: 'outcome', icon: STATUS_APPEARANCE.RESOLVED_UPHELD.icon },
] as const;

const STEPS: readonly DisputeStep[] = ['category', 'reason', 'details', 'review'];
const step = ref<DisputeStep>('category');
const stepIndex = computed(() => STEPS.indexOf(step.value));
const vertical = computed(() => $q.screen.lt.md);

const reasonOptions = computed(() =>
  reasons.reasonsFor(form.category).map((reason) => ({
    value: reason.code,
    label: reason.label,
    hint: t('raiseDispute.steps.reason.sla', { days: reason.slaDays }, reason.slaDays),
  })),
);

const currency = computed(() => transaction.value?.currency ?? 'ZAR');
const fullAmountText = computed(() =>
  transaction.value ? formatMoney(transaction.value.amount, transaction.value.currency) : '',
);
const reviewAmount = computed(() => {
  const cents = parseCents(form.amount);
  return cents === null ? form.amount : formatCents(cents);
});
const reasonLabels = computed(() => reasons.describe(form.reasonCode));

function next(): void {
  const following = STEPS[stepIndex.value + 1];
  if (following && form.validateStep(step.value)) step.value = following;
}

function previous(): void {
  const preceding = STEPS[stepIndex.value - 1];
  if (preceding) step.value = preceding;
}

async function submit(): Promise<void> {
  await form.submit();
  // A rejected field sits on an earlier step, so take the customer to it.
  if (form.stepWithError) step.value = form.stepWithError;
}

const bannerTitle = computed(() => {
  const banner = form.banner;
  if (!banner) return '';
  if (banner.kind === 'failed') return t('raiseDispute.failedTitle');
  return banner.code === 'DISPUTE_ALREADY_EXISTS'
    ? t('raiseDispute.blockedTitle.DISPUTE_ALREADY_EXISTS')
    : t('raiseDispute.blockedTitle.TRANSACTION_NOT_ELIGIBLE');
});

// A 409 or 422 means the transaction changed under us: re-read it to learn about the dispute.
watch(
  () => form.banner,
  (banner) => {
    if (banner?.kind === 'blocked') void retry();
  },
);

// Moving between steps changes the content under the keyboard, so put focus on the new heading.
watch(step, async (name) => {
  await nextTick();
  document.getElementById(`step-heading-${name}`)?.focus();
});
watch(
  () => form.created,
  async (created) => {
    if (!created) return;
    await nextTick();
    document.getElementById('success-heading')?.focus();
  },
);
</script>

<template>
  <q-page padding class="page-content">
    <q-btn
      v-if="transactionId && !form.created"
      flat
      no-caps
      icon="arrow_back"
      class="q-mb-sm"
      :label="t('raiseDispute.back')"
      :to="{ name: 'transaction-detail', params: { id: transactionId } }"
      data-testid="back"
    />

    <h1 class="text-h5 q-mt-none">{{ t('pages.disputeNew.title') }}</h1>

    <div
      v-if="firstLoad"
      role="status"
      aria-busy="true"
      :aria-label="t('raiseDispute.loading')"
      data-testid="raise-skeleton"
    >
      <q-skeleton type="rect" height="260px" />
    </div>

    <EmptyState
      v-else-if="notFound"
      icon="search_off"
      :title="t('raiseDispute.notFoundTitle')"
      :description="t('raiseDispute.notFoundDescription')"
    >
      <q-btn
        color="primary"
        unelevated
        no-caps
        :to="{ name: 'transactions' }"
        :label="t('raiseDispute.backToTransactions')"
      />
    </EmptyState>

    <ErrorState
      v-else-if="error"
      :title="t('raiseDispute.errorTitle')"
      :message="describeError(error)"
      @retry="retry"
    />

    <template v-else-if="transaction">
      <q-banner
        v-if="form.banner"
        rounded
        role="alert"
        class="bg-negative text-white q-mb-md"
        data-testid="banner"
      >
        <template #avatar>
          <q-icon name="error_outline" aria-hidden="true" />
        </template>
        <div class="text-weight-medium">{{ bannerTitle }}</div>
        <div>{{ form.banner.kind === 'blocked' ? form.banner.detail : form.banner.message }}</div>
        <template v-if="form.banner.kind === 'blocked'" #action>
          <q-btn
            v-if="transaction.disputeId"
            flat
            no-caps
            color="white"
            :to="{ name: 'dispute-detail', params: { id: transaction.disputeId } }"
            :label="t('raiseDispute.viewDispute')"
            data-testid="banner-view-dispute"
          />
          <q-btn
            flat
            no-caps
            color="white"
            :to="{ name: 'transaction-detail', params: { id: transaction.id } }"
            :label="t('raiseDispute.back')"
            data-testid="banner-back"
          />
        </template>
      </q-banner>

      <!-- Submitted -->
      <q-card
        v-if="form.created"
        flat
        bordered
        class="success q-pa-lg"
        role="status"
        data-testid="success"
      >
        <div class="column items-center text-center">
          <q-icon name="check_circle" color="positive" size="64px" aria-hidden="true" />
          <h2 id="success-heading" tabindex="-1" class="text-h5 q-mt-md q-mb-sm">
            {{ t('raiseDispute.success.title') }}
          </h2>
          <p class="muted q-mb-xs">{{ t('raiseDispute.success.reference') }}</p>
          <p class="text-h4 reference q-my-none" data-testid="reference">
            {{ form.created.reference }}
          </p>
          <p class="muted q-mt-sm">{{ t('raiseDispute.success.referenceHint') }}</p>
          <AnswerTarget
            :due-at="form.created.dueAt"
            class="justify-center"
            data-testid="answer-target"
          />
        </div>

        <h3 class="text-h6">{{ t('raiseDispute.success.nextHeading') }}</h3>
        <ol class="next-steps q-pl-none" data-testid="next-steps">
          <li v-for="item in NEXT_STEPS" :key="item.key">
            <q-icon :name="item.icon" size="sm" aria-hidden="true" />
            <span>{{ t(`raiseDispute.success.next.${item.key}`) }}</span>
          </li>
        </ol>

        <div class="row justify-center q-gutter-sm q-mt-md">
          <q-btn
            color="primary"
            unelevated
            no-caps
            :to="{ name: 'dispute-detail', params: { id: form.created.id } }"
            :label="t('raiseDispute.success.viewDispute')"
            data-testid="view-dispute"
          />
          <q-btn
            outline
            no-caps
            color="primary"
            :to="{ name: 'transactions' }"
            :label="t('raiseDispute.success.backToTransactions')"
            data-testid="back-to-transactions"
          />
        </div>
      </q-card>

      <!-- Cannot be disputed -->
      <q-card
        v-else-if="!eligible && !form.banner"
        flat
        bordered
        role="region"
        aria-labelledby="not-eligible-heading"
        data-testid="not-eligible"
      >
        <q-card-section class="row no-wrap items-start">
          <q-icon name="info" color="info" size="md" class="q-mr-md" aria-hidden="true" />
          <div class="col">
            <h2 id="not-eligible-heading" class="text-h6 q-my-none">
              {{ t('raiseDispute.notEligibleTitle') }}
            </h2>
            <p class="q-mt-sm q-mb-none">{{ refusalMessage }}</p>
          </div>
        </q-card-section>
        <q-card-actions class="q-px-md q-pb-md">
          <q-btn
            v-if="transaction.disputeId"
            color="primary"
            unelevated
            no-caps
            :to="{ name: 'dispute-detail', params: { id: transaction.disputeId } }"
            :label="t('raiseDispute.viewDispute')"
            data-testid="view-existing-dispute"
          />
          <q-btn
            outline
            no-caps
            color="primary"
            :to="{ name: 'transaction-detail', params: { id: transaction.id } }"
            :label="t('raiseDispute.back')"
          />
        </q-card-actions>
      </q-card>

      <!-- The form -->
      <q-stepper
        v-else-if="eligible"
        v-model="step"
        flat
        bordered
        animated
        active-color="secondary"
        done-color="secondary"
        :vertical="vertical"
        data-testid="stepper"
      >
        <q-step
          name="category"
          icon="category"
          :title="t('raiseDispute.steps.category.label')"
          :done="stepIndex > 0"
        >
          <h2 id="step-heading-category" tabindex="-1" class="text-h6 q-mt-none q-mb-xs">
            {{ t('raiseDispute.steps.category.title') }}
          </h2>
          <p class="muted">{{ t('raiseDispute.steps.category.hint') }}</p>
          <div v-if="reasons.loading && !reasons.loaded" role="status" aria-busy="true">
            <q-skeleton type="rect" height="7rem" />
          </div>
          <ErrorState
            v-else-if="reasons.error"
            :title="t('raiseDispute.reasonsErrorTitle')"
            :message="describeError(reasons.error)"
            @retry="reasons.load()"
          />
          <RadioCardGroup
            v-else
            v-model="form.category"
            layout="tiles"
            :legend="t('raiseDispute.steps.category.title')"
            :options="categoryOptions"
            :error="form.errors.category"
          />
          <q-stepper-navigation>
            <q-btn
              color="primary"
              unelevated
              no-caps
              :label="t('raiseDispute.continue')"
              data-testid="continue"
              @click="next"
            />
          </q-stepper-navigation>
        </q-step>

        <q-step
          name="reason"
          icon="help_outline"
          :title="t('raiseDispute.steps.reason.label')"
          :done="stepIndex > 1"
        >
          <h2 id="step-heading-reason" tabindex="-1" class="text-h6 q-mt-none q-mb-xs">
            {{ t('raiseDispute.steps.reason.title') }}
          </h2>
          <p class="muted">{{ t('raiseDispute.steps.reason.hint') }}</p>
          <RadioCardGroup
            v-model="form.reasonCode"
            layout="list"
            :legend="t('raiseDispute.steps.reason.title')"
            :options="reasonOptions"
            :error="form.errors.reasonCode"
          />
          <q-stepper-navigation class="row q-gutter-sm">
            <q-btn
              flat
              no-caps
              :label="t('raiseDispute.previous')"
              data-testid="previous"
              @click="previous"
            />
            <q-btn
              color="primary"
              unelevated
              no-caps
              :label="t('raiseDispute.continue')"
              data-testid="continue"
              @click="next"
            />
          </q-stepper-navigation>
        </q-step>

        <q-step
          name="details"
          icon="edit_note"
          :title="t('raiseDispute.steps.details.label')"
          :done="stepIndex > 2"
        >
          <h2 id="step-heading-details" tabindex="-1" class="text-h6 q-mt-none q-mb-xs">
            {{ t('raiseDispute.steps.details.title') }}
          </h2>
          <p class="muted">
            {{
              t('raiseDispute.summary', {
                merchant: transaction.merchantName,
                amount: fullAmountText,
                date: formatDate(transaction.postedAt),
              })
            }}
          </p>

          <q-toggle
            v-model="form.partial"
            :label="t('raiseDispute.partialToggle')"
            data-testid="partial-toggle"
          />
          <template v-if="!form.partial">
            <p class="q-mt-sm q-mb-none" data-testid="full-amount">
              {{ t('raiseDispute.fullAmount', { amount: fullAmountText }) }}
            </p>
            <!-- A server complaint about the amount has no input to sit beside when the whole amount is used. -->
            <p
              v-if="form.errors.amount"
              class="error-text q-mt-xs q-mb-none"
              role="alert"
              data-testid="amount-error"
            >
              <q-icon name="error_outline" size="xs" aria-hidden="true" />
              {{ form.errors.amount }}
            </p>
          </template>
          <q-input
            v-else
            v-model="form.partialAmount"
            outlined
            inputmode="decimal"
            autocomplete="off"
            class="amount-input q-mt-sm"
            :label="t('raiseDispute.partialAmount')"
            :prefix="currencySymbol(currency)"
            :hint="t('raiseDispute.partialHint', { max: fullAmountText })"
            :error="Boolean(form.errors.amount)"
            :error-message="form.errors.amount"
            data-testid="partial-amount"
          />

          <q-input
            v-model="form.description"
            type="textarea"
            outlined
            autogrow
            counter
            class="q-mt-md"
            :maxlength="DESCRIPTION_MAX"
            :label="t('raiseDispute.description')"
            :hint="t('raiseDispute.descriptionHint')"
            :error="Boolean(form.errors.description)"
            :error-message="form.errors.description"
            data-testid="description"
          />

          <q-stepper-navigation class="row q-gutter-sm">
            <q-btn
              flat
              no-caps
              :label="t('raiseDispute.previous')"
              data-testid="previous"
              @click="previous"
            />
            <q-btn
              color="primary"
              unelevated
              no-caps
              :label="t('raiseDispute.continue')"
              data-testid="continue"
              @click="next"
            />
          </q-stepper-navigation>
        </q-step>

        <q-step
          name="review"
          icon="fact_check"
          :title="t('raiseDispute.steps.review.label')"
          :done="false"
        >
          <h2 id="step-heading-review" tabindex="-1" class="text-h6 q-mt-none q-mb-xs">
            {{ t('raiseDispute.steps.review.title') }}
          </h2>
          <p class="muted">{{ t('raiseDispute.steps.review.hint') }}</p>

          <dl class="review-list q-my-md" data-testid="review">
            <div>
              <dt class="muted">{{ t('raiseDispute.review.transaction') }}</dt>
              <dd class="q-ma-none">
                {{ transaction.merchantName }} &middot;
                {{ formatDate(transaction.postedAt) }} &middot; {{ transaction.reference }}
              </dd>
            </div>
            <div>
              <dt class="muted">{{ t('raiseDispute.review.category') }}</dt>
              <dd class="q-ma-none">{{ reasonLabels?.category }}</dd>
            </div>
            <div>
              <dt class="muted">{{ t('raiseDispute.review.reason') }}</dt>
              <dd class="q-ma-none">{{ reasonLabels?.reason }}</dd>
            </div>
            <div>
              <dt class="muted">{{ t('raiseDispute.review.amount') }}</dt>
              <dd class="q-ma-none text-weight-medium">
                <MoneyAmount :amount="reviewAmount" :currency="currency" />
              </dd>
            </div>
            <div>
              <dt class="muted">{{ t('raiseDispute.review.description') }}</dt>
              <dd class="q-ma-none review-list__text">{{ form.description.trim() }}</dd>
            </div>
          </dl>

          <q-checkbox
            v-model="form.declaration"
            :label="t('raiseDispute.declaration')"
            data-testid="declaration"
          />
          <p v-if="form.errors.declaration" class="error-text q-mt-xs q-mb-none" role="alert">
            <q-icon name="error_outline" size="xs" aria-hidden="true" />
            {{ form.errors.declaration }}
          </p>

          <q-stepper-navigation class="row q-gutter-sm">
            <q-btn
              flat
              no-caps
              :disable="form.submitting"
              :label="t('raiseDispute.previous')"
              data-testid="previous"
              @click="previous"
            />
            <q-btn
              color="primary"
              unelevated
              no-caps
              :loading="form.submitting"
              :disable="form.submitting"
              :label="t('raiseDispute.submit')"
              data-testid="submit"
              @click="submit"
            />
          </q-stepper-navigation>
        </q-step>
      </q-stepper>
    </template>
  </q-page>
</template>

<style scoped lang="scss">
.amount-input {
  max-width: 20rem;
}

.reference {
  font-family: ui-monospace, 'SFMono-Regular', Menlo, Consolas, monospace;
  letter-spacing: 0.02em;
  user-select: all;
}

.next-steps {
  display: grid;
  gap: 0.75rem;
  list-style: none;

  li {
    display: flex;
    gap: 0.75rem;
    align-items: flex-start;
  }
}

.review-list {
  display: grid;
  gap: 0.75rem;

  &__text {
    white-space: pre-wrap;
    overflow-wrap: anywhere;
  }
}
</style>
