import { reactive, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRoute, useRouter } from 'vue-router';

import { isApiError, toApiError } from '@/api/errors';
import { homeLocationFor } from '@/router/home';
import { sanitiseRedirect } from '@/router/redirect';
import { loginSchema } from '@/schemas/login';
import { useAuthStore } from '@/stores/auth';

import { useApiErrorMessage } from './useApiErrorMessage';

type LoginField = 'username' | 'password';

function isLoginField(value: unknown): value is LoginField {
  return value === 'username' || value === 'password';
}

export function useLoginForm() {
  const { t } = useI18n();
  const auth = useAuthStore();
  const router = useRouter();
  const route = useRoute();
  const describeError = useApiErrorMessage();

  const form = reactive({ username: '', password: '' });
  const fieldErrors = reactive<Record<LoginField, string | undefined>>({
    username: undefined,
    password: undefined,
  });
  const formError = ref<string | null>(null);
  const loading = ref(false);

  // An error describes the text that was typed, so it goes away as soon as the text changes.
  watch(
    () => form.username,
    () => {
      fieldErrors.username = undefined;
    },
  );
  watch(
    () => form.password,
    () => {
      fieldErrors.password = undefined;
    },
  );

  function resetErrors(): void {
    fieldErrors.username = undefined;
    fieldErrors.password = undefined;
    formError.value = null;
  }

  /** Returns true when the form is valid; otherwise fills in the inline errors. */
  function validate(): boolean {
    const result = loginSchema.safeParse(form);
    if (result.success) return true;

    for (const issue of result.error.issues) {
      const field = issue.path[0];
      if (isLoginField(field)) fieldErrors[field] ??= t(issue.message);
    }
    return false;
  }

  function showServerError(error: unknown): void {
    const apiError = toApiError(error);
    let mapped = false;
    for (const [field, message] of Object.entries(apiError.fieldErrors)) {
      if (isLoginField(field)) {
        fieldErrors[field] = message;
        mapped = true;
      }
    }
    if (!mapped) formError.value = describeError(error);
  }

  async function submit(): Promise<void> {
    if (loading.value) return;
    resetErrors();
    if (!validate()) return;

    loading.value = true;
    try {
      await auth.login({ username: form.username.trim(), password: form.password });
    } catch (error) {
      if (!isApiError(error)) throw error;
      showServerError(error);
      return;
    } finally {
      loading.value = false;
    }

    // A redirect into the other role's area is turned back to this role's home by the route guard.
    const user = auth.user;
    await router.replace(
      sanitiseRedirect(route.query.redirect) ?? (user ? homeLocationFor(user.role) : '/'),
    );
  }

  return { form, fieldErrors, formError, loading, submit };
}
