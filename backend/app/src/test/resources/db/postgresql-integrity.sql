CREATE FUNCTION pg_temp.assert_rejected(p_statement TEXT, p_sqlstate TEXT) RETURNS VOID
    LANGUAGE plpgsql
AS $$
DECLARE
    actual_sqlstate TEXT;
BEGIN
    BEGIN
        EXECUTE p_statement;
    EXCEPTION WHEN OTHERS THEN
        GET STACKED DIAGNOSTICS actual_sqlstate = RETURNED_SQLSTATE;
        IF actual_sqlstate = p_sqlstate THEN
            RETURN;
        END IF;
        RAISE;
    END;
    RAISE EXCEPTION 'Expected SQLSTATE %, but statement succeeded: %', p_sqlstate, p_statement
        USING ERRCODE = 'P0001';
END;
$$;

INSERT INTO organizacoes (id, nome) VALUES
    ('00000000-0000-0000-0000-000000000001', 'Organizacao teste A'),
    ('00000000-0000-0000-0000-000000000002', 'Organizacao teste B');

INSERT INTO usuarios (id, organizacao_id, email, senha_hash)
VALUES (
    '00000000-0000-0000-0000-000000000010',
    '00000000-0000-0000-0000-000000000001',
    'operador@example.invalid',
    'hash-de-teste'
);

INSERT INTO cedentes (id, organizacao_id, nome, documento) VALUES
    ('00000000-0000-0000-0000-000000000020', '00000000-0000-0000-0000-000000000001', 'Cedente A', 'TESTE-A'),
    ('00000000-0000-0000-0000-000000000021', '00000000-0000-0000-0000-000000000002', 'Cedente B', 'TESTE-B');

INSERT INTO recebiveis (
    id, organizacao_id, cedente_id, modalidade, valor_face_brl, prazo_meses
) VALUES
    (
        '00000000-0000-0000-0000-000000000030',
        '00000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-000000000020',
        'DUPLICATA_MERCANTIL',
        100000.00,
        3
    ),
    (
        '00000000-0000-0000-0000-000000000033',
        '00000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-000000000020',
        'DUPLICATA_MERCANTIL',
        1000.00,
        1
    ),
    (
        '00000000-0000-0000-0000-000000000034',
        '00000000-0000-0000-0000-000000000001',
        '00000000-0000-0000-0000-000000000020',
        'CHEQUE_PRE_DATADO',
        25000.00,
        2
    );

INSERT INTO taxas_base (id, taxa_mensal, vigente_em)
VALUES (
    '00000000-0000-0000-0000-000000000040',
    0.0100000000,
    '2026-01-01T00:00:00Z'
);

INSERT INTO spreads_recebiveis (id, modalidade, spread_mensal, vigente_em)
VALUES
    (
        '00000000-0000-0000-0000-000000000041',
        'DUPLICATA_MERCANTIL',
        0.0150000000,
        '2026-01-01T00:00:00Z'
    ),
    (
        '00000000-0000-0000-0000-000000000043',
        'CHEQUE_PRE_DATADO',
        0.0250000000,
        '2026-01-01T00:00:00Z'
    );

INSERT INTO cotacoes_cambiais (
    id, taxa_brl_por_unidade, vigente_em
) VALUES (
    '00000000-0000-0000-0000-000000000042',
    5.4321000000,
    '2026-01-02T00:00:00Z'
);

SELECT pg_temp.assert_rejected(
    $$INSERT INTO cotacoes_cambiais (id, taxa_brl_por_unidade, vigente_em)
      VALUES ('00000000-0000-0000-0000-000000000043', -1, '2026-01-02T00:00:00Z')$$,
    '23514'
);

SELECT pg_temp.assert_rejected(
    $$INSERT INTO recebiveis (id, organizacao_id, cedente_id, modalidade, valor_face_brl, prazo_meses)
      VALUES ('00000000-0000-0000-0000-000000000031',
              '00000000-0000-0000-0000-000000000001',
              '00000000-0000-0000-0000-000000000021',
              'DUPLICATA_MERCANTIL', 100.00, 1)$$,
    '23503'
);

