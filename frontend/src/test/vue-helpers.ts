import { Notify, QLayout, QPageContainer, Quasar } from 'quasar';
import { defineComponent, h, type Plugin } from 'vue';
import { RouterView } from 'vue-router';
import { mount } from '@vue/test-utils';
import { createI18n } from 'vue-i18n';

import messages from '@/i18n';

export function createTestI18n() {
  return createI18n({ legacy: false, locale: 'en-ZA', fallbackLocale: 'en-ZA', messages });
}

/** Quasar itself, with the plugins the app registers in quasar.config.ts. */
export const quasarPlugin: [Plugin, Record<string, unknown>] = [Quasar, { plugins: { Notify } }];

/** Runs a composable inside a real component so it can use inject, lifecycle hooks and i18n. */
export function withSetup<T>(composable: () => T, plugins: (Plugin | [Plugin, ...unknown[]])[]): T {
  let outcome: { value: T } | undefined;
  mount(
    defineComponent({
      setup() {
        outcome = { value: composable() };
        return () => null;
      },
    }),
    { global: { plugins } },
  );
  if (!outcome) throw new Error('composable did not run');
  return outcome.value;
}

/**
 * Quasar reads the viewport once and then follows resize events (debounced by 16 ms), so a test
 * that needs a phone or desktop layout has to dispatch one and wait for it to settle.
 */
export async function setViewportWidth(width: number): Promise<void> {
  Object.defineProperty(window, 'innerWidth', { configurable: true, value: width });
  window.dispatchEvent(new Event('resize'));
  await new Promise((resolve) => setTimeout(resolve, 40));
}

/** jsdom has no ResizeObserver, which QLayout needs to measure its header and footer. */
export class ResizeObserverStub {
  observe(): void {}
  unobserve(): void {}
  disconnect(): void {}
}

/**
 * What a routed page needs around it: QPage refuses to render outside a QLayout. Mount this with
 * a router and the page under test appears through the same RouterView it uses in the app.
 */
export const RoutedPageHost = defineComponent({
  render: () =>
    h(QLayout, { view: 'hHh lpR fFf' }, () => h(QPageContainer, null, () => h(RouterView))),
});
