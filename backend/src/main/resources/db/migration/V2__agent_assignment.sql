-- Agent back office. Additive only; portable to PostgreSQL 18 and H2 in PostgreSQL mode.

-- Who is working the dispute. Null until an agent claims it, and kept after resolution so the history
-- shows who decided it.
alter table dispute add column assigned_agent_id uuid null;
alter table dispute add constraint fk_dispute_assigned_agent foreign key (assigned_agent_id) references app_user (id);

-- Back-office only; the customer API has no field that could carry it.
alter table dispute_event add column note_internal varchar(1000) null;

-- The agent queue: filtered by status, oldest first.
create index ix_dispute_status_created on dispute (status, created_at);