SELECT pg_temp.assert_rejected(
    $$INSERT INTO recebiveis (id, organizacao_id, cedente_id, modalidade, valor_face_brl, prazo_meses)
      VALUES ('00000000-0000-0000-0000-000000000032',
              '00000000-0000-0000-0000-000000000001',
              '00000000-0000-0000-0000-000000000020',
              'DUPLICATA_MERCANTIL', -1.00, 1)$$,
    '23514'
);

SELECT pg_temp.assert_rejected(
    $$INSERT INTO recebiveis (id, organizacao_id, cedente_id, modalidade, valor_face_brl, prazo_meses)
      VALUES ('00000000-0000-0000-0000-000000000035',
              '00000000-0000-0000-0000-000000000001',
              '00000000-0000-0000-0000-000000000020',
              'DUPLICATA', 100.00, 1)$$,
    '23514'
);

SELECT pg_temp.assert_rejected(
    $$INSERT INTO usuarios (id, organizacao_id, email, senha_hash)
      VALUES ('00000000-0000-0000-0000-000000000011',
              '00000000-0000-0000-0000-000000000001',
              'OPERADOR@example.invalid', 'hash-de-teste')$$,
    '23505'
);

INSERT INTO liquidacoes (
    id, organizacao_id, recebivel_id, modalidade, valor_face_brl, prazo_meses,
    versao_recebivel, taxa_base_id, taxa_base_mensal, taxa_base_vigente_em,
    spread_id, spread_mensal, spread_vigente_em, valor_presente_brl, desagio_brl,
    moeda_pagamento, valor_pagamento, liquidada_em
) VALUES (
    '00000000-0000-0000-0000-000000000050',
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000030',
    'DUPLICATA_MERCANTIL',
    100000.00,
    3,
    1,
    '00000000-0000-0000-0000-000000000040',
    0.0100000000,
    '2026-01-01T00:00:00Z',
    '00000000-0000-0000-0000-000000000041',
    0.0150000000,
    '2026-01-01T00:00:00Z',
    92859.94,
    7140.06,
    'BRL',
    92859.94,
    '2026-01-02T00:00:00Z'
);

INSERT INTO liquidacoes (
    id, organizacao_id, recebivel_id, modalidade, valor_face_brl, prazo_meses,
    versao_recebivel, taxa_base_id, taxa_base_mensal, taxa_base_vigente_em,
    spread_id, spread_mensal, spread_vigente_em, valor_presente_brl, desagio_brl,
    moeda_pagamento, valor_pagamento, liquidada_em
) VALUES (
    '00000000-0000-0000-0000-000000000056',
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000034',
    'CHEQUE_PRE_DATADO',
    25000.00,
    2,
    1,
    '00000000-0000-0000-0000-000000000040',
    0.0100000000,
    '2026-01-01T00:00:00Z',
    '00000000-0000-0000-0000-000000000043',
    0.0250000000,
    '2026-01-01T00:00:00Z',
    23337.77,
    1662.23,
    'BRL',
    23337.77,
    '2026-01-02T00:00:00Z'
);

SELECT pg_temp.assert_rejected(
    $$INSERT INTO liquidacoes (
          id, organizacao_id, recebivel_id, modalidade, valor_face_brl, prazo_meses,
          versao_recebivel, taxa_base_id, taxa_base_mensal, taxa_base_vigente_em,
          spread_id, spread_mensal, spread_vigente_em, valor_presente_brl, desagio_brl,
          moeda_pagamento, valor_pagamento, liquidada_em
      ) VALUES (
          '00000000-0000-0000-0000-000000000051',
          '00000000-0000-0000-0000-000000000002',
          '00000000-0000-0000-0000-000000000030',
          'DUPLICATA_MERCANTIL', 100000.00, 3, 1,
          '00000000-0000-0000-0000-000000000040', 0.0100000000, '2026-01-01T00:00:00Z',
          '00000000-0000-0000-0000-000000000041', 0.0150000000, '2026-01-01T00:00:00Z',
          92859.94, 7140.06, 'BRL', 92859.94, '2026-01-02T00:00:00Z'
      )$$,
    '23503'
);

