import type { DisputeStatus } from '@/api/types';

/** Customer-facing order: from raised, through review, to the possible endings. */
export const DISPUTE_STATUSES: readonly DisputeStatus[] = [
  'SUBMITTED',
  'UNDER_REVIEW',
  'AWAITING_CUSTOMER_INFO',
  'RESOLVED_UPHELD',
  'RESOLVED_DECLINED',
  'WITHDRAWN',
];

// Each status has its own icon as well as a colour and a word, so none of the three has to carry
// the meaning alone. Every colour pairs with white text at AA contrast.
export const STATUS_APPEARANCE: Record<DisputeStatus, { icon: string; color: string }> = {
  SUBMITTED: { icon: 'send', color: 'info' },
  UNDER_REVIEW: { icon: 'manage_search', color: 'secondary' },
  AWAITING_CUSTOMER_INFO: { icon: 'priority_high', color: 'warning' },
  RESOLVED_UPHELD: { icon: 'check_circle', color: 'positive' },
  RESOLVED_DECLINED: { icon: 'cancel', color: 'negative' },
  WITHDRAWN: { icon: 'undo', color: 'grey-8' },
};
