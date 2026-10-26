import { Notify } from 'quasar';
import { ref, type ShallowRef } from 'vue';
import { useI18n } from 'vue-i18n';

import { claimDispute, requestDisputeInfo, resolveDispute } from '@/api/agent';
import { toApiError } from '@/api/errors';
import type {
  AgentAction,
  AgentDisputeDetail,
  RequestInfoRequest,
  ResolveDisputeRequest,
} from '@/api/types';

import { useApiErrorMessage } from './useApiErrorMessage';

/** `done` and `conflict` both end the action (a dialog can close); `failed` leaves it open to fix. */
export type ActionOutcome = 'done' | 'conflict' | 'failed';

interface Options {
  /** The page's copy of the dispute; a successful action replaces it with the server's answer. */
  dispute: ShallowRef<AgentDisputeDetail | null>;
  /** Loads the dispute again, after another change won the race. */
  reload: () => Promise<void>;
}

/**
 * Claim, request information and resolve for one dispute, with the shared handling the three
 * need: one action in flight at a time, a toast on success, a refresh on a 409, and the
 * server's field errors kept for the dialog that sent them.
 */
export function useAgentActions({ dispute, reload }: Options) {
  const { t } = useI18n();
  const describeError = useApiErrorMessage();

  /** Which action is being sent, or null. */
  const busy = ref<AgentAction | null>(null);
  /** First message per JSON field from the last VALIDATION_FAILED. */
  const fieldErrors = ref<Record<string, string>>({});

  async function perform(
    action: AgentAction,
    call: (id: string) => Promise<AgentDisputeDetail>,
    success: (updated: AgentDisputeDetail) => string,
  ): Promise<ActionOutcome> {
    const current = dispute.value;
    if (!current || busy.value) return 'failed';

    busy.value = action;
    fieldErrors.value = {};
    try {
      const updated = await call(current.id);
      dispute.value = updated;
      Notify.create({ type: 'positive', message: success(updated) });
      return 'done';
    } catch (failure) {
      const problem = toApiError(failure);
      if (problem.code === 'INVALID_DISPUTE_TRANSITION' || problem.code === 'DISPUTE_MODIFIED') {
        // Someone else got there first: show what the dispute looks like now.
        Notify.create({ type: 'warning', message: t(`agentDispute.conflict.${problem.code}`) });
        await reload();
        return 'conflict';
      }
      if (problem.code === 'VALIDATION_FAILED' && Object.keys(problem.fieldErrors).length > 0) {
        fieldErrors.value = { ...problem.fieldErrors };
        return 'failed';
      }
      Notify.create({ type: 'negative', message: describeError(problem) });
      return 'failed';
    } finally {
      busy.value = null;
    }
  }

  function claim(): Promise<ActionOutcome> {
    return perform('CLAIM', claimDispute, (updated) =>
      t('agentDispute.claim.success', { reference: updated.reference }),
    );
  }

  function requestInfo(request: RequestInfoRequest): Promise<ActionOutcome> {
    return perform(
      'REQUEST_INFO',
      (id) => requestDisputeInfo(id, request),
      (updated) => t('agentDispute.requestInfo.success', { reference: updated.reference }),
    );
  }

  function resolve(request: ResolveDisputeRequest): Promise<ActionOutcome> {
    return perform(
      'RESOLVE',
      (id) => resolveDispute(id, request),
      (updated) =>
        t(`agentDispute.resolve.success.${request.outcome}`, { reference: updated.reference }),
    );
  }

  function clearFieldErrors(): void {
    fieldErrors.value = {};
  }

  return { busy, fieldErrors, claim, requestInfo, resolve, clearFieldErrors };
}
