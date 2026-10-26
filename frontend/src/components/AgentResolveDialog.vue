<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';

import type { ResolutionOutcome, ResolveDisputeRequest } from '@/api/types';
import { firstIssuePerField, MESSAGE_MAX, resolveSchema } from '@/schemas/messages';

type Field = 'outcome' | 'customerMessage' | 'internalNote';
const FIELDS: readonly Field[] = ['outcome', 'customerMessage', 'internalNote'];

const props = defineProps<{
  modelValue: boolean;
  busy: boolean;
  /** Field errors the server sent back for the last submit, keyed by JSON property. */
  serverErrors: Record<string, string>;
}>();

const emit = defineEmits<{
  'update:modelValue': [open: boolean];
  submit: [request: ResolveDisputeRequest];
}>();

const { t } = useI18n();

const form = reactive<{
  outcome: ResolutionOutcome | null;
  customerMessage: string;
  internalNote: string;
}>({ outcome: null, customerMessage: '', internalNote: '' });
const localErrors = ref<Partial<Record<Field, string>>>({});

const outcomeOptions = computed(() => [
  { value: 'UPHELD', label: t('agentDispute.resolve.upheld') },
  { value: 'DECLINED', label: t('agentDispute.resolve.declined') },
]);

// Each opening starts from a blank form.
watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      form.outcome = null;
      form.customerMessage = '';
      form.internalNote = '';
      localErrors.value = {};
    }
  },
);

// An error describes what was typed, so editing that field clears it.
function clear(field: Field): void {
  if (localErrors.value[field]) localErrors.value = { ...localErrors.value, [field]: undefined };
}
watch(
  () => form.outcome,
  () => clear('outcome'),
);
watch(
  () => form.customerMessage,
  () => clear('customerMessage'),
);
watch(
  () => form.internalNote,
  () => clear('internalNote'),
);

const errorFor = (field: Field): string | undefined =>
  localErrors.value[field] ?? props.serverErrors[field];

function submit(): void {
  if (props.busy) return;
  const parsed = resolveSchema.safeParse(form);
  if (!parsed.success) {
    const keys = firstIssuePerField(parsed.error);
    const translated: Partial<Record<Field, string>> = {};
    for (const field of FIELDS) {
      const key = keys[field];
      if (key) translated[field] = t(key);
    }
    localErrors.value = translated;
    return;
  }
  emit('submit', parsed.data);
}
</script>

<template>
  <q-dialog
    :model-value="modelValue"
    :persistent="busy"
    aria-labelledby="resolve-title"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <q-card class="action-dialog" data-testid="resolve-dialog">
      <q-form novalidate @submit="submit">
        <q-card-section class="q-gutter-y-md">
          <h2 id="resolve-title" class="text-h6 q-my-none">
            {{ t('agentDispute.resolve.title') }}
          </h2>

          <div>
            <p id="resolve-outcome-label" class="text-subtitle2 q-mb-xs">
              {{ t('agentDispute.resolve.outcome') }}
            </p>
            <q-option-group
              v-model="form.outcome"
              type="radio"
              aria-labelledby="resolve-outcome-label"
              :options="outcomeOptions"
              data-testid="resolve-outcome"
            />
            <p
              v-if="errorFor('outcome')"
              class="error-text text-caption q-mt-xs q-mb-none row items-center no-wrap"
              role="alert"
              data-testid="resolve-outcome-error"
            >
              <q-icon name="error_outline" size="xs" class="q-mr-xs" aria-hidden="true" />
              {{ errorFor('outcome') }}
            </p>
          </div>

          <q-input
            v-model="form.customerMessage"
            type="textarea"
            outlined
            autogrow
            counter
            :maxlength="MESSAGE_MAX"
            :label="t('agentDispute.resolve.customerMessage')"
            :hint="t('agentDispute.resolve.customerMessageHint')"
            :error="Boolean(errorFor('customerMessage'))"
            :error-message="errorFor('customerMessage')"
            data-testid="resolve-customer-message"
          />

          <q-input
            v-model="form.internalNote"
            type="textarea"
            outlined
            autogrow
            counter
            :maxlength="MESSAGE_MAX"
            :label="t('agentDispute.resolve.internalNote')"
            :hint="t('agentDispute.resolve.internalNoteHint')"
            :error="Boolean(errorFor('internalNote'))"
            :error-message="errorFor('internalNote')"
            data-testid="resolve-internal-note"
          />
        </q-card-section>
        <q-card-actions align="right" class="q-px-md q-pb-md">
          <q-btn
            v-close-popup
            flat
            no-caps
            :disable="busy"
            :label="t('agentDispute.resolve.cancel')"
            data-testid="resolve-cancel"
          />
          <q-btn
            type="submit"
            color="primary"
            unelevated
            no-caps
            :loading="busy"
            :disable="busy"
            :label="t('agentDispute.resolve.submit')"
            data-testid="resolve-submit"
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
