import { Notify, Quasar } from 'quasar';
import { defineComponent, type Plugin } from 'vue';
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
