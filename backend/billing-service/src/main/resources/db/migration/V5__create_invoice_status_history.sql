CREATE TABLE invoice_status_history (
    id           uuid          NOT NULL,
    invoice_id   uuid          NOT NULL,
    from_status  varchar(20),
    to_status    varchar(20)   NOT NULL,
    reason       varchar(500),
    changed_by   varchar(100)  NOT NULL,
    changed_at   timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_invoice_status_history         PRIMARY KEY (id),
    CONSTRAINT fk_invoice_status_history_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id) ON DELETE CASCADE,
    CONSTRAINT ck_invoice_status_history_to      CHECK (to_status IN ('OPEN', 'PROCESSING', 'CLOSED', 'CANCELED')),
    CONSTRAINT ck_invoice_status_history_from    CHECK (from_status IS NULL OR from_status IN ('OPEN', 'PROCESSING', 'CLOSED', 'CANCELED'))
);
CREATE INDEX ix_invoice_status_history_invoice ON invoice_status_history (invoice_id, changed_at);

COMMENT ON TABLE invoice_status_history IS 'Linha do tempo de transições de status da nota (append-only)';
