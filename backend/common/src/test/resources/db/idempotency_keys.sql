-- Test-only copy of the idempotency_keys DDL (modelo-de-dados.md, Apêndice A.3).
-- The services own the real Flyway migration (V5/V6 create_technical_tables); this script lets the
-- common module validate its JPA mapping against the reference schema (ddl-auto=validate).
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
