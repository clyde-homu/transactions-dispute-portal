<script setup lang="ts">
import { Notify, type QInput } from 'quasar';
import { ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';

import { respondToDispute } from '@/api/disputes';
import { toApiError } from '@/api/errors';
import type { DisputeDetail } from '@/api/types';
import { useApiErrorMessage } from '@/composables/useApiErrorMessage';
import { firstIssuePerField, MESSAGE_MAX, replySchema } from '@/schemas/messages';

const props = defineProps<{
  disputeId: string;
  /** What the disputes team asked, so the customer can read it next to the box they type in. */
  question: string | null;
}>();

const emit = defineEmits<{
  /** The dispute as the server now has it. */
  replied: [dispute: DisputeDetail];
  /** The dispute changed under the customer; the parent should reload it. */
  conflict: [];
}>();

const { t } = useI18n();
const describeError = useApiErrorMessage();

const message = ref('');
const error = ref<string | null>(null);
const sending = ref(false);
const input = ref<QInput | null>(null);

// An error describes the text that was typed, so it goes away as soon as the text changes.
watch(message, () => {
  error.value = null;
});

function focus(): void {
  input.value?.focus();
}
defineExpose({ focus });

async function submit(): Promise<void> {
  if (sending.value) return;

  const parsed = replySchema.safeParse({ message: message.value });
  if (!parsed.success) {
    const key = firstIssuePerField(parsed.error).message;
    error.value = key ? t(key) : t('errors.VALIDATION_FAILED');
    focus();
    return;
  }

  sending.value = true;
  try {
    const updated = await respondToDispute(props.disputeId, { message: parsed.data.message });
    Notify.create({ type: 'positive', message: t('reply.success') });
    emit('replied', updated);
  } catch (failure) {
    const problem = toApiError(failure);
    if (problem.code === 'INVALID_DISPUTE_TRANSITION' || problem.code === 'DISPUTE_MODIFIED') {
      Notify.create({ type: 'warning', message: t(`reply.conflict.${problem.code}`) });
      emit('conflict');
    } else if (problem.fieldErrors.message) {
      error.value = problem.fieldErrors.message;
      focus();
    } else {
      Notify.create({ type: 'negative', message: describeError(problem) });
    }
  } finally {
    sending.value = false;
  }
}
</script>

<template>
  <q-card
    id="reply-section"
    flat
    bordered
    class="q-mt-md reply-card"
    role="region"
    aria-labelledby="reply-heading"
    data-testid="reply-section"
  >
    <q-card-section>
      <h2 id="reply-heading" class="text-h6 q-mt-none q-mb-xs">{{ t('reply.heading') }}</h2>
      <p class="muted q-mb-none">{{ t('reply.intro') }}</p>
      <blockquote
        v-if="question"
        class="reply-card__question q-mb-none"
        data-testid="reply-question"
      >
        <span class="text-weight-medium">{{ t('reply.asked') }}:</span> {{ question }}
      </blockquote>
    </q-card-section>
    <q-card-section class="q-pt-none">
      <q-form novalidate @submit="submit">
        <q-input
          ref="input"
          v-model="message"
          type="textarea"
          outlined
          autogrow
          counter
          :maxlength="MESSAGE_MAX"
          :label="t('reply.label')"
          :hint="t('reply.hint')"
          :error="Boolean(error)"
          :error-message="error ?? undefined"
          data-testid="reply-message"
        />
        <div class="row justify-end q-mt-md">
          <q-btn
            type="submit"
            color="primary"
            unelevated
            no-caps
            icon="send"
            :label="t('reply.submit')"
            :loading="sending"
            :disable="sending"
            data-testid="reply-submit"
          />
        </div>
      </q-form>
    </q-card-section>
  </q-card>
</template>

<style scoped lang="scss">
.reply-card {
  border-left: 4px solid var(--q-warning);
}

.reply-card__question {
  margin: 0.75rem 0 0;
  padding-left: 0.75rem;
  border-left: 3px solid var(--app-border);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
