<script setup lang="ts">
import { ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';

import type { RequestInfoRequest } from '@/api/types';
import { firstIssuePerField, MESSAGE_MAX, requestInfoSchema } from '@/schemas/messages';

const props = defineProps<{
  modelValue: boolean;
  busy: boolean;
  /** Field errors the server sent back for the last submit, keyed by JSON property. */
  serverErrors: Record<string, string>;
}>();

const emit = defineEmits<{
  'update:modelValue': [open: boolean];
  submit: [request: RequestInfoRequest];
}>();

const { t } = useI18n();

const message = ref('');
const localError = ref<string | null>(null);

// Each opening starts from a blank form.
watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      message.value = '';
      localError.value = null;
    }
  },
);
watch(message, () => {
  localError.value = null;
});

function submit(): void {
  if (props.busy) return;
  const parsed = requestInfoSchema.safeParse({ message: message.value });
  if (!parsed.success) {
    const key = firstIssuePerField(parsed.error).message;
    localError.value = key ? t(key) : t('errors.VALIDATION_FAILED');
    return;
  }
  emit('submit', { message: parsed.data.message });
}
</script>

<template>
  <q-dialog
    :model-value="modelValue"
    :persistent="busy"
    aria-labelledby="request-info-title"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <q-card class="action-dialog" data-testid="request-info-dialog">
      <q-form novalidate @submit="submit">
        <q-card-section>
          <h2 id="request-info-title" class="text-h6 q-my-none">
            {{ t('agentDispute.requestInfo.title') }}
          </h2>
          <p class="muted q-mt-sm">{{ t('agentDispute.requestInfo.description') }}</p>
          <q-input
            v-model="message"
            type="textarea"
            outlined
            autogrow
            autofocus
            counter
            :maxlength="MESSAGE_MAX"
            :label="t('agentDispute.requestInfo.message')"
            :hint="t('agentDispute.requestInfo.hint')"
            :error="Boolean(localError ?? serverErrors.message)"
            :error-message="localError ?? serverErrors.message"
            data-testid="request-info-message"
          />
        </q-card-section>
        <q-card-actions align="right" class="q-px-md q-pb-md">
          <q-btn
            v-close-popup
            flat
            no-caps
            :disable="busy"
            :label="t('agentDispute.requestInfo.cancel')"
            data-testid="request-info-cancel"
          />
          <q-btn
            type="submit"
            color="primary"
            unelevated
            no-caps
            :loading="busy"
            :disable="busy"
            :label="t('agentDispute.requestInfo.submit')"
            data-testid="request-info-submit"
          />
        </q-card-actions>
      </q-form>
    </q-card>
  </q-dialog>
</template>

<style scoped lang="scss">
.action-dialog {
  width: 100%;
  max-width: 34rem;
}
</style>
