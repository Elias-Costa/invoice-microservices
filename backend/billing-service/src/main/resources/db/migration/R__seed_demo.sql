DO $$
DECLARE
    conflicting_number bigint;
    inserted_invoice_ids uuid[];
BEGIN
    IF NOT ${seed} THEN
        RETURN;
    END IF;

    -- Serialize the reserved-number check and sequence adjustment with invoice inserts.
    LOCK TABLE invoices IN SHARE ROW EXCLUSIVE MODE;
    SELECT number INTO conflicting_number
      FROM invoices
     WHERE number BETWEEN 1 AND 5
       AND id <> ('b0000000-0000-4000-8000-' || lpad(number::text, 12, '0'))::uuid
     ORDER BY number
     LIMIT 1;
    IF FOUND THEN
        RAISE EXCEPTION 'Demo invoice number % is already in use', conflicting_number;
    END IF;

    INSERT INTO customers (id, name, document, document_type, created_by, created_at) VALUES
        ('c0000000-0000-4000-8000-000000000001', 'Oficina Mecânica Silva Ltda', '11222333000181', 'CNPJ', 'seed', now() - interval '30 days'),
        ('c0000000-0000-4000-8000-000000000002', 'Construtora Horizonte S.A.',  '45678912000155', 'CNPJ', 'seed', now() - interval '30 days'),
        ('c0000000-0000-4000-8000-000000000003', 'Marcenaria Bom Corte ME',    '98765432000198', 'CNPJ', 'seed', now() - interval '30 days'),
        ('c0000000-0000-4000-8000-000000000004', 'Ana Paula Ribeiro',          '52998224725', 'CPF', 'seed', now() - interval '30 days'),
        ('c0000000-0000-4000-8000-000000000005', 'Carlos Eduardo Menezes',     '84512369791', 'CPF', 'seed', now() - interval '30 days')
    ON CONFLICT (id) DO NOTHING;

    WITH inserted_invoices AS (
    INSERT INTO invoices (id, number, status, customer_id, customer_name, customer_document, total,
                          current_print_request_id, cancel_reason, created_by, created_at, updated_by,
                          updated_at, closed_at, canceled_at)
    SELECT demo.id::uuid, demo.number, demo.status, customer.id, customer.name, customer.document, demo.total,
           demo.print_request_id::uuid, demo.cancel_reason, demo.created_by, now() - interval '14 days',
           demo.updated_by, coalesce(demo.closed_at, demo.canceled_at), demo.closed_at, demo.canceled_at
      FROM (VALUES
        ('b0000000-0000-4000-8000-000000000001', 1, 'CLOSED',   'c0000000-0000-4000-8000-000000000001', 55.00, 'd0000000-0000-4000-8000-000000000001', NULL, 'admin', 'system', now() - interval '7 days', NULL),
        ('b0000000-0000-4000-8000-000000000002', 2, 'CANCELED', 'c0000000-0000-4000-8000-000000000002', 70.80, NULL, 'Pedido duplicado', 'operator', 'admin', NULL, now() - interval '6 days'),
        ('b0000000-0000-4000-8000-000000000003', 3, 'OPEN',     'c0000000-0000-4000-8000-000000000003', 22.00, NULL, NULL, 'operator', NULL, NULL, NULL),
        ('b0000000-0000-4000-8000-000000000004', 4, 'OPEN',     'c0000000-0000-4000-8000-000000000004', 44.50, NULL, NULL, 'operator', NULL, NULL, NULL),
        ('b0000000-0000-4000-8000-000000000005', 5, 'OPEN',     'c0000000-0000-4000-8000-000000000005', 35.50, NULL, NULL, 'operator', NULL, NULL, NULL)
      ) AS demo(id, number, status, customer_id, total, print_request_id, cancel_reason, created_by, updated_by,
                closed_at, canceled_at)
      JOIN customers AS customer ON customer.id = demo.customer_id::uuid
    ON CONFLICT (id) DO NOTHING
    RETURNING id
    )
    SELECT array_agg(id) INTO inserted_invoice_ids FROM inserted_invoices;

    INSERT INTO invoice_items (id, invoice_id, product_id, product_code, product_description, unit_price,
                               quantity, line_total, line_order)
    SELECT demo.id::uuid, demo.invoice_id::uuid, demo.product_id::uuid, demo.product_code,
           demo.product_description, demo.unit_price, demo.quantity, demo.line_total, demo.line_order
      FROM (VALUES
        ('10000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000001', 'PAR-M6-20',  'Parafuso sextavado M6 x 20 mm', 0.35, 100, 35.00, 1),
        ('10000000-0000-4000-8000-000000000002', 'b0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000002', 'POR-M6',     'Porca sextavada M6',           0.20, 100, 20.00, 2),
        ('10000000-0000-4000-8000-000000000003', 'b0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000006', 'MAR-500',    'Martelo unha 500 g',          45.00,   1, 45.00, 1),
        ('10000000-0000-4000-8000-000000000004', 'b0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000004', 'CHV-FEN-6',  'Chave de fenda 6 mm',         12.90,   2, 25.80, 2),
        ('10000000-0000-4000-8000-000000000005', 'b0000000-0000-4000-8000-000000000003', 'a0000000-0000-4000-8000-000000000007', 'TRE-5M',     'Trena 5 m',                   22.00,   1, 22.00, 1),
        ('10000000-0000-4000-8000-000000000006', 'b0000000-0000-4000-8000-000000000004', 'a0000000-0000-4000-8000-000000000007', 'TRE-5M',     'Trena 5 m',                   22.00,   1, 22.00, 1),
        ('10000000-0000-4000-8000-000000000007', 'b0000000-0000-4000-8000-000000000004', 'a0000000-0000-4000-8000-000000000010', 'FIT-ISO-19', 'Fita isolante 19 mm x 10 m',    4.50,   5, 22.50, 2),
        ('10000000-0000-4000-8000-000000000008', 'b0000000-0000-4000-8000-000000000005', 'a0000000-0000-4000-8000-000000000009', 'BRO-ACO-8',  'Broca aço rápido 8 mm',         8.75,   2, 17.50, 1),
        ('10000000-0000-4000-8000-000000000009', 'b0000000-0000-4000-8000-000000000005', 'a0000000-0000-4000-8000-000000000011', 'LIX-120',    'Lixa d''água grão 120',         1.80,  10, 18.00, 2)
      ) AS demo(id, invoice_id, product_id, product_code, product_description, unit_price, quantity, line_total, line_order)
     WHERE demo.invoice_id::uuid = ANY (inserted_invoice_ids)
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO invoice_status_history (id, invoice_id, from_status, to_status, reason, changed_by, changed_at)
    SELECT demo.id::uuid, demo.invoice_id::uuid, demo.from_status, demo.to_status, demo.reason,
           demo.changed_by, demo.changed_at
      FROM (VALUES
        ('20000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000001', NULL,         'OPEN',       NULL, 'admin',    now() - interval '14 days'),
        ('20000000-0000-4000-8000-000000000002', 'b0000000-0000-4000-8000-000000000001', 'OPEN',       'PROCESSING', NULL, 'admin',    now() - interval '7 days' - interval '1 minute'),
        ('20000000-0000-4000-8000-000000000003', 'b0000000-0000-4000-8000-000000000001', 'PROCESSING', 'CLOSED',     NULL, 'system',   now() - interval '7 days'),
        ('20000000-0000-4000-8000-000000000004', 'b0000000-0000-4000-8000-000000000002', NULL,         'OPEN',       NULL, 'operator', now() - interval '14 days'),
        ('20000000-0000-4000-8000-000000000005', 'b0000000-0000-4000-8000-000000000002', 'OPEN', 'CANCELED', 'Pedido duplicado', 'admin', now() - interval '6 days'),
        ('20000000-0000-4000-8000-000000000006', 'b0000000-0000-4000-8000-000000000003', NULL,         'OPEN',       NULL, 'operator', now() - interval '14 days'),
        ('20000000-0000-4000-8000-000000000007', 'b0000000-0000-4000-8000-000000000004', NULL,         'OPEN',       NULL, 'operator', now() - interval '14 days'),
        ('20000000-0000-4000-8000-000000000008', 'b0000000-0000-4000-8000-000000000005', NULL,         'OPEN',       NULL, 'operator', now() - interval '14 days')
      ) AS demo(id, invoice_id, from_status, to_status, reason, changed_by, changed_at)
     WHERE demo.invoice_id::uuid = ANY (inserted_invoice_ids)
    ON CONFLICT (id) DO NOTHING;

    IF (SELECT last_value < 5 OR (last_value = 5 AND NOT is_called) FROM invoice_number_seq) THEN
        PERFORM setval('invoice_number_seq', 5);
    END IF;
END;
$$;
