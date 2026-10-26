<script setup lang="ts">
import { useQuasar, type QTableColumn } from 'quasar';
import { computed, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRouter } from 'vue-router';

import { listAgentDisputes } from '@/api/agent';
import type { AgentDisputeSummary, AgentQueue } from '@/api/types';
import EmptyState from '@/components/EmptyState.vue';
import ErrorState from '@/components/ErrorState.vue';
import ListSkeleton from '@/components/ListSkeleton.vue';
import MoneyAmount from '@/components/MoneyAmount.vue';
import StatusChip from '@/components/StatusChip.vue';
import { AGENT_QUEUES, useAgentQueueFilters } from '@/composables/useAgentQueueFilters';
import { useApiErrorMessage } from '@/composables/useApiErrorMessage';
import { usePageTitle } from '@/composables/usePageTitle';
import { usePagedQuery } from '@/composables/usePagedQuery';
import { formatDate } from '@/utils/datetime';

const { t } = useI18n();
const $q = useQuasar();
const router = useRouter();
const describeError = useApiErrorMessage();
usePageTitle('pages.agentQueue.title');

const filters = useAgentQueueFilters();
const { tab, search, page } = filters;

const pageModel = computed({
  get: () => page.value,
  set: (next: number) => void filters.apply({ page: next }),
});

const query = usePagedQuery({
  params: () => ({ queue: tab.value, search: search.value }),
  page: pageModel,
  fetch: (params, paging, signal) =>
    listAgentDisputes(
      { queue: params.queue, q: params.search, page: paging.page, size: paging.size },
      signal,
    ),
  onPageOutOfRange: (lastPage) => void filters.apply({ page: lastPage }, 'replace'),
});

// ---- search box (debounced by q-input; the URL is updated once typing pauses) -------------------

const searchText = ref(search.value);
watch(searchText, (typed) => {
  const trimmed = typed.trim();
  if (trimmed !== search.value) void filters.apply({ search: trimmed }, 'replace');
});
watch(search, (current) => {
  if (current !== searchText.value.trim()) searchText.value = current;
});

function selectTab(next: AgentQueue): void {
  if (next !== tab.value) void filters.apply({ tab: next });
}

// ---- view helpers ------------------------------------------------------------------------------

const isDesktop = computed(() => $q.screen.gt.sm);
const firstRow = computed(() => query.page.value * query.size + 1);
const lastRow = computed(() => firstRow.value + query.items.value.length - 1);

const assignedTo = (row: AgentDisputeSummary): string =>
  row.assignedAgentName ?? t('agentQueue.unassigned');

const columns = computed<QTableColumn<AgentDisputeSummary>[]>(() => [
  {
    name: 'reference',
    label: t('agentQueue.column.reference'),
    field: 'reference',
    align: 'left',
  },
  {
    name: 'customer',
    label: t('agentQueue.column.customer'),
    field: 'customerName',
    align: 'left',
  },
  {
    name: 'merchant',
    label: t('agentQueue.column.merchant'),
    field: 'merchantName',
    align: 'left',
  },
  { name: 'reason', label: t('agentQueue.column.reason'), field: 'reasonLabel', align: 'left' },
  {
    name: 'amount',
    label: t('agentQueue.column.amount'),
    field: 'disputedAmount',
    align: 'right',
  },
  { name: 'status', label: t('agentQueue.column.status'), field: 'status', align: 'left' },
  {
    name: 'assigned',
    label: t('agentQueue.column.assignedTo'),
    field: assignedTo,
    align: 'left',
  },
  {
    name: 'created',
    label: t('agentQueue.column.created'),
    field: (row) => formatDate(row.createdAt),
    align: 'left',
  },
]);

function detailLocation(row: AgentDisputeSummary) {
  return { name: 'agent-dispute-detail', params: { id: row.id } };
}

// The reference link is the keyboard route in; this makes the rest of the row a mouse target too.
function onRowClick(event: Event, row: AgentDisputeSummary): void {
  if (event.target instanceof Element && event.target.closest('a')) return;
  void router.push(detailLocation(row));
}
</script>

