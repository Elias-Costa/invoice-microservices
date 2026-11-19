CREATE SEQUENCE invoice_number_seq AS bigint START WITH 1 INCREMENT BY 1 NO CYCLE CACHE 1;

CREATE TABLE invoices (
    id                        uuid           NOT NULL,
    number                    bigint         NOT NULL DEFAULT nextval('invoice_number_seq'),
    status                    varchar(20)    NOT NULL,
    customer_id               uuid           NOT NULL,
    customer_name             varchar(150)   NOT NULL,
    customer_document         varchar(14)    NOT NULL,
    total                     numeric(15,2)  NOT NULL DEFAULT 0,
    current_print_request_id  uuid,
    processing_started_at     timestamptz,
    last_failure_reason       varchar(500),
    cancel_reason             varchar(500),
    version                   bigint         NOT NULL DEFAULT 0,
    created_by                varchar(100)   NOT NULL,
    created_at                timestamptz    NOT NULL DEFAULT now(),
    updated_by                varchar(100),
    updated_at                timestamptz,
    closed_at                 timestamptz,
    canceled_at               timestamptz,
    CONSTRAINT pk_invoices               PRIMARY KEY (id),
    CONSTRAINT uk_invoices_number        UNIQUE (number),
    CONSTRAINT fk_invoices_customer      FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT ck_invoices_status        CHECK (status IN ('OPEN', 'PROCESSING', 'CLOSED', 'CANCELED')),
    CONSTRAINT ck_invoices_total         CHECK (total >= 0),
    CONSTRAINT ck_invoices_closed        CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL)),
    CONSTRAINT ck_invoices_canceled      CHECK ((status = 'CANCELED') = (canceled_at IS NOT NULL)),
    CONSTRAINT ck_invoices_cancel_reason CHECK ((status = 'CANCELED') = (cancel_reason IS NOT NULL)),
    CONSTRAINT ck_invoices_processing    CHECK (status <> 'PROCESSING'
                                               OR (current_print_request_id IS NOT NULL AND processing_started_at IS NOT NULL))
);
CREATE INDEX ix_invoices_status_created   ON invoices (status, created_at DESC);
CREATE INDEX ix_invoices_customer_created ON invoices (customer_id, created_at DESC);
CREATE INDEX ix_invoices_created          ON invoices (created_at DESC);
CREATE INDEX ix_invoices_processing       ON invoices (processing_started_at) WHERE status = 'PROCESSING';

COMMENT ON TABLE  invoices                          IS 'Notas fiscais (agregado Invoice)';
COMMENT ON COLUMN invoices.number                   IS 'Numeração sequencial de negócio; lacunas toleradas';
COMMENT ON COLUMN invoices.current_print_request_id IS 'Tentativa de impressão corrente; resultados com outro id são ignorados';
