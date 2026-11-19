CREATE TABLE invoice_debits (
    print_request_id  uuid          NOT NULL,
    invoice_id        uuid          NOT NULL,
    invoice_number    bigint        NOT NULL,
    request_event_id  uuid          NOT NULL,
    outcome           varchar(20)   NOT NULL,
    reason            varchar(30),
    details           jsonb,
    processed_at      timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_invoice_debits               PRIMARY KEY (print_request_id),
    CONSTRAINT uk_invoice_debits_request_event UNIQUE (request_event_id),
    CONSTRAINT ck_invoice_debits_outcome       CHECK (outcome IN ('CONFIRMED', 'REJECTED')),
    CONSTRAINT ck_invoice_debits_reason        CHECK ((outcome = 'REJECTED') = (reason IS NOT NULL)),
    CONSTRAINT ck_invoice_debits_reason_values CHECK (reason IS NULL OR reason IN ('INSUFFICIENT_STOCK', 'PRODUCT_INACTIVE', 'PRODUCT_NOT_FOUND'))
);
CREATE UNIQUE INDEX ux_invoice_debits_confirmed ON invoice_debits (invoice_id) WHERE outcome = 'CONFIRMED';
CREATE INDEX ix_invoice_debits_invoice ON invoice_debits (invoice_id, processed_at DESC);

COMMENT ON TABLE invoice_debits IS 'Log da Saga: resultado de cada solicitação de baixa por nota fiscal';
