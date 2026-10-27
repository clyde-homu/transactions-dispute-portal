<script setup lang="ts" generic="T extends string">
import { useId } from 'vue';

defineProps<{
  /** Names the group for assistive technology; the visible heading is the step title. */
  legend: string;
  options: { value: T; label: string; icon?: string; hint?: string }[];
  /** Arranged as a grid of tiles, or as a plain vertical list. */
  layout?: 'tiles' | 'list';
  error?: string | undefined;
}>();

const model = defineModel<T | null>({ required: true });
const name = useId();
const errorId = useId();
</script>

<template>
  <fieldset
    class="radio-cards"
    :class="layout === 'list' ? 'radio-cards--list' : 'radio-cards--tiles'"
    :aria-describedby="error ? errorId : undefined"
  >
    <legend class="sr-only">{{ legend }}</legend>
    <label
      v-for="option in options"
      :key="option.value"
      class="radio-card"
      :class="{ 'radio-card--selected': model === option.value }"
    >
      <input
        v-model="model"
        type="radio"
        class="sr-only"
        :name="name"
        :value="option.value"
        :data-testid="`option-${option.value}`"
      />
      <q-icon v-if="option.icon" :name="option.icon" size="md" aria-hidden="true" />
      <span class="radio-card__label">
        {{ option.label }}
        <span v-if="option.hint" class="radio-card__hint muted text-caption">{{
          option.hint
        }}</span>
      </span>
      <!-- The tick, not just the border colour, says which one is chosen. -->
      <q-icon
        v-if="model === option.value"
        name="check_circle"
        color="primary"
        size="sm"
        class="radio-card__check"
        aria-hidden="true"
      />
    </label>
    <p v-if="error" :id="errorId" class="error-text q-mt-sm q-mb-none" role="alert">
      <q-icon name="error_outline" size="xs" aria-hidden="true" /> {{ error }}
    </p>
  </fieldset>
</template>

<style scoped lang="scss">
.radio-cards {
  border: 0;
  margin: 0;
  padding: 0;
  min-width: 0;
}

.radio-cards--tiles {
  display: grid;
  gap: 0.75rem;
  grid-template-columns: repeat(auto-fit, minmax(11rem, 1fr));
}

.radio-cards--list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.radio-card {
  position: relative;
  display: flex;
  gap: 0.75rem;
  align-items: center;
  padding: 0.875rem 1rem;
  border: 1px solid var(--app-border);
  border-radius: 6px;
  cursor: pointer;

  &:hover {
    background: var(--app-hover);
  }

  // The radio itself is visually hidden, so the tile shows the keyboard focus.
  &:focus-within {
    outline: 3px solid var(--app-focus);
    outline-offset: 2px;
  }

  &--selected {
    border-color: var(--q-primary);
    box-shadow: 0 0 0 1px var(--q-primary);
  }
}

.radio-cards--tiles .radio-card {
  flex-direction: column;
  justify-content: center;
  min-height: 7rem;
  text-align: center;
}

.radio-card__label {
  flex: 1 1 auto;
}

.radio-card__hint {
  display: block;
}

.radio-cards--tiles .radio-card__check {
  position: absolute;
  top: 0.5rem;
  right: 0.5rem;
}
</style>
