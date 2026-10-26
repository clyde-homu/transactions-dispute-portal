import { api } from './client';
import type {
  CreateDisputeRequest,
  CustomerResponseRequest,
  DisputeDetail,
  DisputePage,
  DisputeStatus,
  ReasonCategory,
} from './types';

export type {
  CreateDisputeRequest,
  DisputeDetail,
  DisputePage,
  DisputeStatus,
  DisputeSummary,
  ReasonCategory,
  TimelineEvent,
} from './types';

export interface DisputeQuery {
  /** Keep only these statuses. Sent as a repeated `status` parameter. */
  status?: readonly DisputeStatus[];
  /** Case-insensitive "contains" on dispute reference or merchant name. */
  q?: string;
  /** Zero-based page index. */
  page?: number;
  /** 1 to 100. */
  size?: number;
}

/** Reason categories with their reasons, in display order. The same for every customer. */
export async function listDisputeReasons(signal?: AbortSignal): Promise<ReasonCategory[]> {
  const { data } = await api.get<ReasonCategory[]>('/dispute-reasons', signal ? { signal } : {});
  return data;
}

/** Rejects with 409 DISPUTE_ALREADY_EXISTS, 422 TRANSACTION_NOT_ELIGIBLE or 400 with field errors. */
export async function createDispute(request: CreateDisputeRequest): Promise<DisputeDetail> {
  const { data } = await api.post<DisputeDetail>('/disputes', request);
  return data;
}

/** Newest first. */
export async function listDisputes(
  query: DisputeQuery = {},
  signal?: AbortSignal,
): Promise<DisputePage> {
  const { data } = await api.get<DisputePage>('/disputes', {
    params: {
      status: query.status?.length ? [...query.status] : undefined,
      q: query.q || undefined,
      page: query.page,
      size: query.size,
    },
    // The contract wants `status=A&status=B`; axios would send `status[]=A&status[]=B`.
    paramsSerializer: { indexes: null },
    ...(signal ? { signal } : {}),
  });
  return data;
}

/** The dispute with its timeline, oldest event first. 404 for anyone else's. */
export async function getDispute(id: string, signal?: AbortSignal): Promise<DisputeDetail> {
  const { data } = await api.get<DisputeDetail>(
    `/disputes/${encodeURIComponent(id)}`,
    signal ? { signal } : {},
  );
  return data;
}

/** Rejects with 409 INVALID_DISPUTE_TRANSITION when the dispute is already closed. */
export async function withdrawDispute(id: string): Promise<DisputeDetail> {
  const { data } = await api.post<DisputeDetail>(`/disputes/${encodeURIComponent(id)}/withdraw`);
  return data;
}

/**
 * Replies to the agent's information request. Rejects with 409 INVALID_DISPUTE_TRANSITION when
 * the dispute is no longer waiting for the customer, or DISPUTE_MODIFIED when it changed at the
 * same moment, and with 400 and field errors for a blank or over-long message.
 */
export async function respondToDispute(
  id: string,
  request: CustomerResponseRequest,
): Promise<DisputeDetail> {
  const { data } = await api.post<DisputeDetail>(
    `/disputes/${encodeURIComponent(id)}/responses`,
    request,
  );
  return data;
}
