CREATE TABLE outbox_events (
    id              uuid          NOT NULL,
    aggregate_type  varchar(50)   NOT NULL,
    aggregate_id    uuid          NOT NULL,
    event_type      varchar(100)  NOT NULL,
    topic           varchar(100)  NOT NULL,
    partition_key   varchar(100)  NOT NULL,
    payload         jsonb         NOT NULL,
    headers         jsonb         NOT NULL DEFAULT '{}'::jsonb,
    attempts        integer       NOT NULL DEFAULT 0,
    last_error      text,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    published_at    timestamptz,
    failed_at       timestamptz,
    CONSTRAINT pk_outbox_events PRIMARY KEY (id),
    CONSTRAINT ck_outbox_events_terminal CHECK (published_at IS NULL OR failed_at IS NULL)
);
CREATE INDEX ix_outbox_events_pending   ON outbox_events (created_at) WHERE published_at IS NULL AND failed_at IS NULL;
CREATE INDEX ix_outbox_events_aggregate ON outbox_events (aggregate_type, aggregate_id);

COMMENT ON TABLE outbox_events IS 'Outbox transacional: eventos a publicar no Kafka (published_at IS NULL AND failed_at IS NULL = pendente; failed_at = abandonado após falhas definitivas)';

CREATE TABLE processed_events (
    event_id        uuid          NOT NULL,
    consumer_group  varchar(100)  NOT NULL,
    event_type      varchar(100)  NOT NULL,
    processed_at    timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_processed_events PRIMARY KEY (event_id, consumer_group)
);
CREATE INDEX ix_processed_events_processed_at ON processed_events (processed_at);

COMMENT ON TABLE processed_events IS 'Deduplicação de eventos consumidos (idempotência de mensageria)';

CREATE TABLE idempotency_keys (
    idempotency_key   uuid          NOT NULL,
    user_id           varchar(100)  NOT NULL,
    route             varchar(200)  NOT NULL,
    request_hash      varchar(64)   NOT NULL,
    state             varchar(20)   NOT NULL,
    response_status   integer,
    response_body     jsonb,
    response_headers  jsonb,
    created_at        timestamptz   NOT NULL DEFAULT now(),
    expires_at        timestamptz   NOT NULL,
    CONSTRAINT pk_idempotency_keys           PRIMARY KEY (idempotency_key, user_id),
    CONSTRAINT ck_idempotency_keys_state     CHECK (state IN ('IN_FLIGHT', 'COMPLETED')),
    CONSTRAINT ck_idempotency_keys_completed CHECK (state <> 'COMPLETED' OR response_status IS NOT NULL)
);
CREATE INDEX ix_idempotency_keys_expires ON idempotency_keys (expires_at);

COMMENT ON TABLE idempotency_keys IS 'Idempotência HTTP: respostas armazenadas por Idempotency-Key e usuário (TTL 24 h)';