SELECT pg_temp.assert_rejected(
    $$INSERT INTO liquidacoes (
          id, organizacao_id, recebivel_id, modalidade, valor_face_brl, prazo_meses,
          versao_recebivel, taxa_base_id, taxa_base_mensal, taxa_base_vigente_em,
          spread_id, spread_mensal, spread_vigente_em, valor_presente_brl, desagio_brl,
          moeda_pagamento, cotacao_cambial_id, taxa_brl_por_unidade, cotacao_vigente_em,
          cotacao_valida_ate, valor_pagamento, liquidada_em
      ) VALUES (
          '00000000-0000-0000-0000-000000000054',
          '00000000-0000-0000-0000-000000000001',
          '00000000-0000-0000-0000-000000000033',
          'DUPLICATA_MERCANTIL', 1000.00, 1, 1,
          '00000000-0000-0000-0000-000000000040', 0.0100000000, '2026-01-01T00:00:00Z',
          '00000000-0000-0000-0000-000000000041', 0.0150000000, '2026-01-01T00:00:00Z',
          900.00, 100.00, 'USD',
          '00000000-0000-0000-0000-000000000042', 5.4322000000, '2026-01-02T00:00:00Z',
          '2026-01-02T00:30:00Z', 165.68, '2026-01-02T00:15:00Z'
      )$$,
    '23503'
);

SELECT pg_temp.assert_rejected(
    $$INSERT INTO liquidacoes (
          id, organizacao_id, recebivel_id, modalidade, valor_face_brl, prazo_meses,
          versao_recebivel, taxa_base_id, taxa_base_mensal, taxa_base_vigente_em,
          spread_id, spread_mensal, spread_vigente_em, valor_presente_brl, desagio_brl,
          moeda_pagamento, valor_pagamento, liquidada_em
      ) VALUES (
          '00000000-0000-0000-0000-000000000053',
          '00000000-0000-0000-0000-000000000001',
          '00000000-0000-0000-0000-000000000033',
          'DUPLICATA_MERCANTIL', 999.00, 1, 1,
          '00000000-0000-0000-0000-000000000040', 0.0100000000, '2026-01-01T00:00:00Z',
          '00000000-0000-0000-0000-000000000041', 0.0150000000, '2026-01-01T00:00:00Z',
          900.00, 99.00, 'BRL', 900.00, '2026-01-02T00:00:00Z'
      )$$,
    '23503'
);

INSERT INTO liquidacoes (
    id, organizacao_id, recebivel_id, modalidade, valor_face_brl, prazo_meses,
    versao_recebivel, taxa_base_id, taxa_base_mensal, taxa_base_vigente_em,
    spread_id, spread_mensal, spread_vigente_em, valor_presente_brl, desagio_brl,
    moeda_pagamento, cotacao_cambial_id, taxa_brl_por_unidade, cotacao_vigente_em,
    cotacao_valida_ate, valor_pagamento, liquidada_em
) VALUES (
    '00000000-0000-0000-0000-000000000055',
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000033',
    'DUPLICATA_MERCANTIL',
    1000.00,
    1,
    1,
    '00000000-0000-0000-0000-000000000040',
    0.0100000000,
    '2026-01-01T00:00:00Z',
    '00000000-0000-0000-0000-000000000041',
    0.0150000000,
    '2026-01-01T00:00:00Z',
    900.00,
    100.00,
    'USD',
    '00000000-0000-0000-0000-000000000042',
    5.4321000000,
    '2026-01-02T00:00:00Z',
    '2026-01-02T00:30:00Z',
    165.68,
    '2026-01-02T00:15:00Z'
);

SELECT pg_temp.assert_rejected(
    $$INSERT INTO liquidacoes (
          id, organizacao_id, recebivel_id, modalidade, valor_face_brl, prazo_meses,
          versao_recebivel, taxa_base_id, taxa_base_mensal, taxa_base_vigente_em,
          spread_id, spread_mensal, spread_vigente_em, valor_presente_brl, desagio_brl,
          moeda_pagamento, valor_pagamento, liquidada_em
      ) VALUES (
          '00000000-0000-0000-0000-000000000052',
          '00000000-0000-0000-0000-000000000001',
          '00000000-0000-0000-0000-000000000030',
          'DUPLICATA_MERCANTIL', 100000.00, 3, 2,
          '00000000-0000-0000-0000-000000000040', 0.0100000000, '2026-01-01T00:00:00Z',
          '00000000-0000-0000-0000-000000000041', 0.0150000000, '2026-01-01T00:00:00Z',
          92859.94, 7140.06, 'BRL', 92859.94, '2026-01-02T00:00:00Z'
      )$$,
    '23505'
);

