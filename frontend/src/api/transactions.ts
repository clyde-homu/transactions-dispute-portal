import { api } from './client';
import type { TransactionDetail, TransactionPage } from './types';

export type {
  Channel,
  Eligibility,
  TransactionDetail,
  TransactionDirection,
  TransactionPage,
  TransactionStatus,
  TransactionSummary,
} from './types';

export interface TransactionQuery {
  /** First day to include, `YYYY-MM-DD`, in the bank's time zone. */
  from?: string;
  /** Last day to include, `YYYY-MM-DD`. */
  to?: string;
  /** Case-insensitive "contains" on merchant name or reference. */
  q?: string;
  /** Only transactions that have a non-withdrawn dispute. */
  disputed?: boolean;
  /** Zero-based page index. */
  page?: number;
  /** 1 to 100. */
  size?: number;
}

/** Newest first. `false` and empty values are left out so the URL stays as short as the intent. */
export async function listTransactions(
  accountId: string,
  query: TransactionQuery = {},
  signal?: AbortSignal,
): Promise<TransactionPage> {
  const params = {
    from: query.from || undefined,
    to: query.to || undefined,
    q: query.q || undefined,
    disputed: query.disputed ? true : undefined,
    page: query.page,
    size: query.size,
  };
  const { data } = await api.get<TransactionPage>(
    `/accounts/${encodeURIComponent(accountId)}/transactions`,
    signal ? { params, signal } : { params },
  );
  return data;
}

/** The transaction plus whether (and why not) it can be disputed. 404 for anyone else's. */
export async function getTransaction(id: string, signal?: AbortSignal): Promise<TransactionDetail> {
  const { data } = await api.get<TransactionDetail>(
    `/transactions/${encodeURIComponent(id)}`,
    signal ? { signal } : {},
  );
  return data;
}
