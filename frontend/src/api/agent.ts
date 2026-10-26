import { api } from './client';
import type {
  AgentDisputeDetail,
  AgentDisputePage,
  AgentQueue,
  DisputeStatus,
  RequestInfoRequest,
  ResolveDisputeRequest,
} from './types';

export type {
  AgentAction,
  AgentDisputeDetail,
  AgentDisputePage,
  AgentDisputeSummary,
  AgentQueue,
  AgentTimelineEvent,
  RequestInfoRequest,
  ResolutionOutcome,
  ResolveDisputeRequest,
} from './types';

export interface AgentQueueQuery {
  /** Defaults to UNASSIGNED on the server. */
  queue?: AgentQueue;
  /** Keep only these statuses. Sent as a repeated `status` parameter. */
  status?: readonly DisputeStatus[];
  /** Case-insensitive "contains" on reference, merchant name or customer name. */
  q?: string;
  /** Zero-based page index. */
  page?: number;
  /** 1 to 100. */
  size?: number;
}

const disputePath = (id: string) => `/agent/disputes/${encodeURIComponent(id)}`;

/** Oldest first (first in, first out). */
export async function listAgentDisputes(
  query: AgentQueueQuery = {},
  signal?: AbortSignal,
): Promise<AgentDisputePage> {
  const { data } = await api.get<AgentDisputePage>('/agent/disputes', {
    params: {
      queue: query.queue,
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

/** Any dispute, with internal notes and the actions this agent may take now. */
export async function getAgentDispute(
  id: string,
  signal?: AbortSignal,
): Promise<AgentDisputeDetail> {
  const { data } = await api.get<AgentDisputeDetail>(disputePath(id), signal ? { signal } : {});
  return data;
}

/** Rejects with 409 INVALID_DISPUTE_TRANSITION or DISPUTE_MODIFIED when someone got there first. */
export async function claimDispute(id: string): Promise<AgentDisputeDetail> {
  const { data } = await api.post<AgentDisputeDetail>(`${disputePath(id)}/claim`);
  return data;
}

/** Asks the customer for more information; the dispute waits for their reply. */
export async function requestDisputeInfo(
  id: string,
  request: RequestInfoRequest,
): Promise<AgentDisputeDetail> {
  const { data } = await api.post<AgentDisputeDetail>(`${disputePath(id)}/request-info`, request);
  return data;
}

/** Closes the dispute as upheld or declined. */
export async function resolveDispute(
  id: string,
  request: ResolveDisputeRequest,
): Promise<AgentDisputeDetail> {
  const { data } = await api.post<AgentDisputeDetail>(`${disputePath(id)}/resolve`, request);
  return data;
}
