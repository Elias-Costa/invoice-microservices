DO $$
BEGIN
    IF NOT ${seed} THEN
        RETURN;
    END IF;

    INSERT INTO products (id, code, description, unit_price, balance, active, created_by) VALUES
        ('a0000000-0000-4000-8000-000000000001', 'PAR-M6-20',   'Parafuso sextavado M6 x 20 mm',          0.35,  500, true,  'seed'),
        ('a0000000-0000-4000-8000-000000000002', 'POR-M6',      'Porca sextavada M6',                     0.20,  480, true,  'seed'),
        ('a0000000-0000-4000-8000-000000000003', 'ARR-M6',      'Arruela lisa M6',                       0.10, 1000, true,  'seed'),
        ('a0000000-0000-4000-8000-000000000004', 'CHV-FEN-6',   'Chave de fenda 6 mm',                   12.90,   25, true,  'seed'),
        ('a0000000-0000-4000-8000-000000000005', 'CHV-PHI-2',   'Chave Phillips nº 2',                   13.50,   18, true,  'seed'),
        ('a0000000-0000-4000-8000-000000000006', 'MAR-500',     'Martelo unha 500 g',                    45.00,   10, true,  'seed'),
        ('a0000000-0000-4000-8000-000000000007', 'TRE-5M',      'Trena 5 m',                             22.00,    1, true,  'seed'),
        ('a0000000-0000-4000-8000-000000000008', 'FUR-IMP-650', 'Furadeira de impacto 650 W',           289.90,    3, true,  'seed'),
        ('a0000000-0000-4000-8000-000000000009', 'BRO-ACO-8',   'Broca aço rápido 8 mm',                 8.75,    0, true,  'seed'),
        ('a0000000-0000-4000-8000-000000000010', 'FIT-ISO-19',  'Fita isolante 19 mm x 10 m',             4.50,   60, true,  'seed'),
        ('a0000000-0000-4000-8000-000000000011', 'LIX-120',     'Lixa d''água grão 120',                  1.80,  200, true,  'seed'),
        ('a0000000-0000-4000-8000-000000000012', 'SER-MAN-22',  'Serrote manual 22" (descontinuado)',    35.00,    4, false, 'seed')
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO stock_movements (id, product_id, type, quantity, balance_after, created_by, created_at) VALUES
        ('f0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000001', 'INITIAL',  600,  600, 'seed', now() - interval '30 days'),
        ('f0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000002', 'INITIAL',  580,  580, 'seed', now() - interval '30 days'),
        ('f0000000-0000-4000-8000-000000000003', 'a0000000-0000-4000-8000-000000000003', 'INITIAL', 1000, 1000, 'seed', now() - interval '30 days'),
        ('f0000000-0000-4000-8000-000000000004', 'a0000000-0000-4000-8000-000000000004', 'INITIAL',   25,   25, 'seed', now() - interval '30 days'),
        ('f0000000-0000-4000-8000-000000000005', 'a0000000-0000-4000-8000-000000000005', 'INITIAL',   18,   18, 'seed', now() - interval '30 days'),
        ('f0000000-0000-4000-8000-000000000006', 'a0000000-0000-4000-8000-000000000006', 'INITIAL',   10,   10, 'seed', now() - interval '30 days'),
        ('f0000000-0000-4000-8000-000000000007', 'a0000000-0000-4000-8000-000000000007', 'INITIAL',    1,    1, 'seed', now() - interval '30 days'),
        ('f0000000-0000-4000-8000-000000000008', 'a0000000-0000-4000-8000-000000000008', 'INITIAL',    3,    3, 'seed', now() - interval '30 days'),
        ('f0000000-0000-4000-8000-000000000009', 'a0000000-0000-4000-8000-000000000009', 'INITIAL',    0,    0, 'seed', now() - interval '30 days'),
        ('f0000000-0000-4000-8000-000000000010', 'a0000000-0000-4000-8000-000000000010', 'INITIAL',   60,   60, 'seed', now() - interval '30 days'),
        ('f0000000-0000-4000-8000-000000000011', 'a0000000-0000-4000-8000-000000000011', 'INITIAL',  200,  200, 'seed', now() - interval '30 days'),
        ('f0000000-0000-4000-8000-000000000012', 'a0000000-0000-4000-8000-000000000012', 'INITIAL',    4,    4, 'seed', now() - interval '30 days')
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO stock_movements (id, product_id, type, quantity, balance_after, reference_id, reference_number, created_by, created_at) VALUES
        ('f0000000-0000-4000-8000-000000000013', 'a0000000-0000-4000-8000-000000000001', 'INVOICE_DEBIT', 100, 500, 'b0000000-0000-4000-8000-000000000001', 1, 'admin', now() - interval '7 days'),
        ('f0000000-0000-4000-8000-000000000014', 'a0000000-0000-4000-8000-000000000002', 'INVOICE_DEBIT', 100, 480, 'b0000000-0000-4000-8000-000000000001', 1, 'admin', now() - interval '7 days')
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO invoice_debits (print_request_id, invoice_id, invoice_number, request_event_id, outcome, details, processed_at) VALUES
        ('d0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000001', 1,
         'e0000000-0000-4000-8000-000000000001', 'CONFIRMED',
         '[{"productId":"a0000000-0000-4000-8000-000000000001","quantity":100,"balanceAfter":500},
           {"productId":"a0000000-0000-4000-8000-000000000002","quantity":100,"balanceAfter":480}]'::jsonb,
         now() - interval '7 days')
    ON CONFLICT DO NOTHING;
END;
$$;
