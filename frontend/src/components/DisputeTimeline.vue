<script setup lang="ts">
import { useI18n } from 'vue-i18n';

import type { TimelineEvent } from '@/api/types';
import { formatDateTime } from '@/utils/datetime';
import { STATUS_APPEARANCE } from '@/utils/disputeStatus';

defineProps<{
  /** Oldest first, as the API returns them. */
  events: TimelineEvent[];
}>();

const { t } = useI18n();
</script>

<template>
  <q-timeline layout="dense" color="secondary" class="dispute-timeline">
    <q-timeline-entry
      v-for="(event, index) in events"
      :key="event.id"
      :title="t(`disputeStatus.${event.status}`)"
      :subtitle="formatDateTime(event.occurredAt)"
      :icon="STATUS_APPEARANCE[event.status].icon"
      :color="STATUS_APPEARANCE[event.status].color"
      :aria-current="index === events.length - 1 ? 'step' : undefined"
      :data-status="event.status"
      data-testid="timeline-entry"
    >
      <p class="muted q-mb-xs text-caption" data-testid="timeline-actor">
        {{
          t('disputeDetail.timeline.by', {
            actor: t(`disputeDetail.timeline.actor.${event.actor}`),
          })
        }}
      </p>
      <p v-if="event.message" class="q-my-none" data-testid="timeline-message">
        {{ event.message }}
      </p>
    </q-timeline-entry>
  </q-timeline>
</template>
