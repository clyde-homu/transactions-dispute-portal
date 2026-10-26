import { watchEffect } from 'vue';
import { useI18n } from 'vue-i18n';

/** Keeps the browser tab title in step with the page, which screen readers announce on navigation. */
export function usePageTitle(titleKey: string): void {
  const { t } = useI18n();
  watchEffect(() => {
    document.title = `${t(titleKey)} · ${t('app.name')}`;
  });
}
