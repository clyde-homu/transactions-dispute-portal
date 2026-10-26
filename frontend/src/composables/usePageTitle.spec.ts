import { describe, expect, it } from 'vitest';

import { createTestI18n, withSetup } from '@/test/vue-helpers';

import { usePageTitle } from './usePageTitle';

describe('usePageTitle', () => {
  it('sets the document title to the page name followed by the product name', () => {
    withSetup(() => usePageTitle('pages.disputes.title'), [createTestI18n()]);

    expect(document.title).toBe('Your disputes · Dispute Portal');
  });
});
