-- SLA due dates (ADR 0007). Additive only; portable to PostgreSQL 18 and H2 in PostgreSQL mode.

-- Nullable first so existing rows can be filled in before the constraint is applied.
alter table dispute add column due_at timestamp with time zone null;

-- Existing disputes get the date they would have been given at creation: created_at plus the SLA of their reason code
-- (F01, F02, A01 = 10 days; P01, P02 = 15; C01-C03 = 30). The intervals are written in hours on purpose: PostgreSQL
-- adds "days" in the session time zone, which would make a day 23 or 25 hours across a daylight saving change, while
-- the application defines a day as exactly 24 hours of UTC time. Both databases accept this literal form.
update dispute
set due_at = case
    when reason_code in ('F01', 'F02', 'A01') then created_at + interval '240' hour
    when reason_code in ('P01', 'P02') then created_at + interval '360' hour
    else created_at + interval '720' hour
end;

alter table dispute alter column due_at set not null;

-- The agent queue ordered by due date (the default) and its overdue filter: both start from the status.
create index ix_dispute_status_due on dispute (status, due_at);
