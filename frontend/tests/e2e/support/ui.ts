import type { Locator, Page } from '@playwright/test';

export function heading(page: Page, name: string): Locator {
  return page.getByRole('heading', { name });
}

/** A list entry: a table row on wide screens, a linked card on phones. */
export function listEntry(page: Page, text: string): Locator {
  return page
    .getByRole('row')
    .filter({ hasText: text })
    .or(page.getByRole('list').getByRole('link').filter({ hasText: text }));
}

/** Rows hold a link named after one cell; on phones the whole card is the link. */
export async function openListEntry(entry: Locator, linkName: string): Promise<void> {
  const inner = entry.getByRole('link', { name: linkName });
  await ((await inner.count()) > 0 ? inner : entry).click();
}
