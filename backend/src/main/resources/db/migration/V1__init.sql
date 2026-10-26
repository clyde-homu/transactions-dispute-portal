-- Portable DDL: runs unchanged on PostgreSQL 18 and on H2 in PostgreSQL mode (standalone run).
-- Ids are generated in Java. No partial indexes and no database-specific functions.

create table app_user (
    id            uuid                     not null,
    username      varchar(50)              not null,
    password_hash varchar(100)             not null,
    display_name  varchar(100)             not null,
    role          varchar(20)              not null,
    created_at    timestamp with time zone not null,
    constraint pk_app_user primary key (id),
    constraint uq_app_user_username unique (username),
    constraint ck_app_user_role check (role in ('CUSTOMER', 'AGENT'))
);

create table account (
    id                uuid                     not null,
    owner_id          uuid                     not null,
    masked_number     varchar(20)              not null,
    product_name      varchar(100)             not null,
    currency          char(3)                  not null,
    available_balance numeric(19, 4)           not null,
    created_at        timestamp with time zone not null,
    constraint pk_account primary key (id),
    constraint fk_account_owner foreign key (owner_id) references app_user (id)
);

create table bank_transaction (
    id                uuid                     not null,
    account_id        uuid                     not null,
    reference         varchar(40)              not null,
    posted_at         timestamp with time zone not null,
    amount            numeric(19, 4)           not null,
    currency          char(3)                  not null,
    direction         varchar(6)               not null,
    status            varchar(10)              not null,
    merchant_name     varchar(120)             not null,
    merchant_category varchar(60)              not null,
    channel           varchar(20)              not null,
    constraint pk_bank_transaction primary key (id),
    constraint uq_bank_transaction_reference unique (reference),
    constraint fk_bank_transaction_account foreign key (account_id) references account (id),
    constraint ck_bank_transaction_amount check (amount > 0),
    constraint ck_bank_transaction_direction check (direction in ('DEBIT', 'CREDIT')),
    constraint ck_bank_transaction_status check (status in ('PENDING', 'SETTLED')),
    constraint ck_bank_transaction_channel check (channel in ('CARD', 'DEBIT_ORDER', 'EFT', 'ATM'))
);

create table dispute (
    id              uuid                     not null,
    reference       varchar(20)              not null,
    transaction_id  uuid                     not null,
    customer_id     uuid                     not null,
    reason_code     varchar(10)              not null,
    disputed_amount numeric(19, 4)           not null,
    currency        char(3)                  not null,
    description     varchar(1000)            not null,
    status          varchar(30)              not null,
    created_at      timestamp with time zone not null,
    updated_at      timestamp with time zone not null,
    resolved_at     timestamp with time zone null,
    version         bigint                   not null default 0,
    constraint pk_dispute primary key (id),
    constraint uq_dispute_reference unique (reference),
    constraint fk_dispute_transaction foreign key (transaction_id) references bank_transaction (id),
    constraint fk_dispute_customer foreign key (customer_id) references app_user (id),
    constraint ck_dispute_amount check (disputed_amount > 0)
);

create table dispute_event (
    id          uuid                     not null,
    dispute_id  uuid                     not null,
    actor_role  varchar(20)              not null,
    actor_id    uuid                     null,
    from_status varchar(30)              null,
    to_status   varchar(30)              not null,
    message     varchar(1000)            null,
    created_at  timestamp with time zone not null,
    constraint pk_dispute_event primary key (id),
    constraint fk_dispute_event_dispute foreign key (dispute_id) references dispute (id),
    constraint fk_dispute_event_actor foreign key (actor_id) references app_user (id),
    constraint ck_dispute_event_actor_role check (actor_role in ('CUSTOMER', 'AGENT', 'SYSTEM'))
);

create sequence dispute_reference_seq start with 1;

-- Owner lookup for "my accounts", transaction list per account, dispute history per customer,
-- the one-dispute-per-transaction check, and the dispute timeline.
create index ix_account_owner on account (owner_id);
create index ix_bank_transaction_account_posted on bank_transaction (account_id, posted_at desc);
create index ix_dispute_customer_created on dispute (customer_id, created_at desc);
create index ix_dispute_transaction on dispute (transaction_id);
create index ix_dispute_event_dispute_created on dispute_event (dispute_id, created_at);
