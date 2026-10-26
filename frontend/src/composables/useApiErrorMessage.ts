import { useI18n } from 'vue-i18n';

import { toApiError } from '@/api/errors';

/**
 * Returns a function that turns anything thrown by the API layer into a translated sentence.
 * It branches on the stable error code, never on the server's wording.
 */
export function useApiErrorMessage(): (error: unknown) => string {
  const { t } = useI18n();
  return (error) => t(`errors.${toApiError(error).code}`);
}