INSERT INTO idempotencia (
    id, organizacao_id, tipo_operacao, chave, hash_requisicao, situacao,
    liquidacao_id, resposta_http, resposta_corpo, concluido_em
) VALUES (
    '00000000-0000-0000-0000-000000000061',
    '00000000-0000-0000-0000-000000000001',
    'LIQUIDAR_RECEBIVEL',
    'chave-teste',
    decode(repeat('ab', 32), 'hex'),
    'CONCLUIDA',
    '00000000-0000-0000-0000-000000000050',
    201,
    '{"valor":"92859.94","moeda":"BRL"}',
    CURRENT_TIMESTAMP
);

DO $$
DECLARE
    rejected BOOLEAN := FALSE;
BEGIN
    BEGIN
        INSERT INTO idempotencia (
            id, organizacao_id, tipo_operacao, chave, hash_requisicao
        ) VALUES (
            '00000000-0000-0000-0000-000000000063',
            '00000000-0000-0000-0000-000000000001',
            'LIQUIDAR_RECEBIVEL',
            'chave-incompleta',
            decode(repeat('ef', 32), 'hex')
        );
        SET CONSTRAINTS idempotencia_concluida_ctrg IMMEDIATE;
    EXCEPTION WHEN check_violation THEN
        rejected := TRUE;
    END;
    IF NOT rejected THEN
        RAISE EXCEPTION 'A reserva incompleta de idempotencia foi aceita no commit';
    END IF;
END;
$$;

SELECT pg_temp.assert_rejected(
    $$INSERT INTO idempotencia (
          id, organizacao_id, tipo_operacao, chave, hash_requisicao, situacao,
          liquidacao_id, resposta_http, resposta_corpo, concluido_em
      ) VALUES (
          '00000000-0000-0000-0000-000000000060',
          '00000000-0000-0000-0000-000000000001',
          'LIQUIDAR_RECEBIVEL', 'chave-teste', decode(repeat('ab', 32), 'hex'),
          'CONCLUIDA', '00000000-0000-0000-0000-000000000050', 201,
          '{"valor":"92859.94","moeda":"BRL"}', CURRENT_TIMESTAMP
      )$$,
    '23505'
);

SELECT pg_temp.assert_rejected(
    $$INSERT INTO idempotencia (
          id, organizacao_id, tipo_operacao, chave, hash_requisicao, situacao,
          liquidacao_id, resposta_http, resposta_corpo, concluido_em
      ) VALUES (
          '00000000-0000-0000-0000-000000000062',
          '00000000-0000-0000-0000-000000000002',
          'LIQUIDAR_RECEBIVEL', 'chave-outro-tenant', decode(repeat('cd', 32), 'hex'),
          'CONCLUIDA', '00000000-0000-0000-0000-000000000050', 201,
          '{"valor":"92859.94","moeda":"BRL"}', CURRENT_TIMESTAMP
      )$$,
    '23503'
);

INSERT INTO auditoria_liquidacoes (
    id, organizacao_id, liquidacao_id, usuario_id, evento, detalhes
) VALUES (
    '00000000-0000-0000-0000-000000000070',
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000050',
    '00000000-0000-0000-0000-000000000010',
    'LIQUIDADA',
    '{"origem":"teste"}'
);

SELECT pg_temp.assert_rejected(
    $$UPDATE liquidacoes SET valor_pagamento = 1.00
      WHERE id = '00000000-0000-0000-0000-000000000050'$$,
    '55000'
);

SELECT pg_temp.assert_rejected(
    $$DELETE FROM auditoria_liquidacoes
      WHERE id = '00000000-0000-0000-0000-000000000070'$$,
    '55000'
);

SELECT pg_temp.assert_rejected(
    $$UPDATE idempotencia SET resposta_corpo = '{}'::jsonb
      WHERE id = '00000000-0000-0000-0000-000000000061'$$,
    '55000'
);

SELECT pg_temp.assert_rejected(
    $$TRUNCATE TABLE auditoria_liquidacoes$$,
    '55000'
);

SELECT pg_temp.assert_rejected(
    $$UPDATE cotacoes_cambiais SET taxa_brl_por_unidade = 0
      WHERE id = '00000000-0000-0000-0000-000000000042'$$,
    '55000'
);

SELECT 'Todas as verificacoes de integridade PostgreSQL passaram.' AS resultado;
