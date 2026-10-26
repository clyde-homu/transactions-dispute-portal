<script setup lang="ts">
import { useQuasar, type QTableColumn } from 'quasar';
import { computed, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { useRouter } from 'vue-router';

import { listAccounts } from '@/api/accounts';
import { listTransactions } from '@/api/transactions';
import type { TransactionSummary } from '@/api/types';
import EmptyState from '@/components/EmptyState.vue';
import ErrorState from '@/components/ErrorState.vue';
import ListSkeleton from '@/components/ListSkeleton.vue';
import MoneyAmount from '@/components/MoneyAmount.vue';
import TransactionBadges from '@/components/TransactionBadges.vue';
import { useApiErrorMessage } from '@/composables/useApiErrorMessage';
import { useAsyncData } from '@/composables/useAsyncData';
import { usePageTitle } from '@/composables/usePageTitle';
import { usePagedQuery } from '@/composables/usePagedQuery';
import { RANGE_PRESETS, useTransactionFilters } from '@/composables/useTransactionFilters';
import { apiDateDaysAgo, formatDate } from '@/utils/datetime';

const { t } = useI18n();
const $q = useQuasar();
const router = useRouter();
const describeError = useApiErrorMessage();
usePageTitle('pages.transactions.title');

const filters = useTransactionFilters();
const { account, range, search, disputedOnly, page, hasActiveFilters } = filters;

// ---- accounts ----------------------------------------------------------------------------------

const accounts = useAsyncData(
  () => ({}),
  (_, signal) => listAccounts(signal),
);
const accountList = computed(() => accounts.data.value ?? []);

// An unknown or missing `account` in the URL falls back to the first account.
const selectedAccount = computed(
  () =>
    accountList.value.find((candidate) => candidate.id === account.value) ??
    accountList.value[0] ??
    null,
);

// Make the address bar name the account that is actually on screen, so reload and Back agree.
watch(
  selectedAccount,
  (selected) => {
    if (selected && account.value !== selected.id)
      void filters.apply({ account: selected.id }, 'replace');
  },
  { immediate: true },
);

function selectAccount(accountId: string): void {
  if (accountId !== selectedAccount.value?.id) void filters.apply({ account: accountId });
}

// ---- transactions ------------------------------------------------------------------------------

const pageModel = computed({
  get: () => page.value,
  set: (next: number) => void filters.apply({ page: next }),
});

const query = usePagedQuery({
  params: () =>
    selectedAccount.value
      ? {
          accountId: selectedAccount.value.id,
          range: range.value,
          search: search.value,
          disputedOnly: disputedOnly.value,
        }
      : null,
  page: pageModel,
  fetch: (params, paging, signal) =>
    listTransactions(
      params.accountId,
      {
        ...(params.range === 'all' ? {} : { from: apiDateDaysAgo(Number(params.range)) }),
        q: params.search,
        disputed: params.disputedOnly,
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
// Back and "Clear filters" change the URL, and the box must follow.
watch(search, (current) => {
  if (current !== searchText.value.trim()) searchText.value = current;
});

// ---- view helpers ------------------------------------------------------------------------------

const isDesktop = computed(() => $q.screen.gt.sm);

const firstRow = computed(() => query.page.value * query.size + 1);
const lastRow = computed(() => firstRow.value + query.items.value.length - 1);

const columns = computed<QTableColumn<TransactionSummary>[]>(() => [
  {
    name: 'date',
    label: t('transactions.list.column.date'),
    field: (row) => formatDate(row.postedAt),
    align: 'left',
  },
  {
    name: 'merchant',
    label: t('transactions.list.column.merchant'),
    field: 'merchantName',
    align: 'left',
  },
  {
    name: 'reference',
    label: t('transactions.list.column.reference'),
    field: 'reference',
    align: 'left',
  },
  {
    name: 'amount',
    label: t('transactions.list.column.amount'),
    field: 'amount',
    align: 'right',
  },
]);

function detailLocation(row: TransactionSummary) {
  return { name: 'transaction-detail', params: { id: row.id } };
}

// The merchant link is the keyboard route in; this makes the rest of the row a mouse target too.
function onRowClick(event: Event, row: TransactionSummary): void {
  if (event.target instanceof Element && event.target.closest('a')) return;
  void router.push(detailLocation(row));
}
</script>

<template>
  <q-page padding class="page-content">
    <h1 class="text-h5 q-mt-none">{{ t('pages.transactions.title') }}</h1>

    <!-- Accounts -->
    <div
      v-if="accounts.loading.value && !accounts.data.value"
      class="row q-col-gutter-md"
      role="status"
      aria-busy="true"
      :aria-label="t('transactions.accounts.loading')"
    >
      <div class="col-12 col-sm-6 col-md-4"><q-skeleton type="rect" height="96px" /></div>
    </div>
    <ErrorState
      v-else-if="accounts.error.value"
      :title="t('transactions.accounts.errorTitle')"
      :message="describeError(accounts.error.value)"
      @retry="accounts.retry"
    />
    <EmptyState
      v-else-if="accountList.length === 0"
      icon="account_balance_wallet"
      :title="t('transactions.accounts.emptyTitle')"
      :description="t('transactions.accounts.emptyDescription')"
    />

    <template v-else>
      <section :aria-label="t('transactions.accounts.label')">
        <div class="account-list">
          <div v-for="item in accountList" :key="item.id">
            <q-card
              tag="button"
              type="button"
              flat
              bordered
              class="account-card full-width text-left"
              :class="{ 'account-card--selected': item.id === selectedAccount?.id }"
              :aria-pressed="item.id === selectedAccount?.id"
              data-testid="account-card"
              @click="selectAccount(item.id)"
            >
              <q-card-section>
                <div class="row items-center no-wrap">
                  <div class="col">
                    <div class="text-subtitle1 text-weight-medium">{{ item.productName }}</div>
                    <div class="muted">{{ item.maskedNumber }}</div>
                  </div>
                  <q-icon
                    v-if="item.id === selectedAccount?.id"
                    name="check_circle"
                    color="primary"
                    size="sm"
                    aria-hidden="true"
                  />
                </div>
                <div class="q-mt-sm">
                  <div class="text-caption muted">
                    {{ t('transactions.accounts.availableBalance') }}
                  </div>
                  <MoneyAmount
                    class="text-h6"
                    :amount="item.availableBalance"
                    :currency="item.currency"
                  />
                </div>
              </q-card-section>
            </q-card>
          </div>
        </div>
      </section>

      <!-- Filters -->
      <section class="q-mt-lg" :aria-label="t('transactions.filters.label')">
        <div class="row items-center q-gutter-sm">
          <div role="group" :aria-label="t('transactions.filters.rangeLabel')">
            <q-chip
              v-for="preset in RANGE_PRESETS"
              :key="preset"
              clickable
              :selected="range === preset"
              :outline="range !== preset"
              color="primary"
              :text-color="range === preset ? 'white' : 'primary'"
              icon-selected="check"
              :label="t(`transactions.filters.range.${preset}`)"
              :data-testid="`range-${preset}`"
              @click="filters.apply({ range: preset })"
            />
          </div>
        </div>
        <div class="row items-center q-col-gutter-md q-mt-none">
          <div class="col-12 col-sm">
            <q-input
              v-model="searchText"
              outlined
              dense
              clearable
              :debounce="300"
              :label="t('transactions.filters.search')"
              autocomplete="off"
              data-testid="search"
            >
              <template #prepend>
                <q-icon name="search" aria-hidden="true" />
              </template>
            </q-input>
          </div>
          <div class="col-12 col-sm-auto">
            <q-toggle
              :model-value="disputedOnly"
              :label="t('transactions.filters.disputedOnly')"
              data-testid="disputed-only"
              @update:model-value="filters.apply({ disputedOnly: $event })"
            />
          </div>
        </div>
      </section>

      <!-- Results -->
      <section class="q-mt-md">
        <ListSkeleton
          v-if="query.loading.value"
          :label="t('transactions.list.loading')"
          :rows="8"
        />

        <ErrorState
          v-else-if="query.error.value"
          :title="t('transactions.list.errorTitle')"
          :message="describeError(query.error.value)"
          @retry="query.retry"
        />

        <EmptyState
          v-else-if="query.items.value.length === 0 && hasActiveFilters"
          icon="search_off"
          :title="t('transactions.list.emptyFilteredTitle')"
          :description="t('transactions.list.emptyFilteredDescription')"
        >
          <q-btn
            outline
            no-caps
            color="primary"
            :label="t('transactions.filters.clear')"
            data-testid="clear-filters"
            @click="filters.clearFilters()"
          />
        </EmptyState>

        <EmptyState
          v-else-if="query.items.value.length === 0"
          icon="receipt_long"
          :title="t('transactions.list.emptyTitle')"
          :description="t('transactions.list.emptyDescription')"
        />

        <template v-else>
          <p class="muted text-body2 q-mb-sm" role="status" data-testid="summary">
            {{
              t('transactions.list.summary', {
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
            class="transactions-table"
            :rows="query.items.value"
            :columns="columns"
            :pagination="{ rowsPerPage: 0 }"
            :rows-per-page-options="[0]"
            data-testid="transactions-table"
            @row-click="onRowClick"
          >
            <template #body-cell-merchant="cell">
              <q-td :props="cell">
                <div class="merchant-cell">
                  <router-link class="row-link" :to="detailLocation(cell.row)">
                    {{ cell.row.merchantName }}
                  </router-link>
                  <TransactionBadges
                    :status="cell.row.status"
                    :dispute-status="cell.row.disputeStatus"
                  />
                </div>
              </q-td>
            </template>
            <template #body-cell-amount="cell">
              <q-td :props="cell">
                <MoneyAmount
                  :amount="cell.row.amount"
                  :currency="cell.row.currency"
                  :direction="cell.row.direction"
                />
              </q-td>
            </template>
          </q-table>

          <q-list v-else bordered separator class="rounded-borders" data-testid="transactions-list">
            <q-item
              v-for="row in query.items.value"
              :key="row.id"
              clickable
              :to="detailLocation(row)"
              class="transaction-card"
            >
              <q-item-section>
                <q-item-label class="text-weight-medium">{{ row.merchantName }}</q-item-label>
                <q-item-label caption
                  >{{ formatDate(row.postedAt) }} &middot; {{ row.reference }}</q-item-label
                >
                <q-item-label v-if="row.status === 'PENDING' || row.disputeStatus" class="q-mt-xs">
                  <TransactionBadges :status="row.status" :dispute-status="row.disputeStatus" />
                </q-item-label>
              </q-item-section>
              <q-item-section side top>
                <MoneyAmount
                  class="text-body1 text-weight-medium"
                  :amount="row.amount"
                  :currency="row.currency"
                  :direction="row.direction"
                />
              </q-item-section>
            </q-item>
          </q-list>

          <nav
            v-if="query.totalPages.value > 1"
            class="row justify-center q-mt-md"
            :aria-label="t('transactions.list.pagination')"
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
    </template>
  </q-page>
</template>

<style scoped lang="scss">
// One scrolling row on a phone so two accounts do not push the list off screen; a wrapping grid
// from tablet width up. The padding keeps the focus ring from being clipped by the scroller.
.account-list {
  display: flex;
  gap: 1rem;
  padding: 0.25rem;
  overflow-x: auto;
  scroll-snap-type: x proximity;

  > * {
    flex: 0 0 min(85%, 20rem);
    scroll-snap-align: start;
  }

  @media (width >= 600px) {
    flex-wrap: wrap;
    overflow: visible;

    > * {
      flex: 0 1 20rem;
    }
  }
}

.merchant-cell {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem 0.5rem;
  align-items: center;
}

.account-card {
  cursor: pointer;
  font: inherit;
  color: inherit;
  padding: 0;
  border-width: 1px;

  // A thicker border and a check mark say "selected" without relying on colour alone.
  &--selected {
    border-color: var(--q-primary);
    box-shadow: 0 0 0 1px var(--q-primary);
  }
}

.transactions-table :deep(tbody tr) {
  cursor: pointer;
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
