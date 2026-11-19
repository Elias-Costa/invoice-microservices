CREATE TABLE invoice_items (
    id                   uuid           NOT NULL,
    invoice_id           uuid           NOT NULL,
    product_id           uuid           NOT NULL,
    product_code         varchar(30)    NOT NULL,
    product_description  varchar(200)   NOT NULL,
    unit_price           numeric(15,2)  NOT NULL,
    quantity             integer        NOT NULL,
    line_total           numeric(15,2)  NOT NULL,
    line_order           integer        NOT NULL,
    CONSTRAINT pk_invoice_items                 PRIMARY KEY (id),
    CONSTRAINT fk_invoice_items_invoice         FOREIGN KEY (invoice_id) REFERENCES invoices (id) ON DELETE CASCADE,
    CONSTRAINT uk_invoice_items_invoice_product UNIQUE (invoice_id, product_id),
    CONSTRAINT uk_invoice_items_invoice_order   UNIQUE (invoice_id, line_order),
    CONSTRAINT ck_invoice_items_quantity        CHECK (quantity > 0),
    CONSTRAINT ck_invoice_items_unit_price      CHECK (unit_price >= 0),
    CONSTRAINT ck_invoice_items_line_total      CHECK (line_total = round(unit_price * quantity, 2)),
    CONSTRAINT ck_invoice_items_line_order      CHECK (line_order > 0)
);
CREATE INDEX ix_invoice_items_product ON invoice_items (product_id);

COMMENT ON TABLE invoice_items IS 'Itens da nota com snapshot do produto (código, descrição, preço)';
