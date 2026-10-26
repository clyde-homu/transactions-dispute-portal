<script setup lang="ts">
import { nextTick, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import type { QInput } from 'quasar';

import { useLoginForm } from '@/composables/useLoginForm';
import { usePageTitle } from '@/composables/usePageTitle';

const { t } = useI18n();
usePageTitle('login.title');

const { form, fieldErrors, formError, loading, submit } = useLoginForm();

const showPassword = ref(false);
const usernameInput = ref<QInput | null>(null);
const passwordInput = ref<QInput | null>(null);

async function onSubmit(): Promise<void> {
  await submit();
  // Put the keyboard where the problem is, so a screen reader announces the error next.
  await nextTick();
  if (fieldErrors.username) usernameInput.value?.focus();
  else if (fieldErrors.password) passwordInput.value?.focus();
}
</script>

<template>
  <q-card flat bordered class="login-card">
    <q-card-section>
      <h1 class="text-h5 q-my-none">{{ t('login.title') }}</h1>
      <p class="muted q-mt-sm q-mb-none">{{ t('login.subtitle') }}</p>
    </q-card-section>

    <q-card-section>
      <q-form class="q-gutter-y-md" novalidate @submit="onSubmit">
        <q-banner
          v-if="formError"
          rounded
          dense
          role="alert"
          class="bg-negative text-white"
          data-testid="login-error"
        >
          <template #avatar>
            <q-icon name="error_outline" aria-hidden="true" />
          </template>
          {{ formError }}
        </q-banner>

        <q-input
          ref="usernameInput"
          v-model="form.username"
          :label="t('login.username')"
          autocomplete="username"
          autocapitalize="none"
          spellcheck="false"
          outlined
          :error="Boolean(fieldErrors.username)"
          :error-message="fieldErrors.username"
          data-testid="login-username"
        />

        <q-input
          ref="passwordInput"
          v-model="form.password"
          :type="showPassword ? 'text' : 'password'"
          :label="t('login.password')"
          autocomplete="current-password"
          outlined
          :error="Boolean(fieldErrors.password)"
          :error-message="fieldErrors.password"
          data-testid="login-password"
        >
          <template #append>
            <q-btn
              flat
              round
              dense
              type="button"
              :icon="showPassword ? 'visibility_off' : 'visibility'"
              :aria-label="showPassword ? t('login.hidePassword') : t('login.showPassword')"
              :aria-pressed="showPassword"
              @click="showPassword = !showPassword"
            />
          </template>
        </q-input>

        <q-btn
          type="submit"
          color="primary"
          unelevated
          no-caps
          size="lg"
          class="full-width"
          :label="t('login.submit')"
          :loading="loading"
          data-testid="login-submit"
        />
      </q-form>
    </q-card-section>

    <q-separator />

    <q-card-section class="muted text-caption row items-center no-wrap q-gutter-x-sm">
      <q-icon name="info_outline" size="xs" aria-hidden="true" />
      <span>{{ t('login.demoHint') }}</span>
    </q-card-section>
  </q-card>
</template>

<style scoped lang="scss">
.login-card {
  width: 100%;
  max-width: 26rem;
}
</style>
