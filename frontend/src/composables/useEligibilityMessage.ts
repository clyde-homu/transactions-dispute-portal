import { computed, type Ref } from 'vue';
import { useI18n } from 'vue-i18n';

import type { Eligibility } from '@/api/types';

/**
 * Why a transaction cannot be disputed, in words. The server's wording wins; the local text only
 * covers a refusal that arrived without one.
 */
export function useEligibilityMessage(eligibility: Ref<Eligibility | null>) {
  const { t } = useI18n();

  return computed(() => {
    const current = eligibility.value;
    if (current?.message) return current.message;
    return current?.reasonCode
      ? t(`transactionDetail.eligibility.reason.${current.reasonCode}`)
      : t('transactionDetail.eligibility.noReason');
  });
}