<template>
  <q-page padding class="page-content page-content--wide">
    <h1 class="text-h5 q-mt-none">{{ t('pages.agentQueue.title') }}</h1>

    <q-tabs
      :model-value="tab"
      dense
      align="left"
      active-color="primary"
      indicator-color="primary"
      class="queue-tabs"
      :aria-label="t('agentQueue.tabsLabel')"
      data-testid="queue-tabs"
      @update:model-value="selectTab($event as AgentQueue)"
    >
      <q-tab
        v-for="queue in AGENT_QUEUES"
        :key="queue"
        :name="queue"
        no-caps
        :label="t(`agentQueue.tabs.${queue}`)"
        :data-testid="`tab-${queue}`"
      />
    </q-tabs>
    <q-separator />

    <q-input
      v-model="searchText"
      :maxlength="100"
      outlined
      dense
      clearable
      class="q-mt-md"
      :debounce="300"
      :label="t('agentQueue.search')"
      autocomplete="off"
      data-testid="search"
    >
      <template #prepend>
        <q-icon name="search" aria-hidden="true" />
      </template>
    </q-input>

    <section
      class="q-mt-md"
      role="tabpanel"
      :aria-label="t(`agentQueue.tabs.${tab}`)"
      data-testid="queue-panel"
    >
      <ListSkeleton v-if="query.loading.value" :label="t('agentQueue.loading')" :rows="6" />

      <ErrorState
        v-else-if="query.error.value"
        :title="t('agentQueue.errorTitle')"
        :message="describeError(query.error.value)"
        @retry="query.retry"
      />

      <EmptyState
        v-else-if="query.items.value.length === 0 && search"
        icon="search_off"
        :title="t('agentQueue.emptyFilteredTitle')"
        :description="t('agentQueue.emptyFilteredDescription')"
      >
        <q-btn
          outline
          no-caps
          color="primary"
          :label="t('agentQueue.clearSearch')"
          data-testid="clear-search"
          @click="filters.apply({ search: '' })"
        />
      </EmptyState>

      <EmptyState
        v-else-if="query.items.value.length === 0"
        :icon="tab === 'MINE' ? 'assignment_ind' : tab === 'ALL' ? 'folder_open' : 'task_alt'"
        :title="t(`agentQueue.empty.${tab}.title`)"
        :description="t(`agentQueue.empty.${tab}.description`)"
      >
        <q-btn
          v-if="tab === 'MINE'"
          color="primary"
          unelevated
          no-caps
          :label="t('agentQueue.goToUnassigned')"
          data-testid="go-to-unassigned"
          @click="selectTab('UNASSIGNED')"
        />
      </EmptyState>

      <template v-else>
        <p class="muted text-body2 q-mb-sm" role="status" data-testid="summary">
          {{
            t('agentQueue.summary', {
              from: firstRow,
              to: lastRow,
              total: query.totalItems.value,
            })
          }}
        </p>

        <q-table
          v-if="isDesktop"
          flat
          bordered
          dense
          wrap-cells
          hide-pagination
          row-key="id"
          class="queue-table"
          :rows="query.items.value"
          :columns="columns"
          :pagination="{ rowsPerPage: 0 }"
          :rows-per-page-options="[0]"
          data-testid="queue-table"
          @row-click="onRowClick"
        >
          <template #body-cell-reference="cell">
            <q-td :props="cell">
              <router-link class="row-link reference" :to="detailLocation(cell.row)">
                {{ cell.row.reference }}
              </router-link>
            </q-td>
          </template>
          <template #body-cell-amount="cell">
            <q-td :props="cell">
              <MoneyAmount :amount="cell.row.disputedAmount" :currency="cell.row.currency" />
            </q-td>
          </template>
          <template #body-cell-status="cell">
            <q-td :props="cell">
              <StatusChip :status="cell.row.status" audience="agent" />
            </q-td>
          </template>
          <template #body-cell-assigned="cell">
            <q-td :props="cell">
              <span :class="{ muted: !cell.row.assignedAgentName }">{{ cell.value }}</span>
            </q-td>
          </template>
        </q-table>

        <q-list v-else bordered separator class="rounded-borders" data-testid="queue-list">
          <q-item
            v-for="row in query.items.value"
            :key="row.id"
            clickable
            :to="detailLocation(row)"
            class="queue-card"
          >
            <q-item-section>
              <q-item-label class="text-weight-medium reference">{{ row.reference }}</q-item-label>
              <q-item-label>{{ row.customerName }}</q-item-label>
              <q-item-label caption>{{ row.merchantName }} · {{ row.reasonLabel }}</q-item-label>
              <q-item-label caption>
                {{ t('agentQueue.created', { date: formatDate(row.createdAt) }) }}
              </q-item-label>
              <q-item-label caption>
                {{
                  row.assignedAgentName
                    ? t('agentQueue.assignedTo', { name: row.assignedAgentName })
                    : t('agentQueue.unassigned')
                }}
              </q-item-label>
              <q-item-label class="q-mt-xs">
                <StatusChip :status="row.status" audience="agent" />
              </q-item-label>
            </q-item-section>
            <q-item-section side top>
              <MoneyAmount
                class="text-body1 text-weight-medium"
                :amount="row.disputedAmount"
                :currency="row.currency"
              />
            </q-item-section>
          </q-item>
        </q-list>

        <nav
          v-if="query.totalPages.value > 1"
          class="row justify-center q-mt-md"
          :aria-label="t('agentQueue.pagination')"
        >
          <q-pagination
            :model-value="query.page.value + 1"
            :max="query.totalPages.value"
            :max-pages="isDesktop ? 7 : 5"
            direction-links
            boundary-links
            flat
            color="primary"
            active-color="primary"
            @update:model-value="query.goToPage($event - 1)"
          />
        </nav>
      </template>
    </section>
  </q-page>
</template>

<style scoped lang="scss">
// Eight columns need more room than the 60rem the other pages use.
.page-content--wide {
  max-width: 80rem;
}

.queue-table :deep(tbody tr) {
  cursor: pointer;
}

.reference {
  font-family: ui-monospace, 'SFMono-Regular', Menlo, Consolas, monospace;
  // A reference broken over two lines is hard to read out or copy.
  white-space: nowrap;
}

.row-link {
  color: inherit;
  font-weight: 500;
  text-decoration: none;

  &:hover {
    text-decoration: underline;
  }
}
</style>
