import { computed, reactive, ref, shallowRef, watch, type Ref } from 'vue';
import { useI18n } from 'vue-i18n';

import { createDispute } from '@/api/disputes';
import { isApiError, type ApiErrorCode } from '@/api/errors';
import type { DisputeDetail, ReasonCategoryCode, ReasonCode, TransactionDetail } from '@/api/types';
import {
  createDisputeSchema,
  fieldIssues,
  STEP_FIELDS,
  toCreateRequest,
  type DisputeField,
  type DisputeStep,
} from '@/schemas/dispute';
import { formatMoney } from '@/utils/money';

import { useApiErrorMessage } from './useApiErrorMessage';

export interface FormBanner {
  /** "blocked": the server says this transaction cannot (or can no longer) be disputed. */
  kind: 'blocked' | 'failed';
  code: ApiErrorCode;
  /** Plain-language explanation. For "blocked" this is the server's own text, which names the dispute. */
  detail: string | null;
  message: string;
}

// The request fields a server can reject. The stepper then opens the step that holds the first one.
const SERVER_FIELDS: readonly DisputeField[] = ['reasonCode', 'amount', 'description'];
const STEP_ORDER: readonly DisputeStep[] = ['category', 'reason', 'details', 'review'];

/**
 * State and rules for the raise-dispute stepper: the answers so far, per-step validation, the
 * submit call, and how server problems are shown. The stepper itself only decides which step is
 * on screen.
 */
export function useDisputeForm(transaction: Ref<TransactionDetail | null>) {
  const { t } = useI18n();
  const describeError = useApiErrorMessage();

  const category = ref<ReasonCategoryCode | null>(null);
  const reasonCode = ref<ReasonCode | null>(null);
  const partial = ref(false);
  const partialAmount = ref('');
  const description = ref('');
  const declaration = ref(false);

  const errors = reactive<Partial<Record<DisputeField, string>>>({});
  const banner = shallowRef<FormBanner | null>(null);
  const submitting = ref(false);
  const created = shallowRef<DisputeDetail | null>(null);

  // Disputing the whole transaction is the default; the typed amount only counts when "part" is on.
  const amount = computed(() =>
    partial.value ? partialAmount.value : (transaction.value?.amount ?? ''),
  );
  const maxAmountText = computed(() =>
    transaction.value ? formatMoney(transaction.value.amount, transaction.value.currency) : '',
  );
  const schema = computed(() => createDisputeSchema(transaction.value?.amount ?? '0.00'));

  function values() {
    return {
      category: category.value,
      reasonCode: reasonCode.value,
      amount: amount.value,
      description: description.value,
      declaration: declaration.value,
    };
  }

  function clearError(field: DisputeField) {
    delete errors[field];
  }

  // An error describes what was typed, so it goes away as soon as the answer changes.
  watch(category, (next, previous) => {
    clearError('category');
    // A reason belongs to one category; a different category invalidates the earlier choice.
    if (previous !== null && next !== previous) {
      reasonCode.value = null;
    }
  });
  watch(reasonCode, () => clearError('reasonCode'));
  watch([partial, partialAmount], () => clearError('amount'));
  watch(description, () => clearError('description'));
  watch(declaration, () => clearError('declaration'));

  function translate(issue: string): string {
    return t(issue, { max: maxAmountText.value });
  }

  /** Validates one step, shows its problems inline, and says whether the step may be left. */
  function validateStep(step: DisputeStep): boolean {
    const issues = fieldIssues(schema.value, values(), STEP_FIELDS[step]);
    for (const field of STEP_FIELDS[step]) {
      if (issues[field]) errors[field] = translate(issues[field]);
      else clearError(field);
    }
    return Object.keys(issues).length === 0;
  }

  /** The earliest step with a problem showing, or null. */
  const stepWithError = computed<DisputeStep | null>(
    () => STEP_ORDER.find((step) => STEP_FIELDS[step].some((field) => errors[field])) ?? null,
  );

  function applyServerError(error: unknown): void {
    if (!isApiError(error)) throw error;

    if (error.code === 'DISPUTE_ALREADY_EXISTS' || error.code === 'TRANSACTION_NOT_ELIGIBLE') {
      banner.value = {
        kind: 'blocked',
        code: error.code,
        detail: error.message,
        message: describeError(error),
      };
      return;
    }

    let mapped = false;
    for (const [field, message] of Object.entries(error.fieldErrors)) {
      const known = SERVER_FIELDS.find((candidate) => candidate === field);
      if (known) {
        errors[known] = message;
        mapped = true;
      }
    }
    if (!mapped) {
      banner.value = {
        kind: 'failed',
        code: error.code,
        detail: null,
        message: describeError(error),
      };
    }
  }

  /** Validates every step, then sends the dispute. Safe to call twice: the second call is ignored. */
  async function submit(): Promise<void> {
    const current = transaction.value;
    if (submitting.value || !current) return;

    banner.value = null;
    const allValid = STEP_ORDER.map((step) => validateStep(step)).every(Boolean);
    if (!allValid) return;

    const parsed = schema.value.parse(values());
    submitting.value = true;
    try {
      created.value = await createDispute(
        toCreateRequest(current.id, {
          reasonCode: parsed.reasonCode,
          amount: parsed.amount,
          description: parsed.description,
        }),
      );
    } catch (error) {
      applyServerError(error);
    } finally {
      submitting.value = false;
    }
  }

  return {
    category,
    reasonCode,
    partial,
    partialAmount,
    description,
    declaration,
    amount,
    errors,
    banner,
    submitting,
    created,
    stepWithError,
    validateStep,
    submit,
  };
}
