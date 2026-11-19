CREATE TABLE customers (
    id             uuid          NOT NULL,
    name           varchar(150)  NOT NULL,
    document       varchar(14)   NOT NULL,
    document_type  varchar(4)    NOT NULL,
    email          varchar(150),
    phone          varchar(20),
    active         boolean       NOT NULL DEFAULT true,
    version        bigint        NOT NULL DEFAULT 0,
    created_by     varchar(100)  NOT NULL,
    created_at     timestamptz   NOT NULL DEFAULT now(),
    updated_by     varchar(100),
    updated_at     timestamptz,
    CONSTRAINT pk_customers                 PRIMARY KEY (id),
    CONSTRAINT uk_customers_document        UNIQUE (document),
    CONSTRAINT ck_customers_document_digits CHECK (document ~ '^[0-9]{11}$' OR document ~ '^[0-9]{14}$'),
    CONSTRAINT ck_customers_document_type   CHECK ((document_type = 'CPF' AND length(document) = 11)
                                               OR (document_type = 'CNPJ' AND length(document) = 14))
);
CREATE INDEX ix_customers_active_name ON customers (active, name);
CREATE INDEX ix_customers_search_trgm ON customers USING gin (name gin_trgm_ops);

COMMENT ON TABLE  customers          IS 'Clientes destinatários de notas fiscais (agregado Customer)';
COMMENT ON COLUMN customers.document IS 'CPF ou CNPJ somente dígitos; dígitos verificadores validados na aplicação';
