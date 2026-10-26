<script setup lang="ts">
import { useQuasar, type QTableColumn } from 'quasar';
import { computed, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRouter } from 'vue-router';

import { listDisputes } from '@/api/disputes';
import type { DisputeSummary } from '@/api/types';
import EmptyState from '@/components/EmptyState.vue';
import ErrorState from '@/components/ErrorState.vue';
import ListSkeleton from '@/components/ListSkeleton.vue';
import MoneyAmount from '@/components/MoneyAmount.vue';
import StatusChip from '@/components/StatusChip.vue';
import { useApiErrorMessage } from '@/composables/useApiErrorMessage';
import { useDisputeFilters } from '@/composables/useDisputeFilters';
import { usePageTitle } from '@/composables/usePageTitle';
import { usePagedQuery } from '@/composables/usePagedQuery';
import { formatDate } from '@/utils/datetime';
import { DISPUTE_STATUSES } from '@/utils/disputeStatus';

const { t } = useI18n();
const $q = useQuasar();
const router = useRouter();
const describeError = useApiErrorMessage();
usePageTitle('pages.disputes.title');

const filters = useDisputeFilters();
const { statuses, search, page, hasActiveFilters } = filters;

const pageModel = computed({
  get: () => page.value,
  set: (next: number) => void filters.apply({ page: next }),
});

const query = usePagedQuery({
  params: () => ({ statuses: statuses.value, search: search.value }),
  page: pageModel,
  fetch: (params, paging, signal) =>
    listDisputes(
      {
        status: params.statuses,
        q: params.search,
        page: paging.page,
        size: paging.size,
      },
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

// ---- view helpers ------------------------------------------------------------------------------

const isDesktop = computed(() => $q.screen.gt.sm);
const firstRow = computed(() => query.page.value * query.size + 1);
const lastRow = computed(() => firstRow.value + query.items.value.length - 1);

const columns = computed<QTableColumn<DisputeSummary>[]>(() => [
  {
    name: 'reference',
    label: t('disputes.list.column.reference'),
    field: 'reference',
    align: 'left',
  },
  {
    name: 'merchant',
    label: t('disputes.list.column.merchant'),
    field: 'merchantName',
    align: 'left',
  },
  {
    name: 'reason',
    label: t('disputes.list.column.reason'),
    field: 'reasonLabel',
    align: 'left',
  },
  {
    name: 'amount',
    label: t('disputes.list.column.amount'),
    field: 'disputedAmount',
    align: 'right',
  },
  { name: 'status', label: t('disputes.list.column.status'), field: 'status', align: 'left' },
  {
    name: 'updated',
    label: t('disputes.list.column.updated'),
    field: (row) => formatDate(row.updatedAt),
    align: 'left',
  },
]);

function detailLocation(row: DisputeSummary) {
  return { name: 'dispute-detail', params: { id: row.id } };
}

// The reference link is the keyboard route in; this makes the rest of the row a mouse target too.
function onRowClick(event: Event, row: DisputeSummary): void {
  if (event.target instanceof Element && event.target.closest('a')) return;
  void router.push(detailLocation(row));
}
</script>

<template>
  <q-page padding class="page-content">
    <h1 class="text-h5 q-mt-none">{{ t('pages.disputes.title') }}</h1>

    <!-- Filters -->
    <section :aria-label="t('disputes.filters.label')">
      <div role="group" :aria-label="t('disputes.filters.statusLabel')" class="q-gutter-xs">
        <q-chip
          clickable
          :selected="statuses.length === 0"
          :outline="statuses.length > 0"
          color="primary"
          :text-color="statuses.length === 0 ? 'white' : 'primary'"
          icon-selected="check"
          :label="t('disputes.filters.all')"
          data-testid="status-all"
          @click="filters.clearFilters()"
        />
        <q-chip
          v-for="status in DISPUTE_STATUSES"
          :key="status"
          clickable
          :selected="statuses.includes(status)"
          :outline="!statuses.includes(status)"
          color="primary"
          :text-color="statuses.includes(status) ? 'white' : 'primary'"
          icon-selected="check"
          :label="t(`disputeStatus.${status}`)"
          :data-testid="`status-${status}`"
          @click="filters.toggleStatus(status)"
        />
      </div>
      <q-input
        v-model="searchText"
        outlined
        dense
        clearable
        class="q-mt-md"
        :debounce="300"
        :label="t('disputes.filters.search')"
        autocomplete="off"
        data-testid="search"
      >
        <template #prepend>
          <q-icon name="search" aria-hidden="true" />
        </template>
      </q-input>
    </section>

    <!-- Results -->
    <section class="q-mt-md">
      <ListSkeleton v-if="query.loading.value" :label="t('disputes.list.loading')" :rows="6" />

      <ErrorState
        v-else-if="query.error.value"
        :title="t('disputes.list.errorTitle')"
        :message="describeError(query.error.value)"
        @retry="query.retry"
      />

      <EmptyState
        v-else-if="query.items.value.length === 0 && hasActiveFilters"
        icon="search_off"
        :title="t('disputes.list.emptyFilteredTitle')"
        :description="t('disputes.list.emptyFilteredDescription')"
      >
        <q-btn
          outline
          no-caps
          color="primary"
          :label="t('disputes.filters.clear')"
          data-testid="clear-filters"
          @click="filters.clearFilters()"
        />
      </EmptyState>

      <EmptyState
        v-else-if="query.items.value.length === 0"
        icon="folder_open"
        :title="t('disputes.list.emptyTitle')"
        :description="t('disputes.list.emptyDescription')"
      >
        <q-btn
          color="primary"
          unelevated
          no-caps
          :to="{ name: 'transactions' }"
          :label="t('disputes.list.goToTransactions')"
          data-testid="go-to-transactions"
        />
      </EmptyState>

      <template v-else>
        <p class="muted text-body2 q-mb-sm" role="status" data-testid="summary">
          {{
            t('disputes.list.summary', {
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
          hide-pagination
          row-key="id"
          class="disputes-table"
          :rows="query.items.value"
          :columns="columns"
          :pagination="{ rowsPerPage: 0 }"
          :rows-per-page-options="[0]"
          data-testid="disputes-table"
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
              <StatusChip :status="cell.row.status" />
            </q-td>
          </template>
        </q-table>

        <q-list v-else bordered separator class="rounded-borders" data-testid="disputes-list">
          <q-item
            v-for="row in query.items.value"
            :key="row.id"
            clickable
            :to="detailLocation(row)"
            class="dispute-card"
          >
            <q-item-section>
              <q-item-label class="text-weight-medium reference">{{ row.reference }}</q-item-label>
              <q-item-label>{{ row.merchantName }}</q-item-label>
              <q-item-label caption>{{ row.reasonLabel }}</q-item-label>
              <q-item-label caption>
                {{ t('disputes.list.updated', { date: formatDate(row.updatedAt) }) }}
              </q-item-label>
              <q-item-label class="q-mt-xs">
                <StatusChip :status="row.status" />
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
          :aria-label="t('disputes.list.pagination')"
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
.disputes-table :deep(tbody tr) {
  cursor: pointer;
}

.reference {
  font-family: ui-monospace, 'SFMono-Regular', Menlo, Consolas, monospace;
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
