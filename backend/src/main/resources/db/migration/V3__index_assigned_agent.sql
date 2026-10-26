-- The agent's "mine" queue filters on the assignee and orders by age; PostgreSQL does not index
-- foreign keys on its own.
create index ix_dispute_assigned_agent on dispute (assigned_agent_id, created_at);
