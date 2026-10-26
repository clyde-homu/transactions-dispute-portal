<script setup lang="ts">
import { useI18n } from 'vue-i18n';

import type { AgentTimelineEvent, TimelineEvent } from '@/api/types';
import { formatDateTime } from '@/utils/datetime';
import { STATUS_APPEARANCE } from '@/utils/disputeStatus';

type AnyEvent = TimelineEvent | AgentTimelineEvent;

const props = withDefaults(
  defineProps<{
    /** Oldest first, as the API returns them. */
    events: readonly AnyEvent[];
    /**
     * `agent` is for the back office: it names who acted, uses the back office's status words and
     * shows internal notes, marked as such. The customer view is the default and never shows them.
     */
    variant?: 'customer' | 'agent';
  }>(),
  { variant: 'customer' },
);

const { t } = useI18n();

const statusKey = (event: AnyEvent) =>
  `${props.variant === 'agent' ? 'agentStatus' : 'disputeStatus'}.${event.status}`;

function actorText(event: AnyEvent): string {
  if (props.variant === 'customer') {
    return t('disputeDetail.timeline.by', {
      actor: t(`disputeDetail.timeline.actor.${event.actor}`),
    });
  }
  const role = t(`agentDispute.timeline.actor.${event.actor}`);
  const name = 'actorName' in event ? event.actorName : null;
  return t('agentDispute.timeline.by', { actor: name ? `${name} (${role})` : role });
}

// Only an agent event can carry a note, and only the agent variant is allowed to show it.
function internalNote(event: AnyEvent): string | null {
  return props.variant === 'agent' && 'internalNote' in event ? event.internalNote : null;
}
</script>

<template>
  <q-timeline layout="dense" color="secondary" class="dispute-timeline">
    <q-timeline-entry
      v-for="(event, index) in events"
      :key="event.id"
      :title="t(statusKey(event))"
      :subtitle="formatDateTime(event.occurredAt)"
      :icon="STATUS_APPEARANCE[event.status].icon"
      :color="STATUS_APPEARANCE[event.status].color"
      :aria-current="index === events.length - 1 ? 'step' : undefined"
      :data-status="event.status"
      data-testid="timeline-entry"
    >
      <p class="muted q-mb-xs text-caption" data-testid="timeline-actor">
        {{ actorText(event) }}
      </p>
      <p v-if="event.message" class="q-my-none" data-testid="timeline-message">
        {{ event.message }}
      </p>
      <div v-if="internalNote(event)" class="internal-note" data-testid="timeline-internal-note">
        <p class="internal-note__label q-mb-xs row items-center no-wrap text-weight-medium">
          <q-icon name="lock" size="xs" class="q-mr-xs" aria-hidden="true" />
          {{ t('agentDispute.timeline.internalNote') }}
          <span class="muted text-weight-regular text-caption q-ml-sm">
            {{ t('agentDispute.timeline.internalNoteHint') }}
          </span>
        </p>
        <p class="q-my-none internal-note__text">{{ internalNote(event) }}</p>
      </div>
    </q-timeline-entry>
  </q-timeline>
</template>

<style scoped lang="scss">
// Dashed and tinted with a lock, so an internal note cannot be mistaken for a customer message
// even without colour.
.internal-note {
  margin-top: 0.5rem;
  padding: 0.5rem 0.75rem;
  border: 1px dashed var(--app-border);
  border-left: 4px solid var(--q-warning);
  border-radius: 4px;
  background: var(--app-hover);
}

.internal-note__text {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
