// @vitest-environment node
import { describe, expect, it } from 'vitest';

import { DISPUTE_STATUSES, isOpenStatus } from './disputeStatus';

describe('isOpenStatus', () => {
  it.each(['SUBMITTED', 'UNDER_REVIEW', 'AWAITING_CUSTOMER_INFO'] as const)(
    'treats %s as open',
    (status) => {
      expect(isOpenStatus(status)).toBe(true);
    },
  );

  it.each(['RESOLVED_UPHELD', 'RESOLVED_DECLINED', 'WITHDRAWN'] as const)(
    'treats %s as closed',
    (status) => {
      expect(isOpenStatus(status)).toBe(false);
    },
  );

  it('has exactly three open statuses out of the six', () => {
    expect(DISPUTE_STATUSES).toHaveLength(6);
    expect(DISPUTE_STATUSES.filter(isOpenStatus)).toHaveLength(3);
  });
});
