CREATE TABLE products (
    id           uuid           NOT NULL,
    code         varchar(30)    NOT NULL,
    description  varchar(200)   NOT NULL,
    unit_price   numeric(15,2)  NOT NULL,
    balance      integer        NOT NULL DEFAULT 0,
    active       boolean        NOT NULL DEFAULT true,
    version      bigint         NOT NULL DEFAULT 0,
    created_by   varchar(100)   NOT NULL,
    created_at   timestamptz    NOT NULL DEFAULT now(),
    updated_by   varchar(100),
    updated_at   timestamptz,
    CONSTRAINT pk_products             PRIMARY KEY (id),
    CONSTRAINT uk_products_code        UNIQUE (code),
    CONSTRAINT ck_products_code_format CHECK (code ~ '^[A-Z0-9][A-Z0-9._-]{0,29}$'),
    CONSTRAINT ck_products_unit_price  CHECK (unit_price >= 0),
    CONSTRAINT ck_products_balance     CHECK (balance >= 0)
);
CREATE INDEX ix_products_active_code ON products (active, code);
CREATE INDEX ix_products_search_trgm ON products USING gin (code gin_trgm_ops, description gin_trgm_ops);

COMMENT ON TABLE  products             IS 'Produtos cadastrados no estoque (agregado Product)';
COMMENT ON COLUMN products.code        IS 'Código de negócio, único, maiúsculas, imutável';
COMMENT ON COLUMN products.balance     IS 'Saldo atual; alterado somente via stock_movements';
COMMENT ON COLUMN products.version     IS 'Locking otimista (JPA @Version)';
