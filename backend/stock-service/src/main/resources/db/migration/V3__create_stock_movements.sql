CREATE TABLE stock_movements (
    id                uuid          NOT NULL,
    product_id        uuid          NOT NULL,
    type              varchar(20)   NOT NULL,
    quantity          integer       NOT NULL,
    balance_after     integer       NOT NULL,
    reason            varchar(200),
    reference_id      uuid,
    reference_number  bigint,
    created_by        varchar(100)  NOT NULL,
    created_at        timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_stock_movements               PRIMARY KEY (id),
    CONSTRAINT fk_stock_movements_product       FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_stock_movements_type          CHECK (type IN ('INITIAL', 'INBOUND', 'OUTBOUND', 'INVOICE_DEBIT')),
    CONSTRAINT ck_stock_movements_quantity      CHECK ((type = 'INITIAL' AND quantity >= 0) OR (type <> 'INITIAL' AND quantity > 0)),
    CONSTRAINT ck_stock_movements_balance_after CHECK (balance_after >= 0),
    CONSTRAINT ck_stock_movements_reference     CHECK ((type = 'INVOICE_DEBIT') = (reference_id IS NOT NULL AND reference_number IS NOT NULL)),
    CONSTRAINT ck_stock_movements_reason        CHECK (type IN ('INITIAL', 'INVOICE_DEBIT') OR reason IS NOT NULL)
);
CREATE INDEX ix_stock_movements_product_created ON stock_movements (product_id, created_at DESC);
CREATE UNIQUE INDEX ux_stock_movements_invoice_product ON stock_movements (reference_id, product_id) WHERE type = 'INVOICE_DEBIT';
CREATE INDEX ix_stock_movements_reference ON stock_movements (reference_id) WHERE reference_id IS NOT NULL;

COMMENT ON TABLE stock_movements IS 'Histórico imutável (append-only) de alterações de saldo';
