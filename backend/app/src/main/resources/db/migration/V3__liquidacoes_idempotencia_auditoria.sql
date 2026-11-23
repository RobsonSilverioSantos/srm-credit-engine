CREATE TABLE liquidacoes (
    id UUID PRIMARY KEY,
    organizacao_id UUID NOT NULL,
    recebivel_id UUID NOT NULL,
    modalidade VARCHAR(32) NOT NULL,
    valor_face_brl NUMERIC(19, 2) NOT NULL,
    prazo_meses INTEGER NOT NULL,
    versao_recebivel BIGINT NOT NULL,
    taxa_base_id UUID NOT NULL,
    taxa_base_mensal NUMERIC(12, 10) NOT NULL,
    taxa_base_vigente_em TIMESTAMPTZ NOT NULL,
    spread_id UUID NOT NULL,
    spread_mensal NUMERIC(12, 10) NOT NULL,
    spread_vigente_em TIMESTAMPTZ NOT NULL,
    valor_presente_brl NUMERIC(19, 2) NOT NULL,
    desagio_brl NUMERIC(19, 2) NOT NULL,
    moeda_pagamento CHAR(3) NOT NULL,
    cotacao_cambial_id UUID,
    taxa_brl_por_unidade NUMERIC(20, 10),
    cotacao_vigente_em TIMESTAMPTZ,
    cotacao_valida_ate TIMESTAMPTZ,
    valor_pagamento NUMERIC(19, 2) NOT NULL,
    liquidada_em TIMESTAMPTZ NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT liquidacoes_recebivel_organizacao_fk
        FOREIGN KEY (recebivel_id, organizacao_id, modalidade, valor_face_brl, prazo_meses)
        REFERENCES recebiveis (id, organizacao_id, modalidade, valor_face_brl, prazo_meses),
    CONSTRAINT liquidacoes_taxa_base_fk
        FOREIGN KEY (taxa_base_id, taxa_base_mensal, taxa_base_vigente_em)
        REFERENCES taxas_base (id, taxa_mensal, vigente_em),
    CONSTRAINT liquidacoes_spread_fk
        FOREIGN KEY (spread_id, modalidade, spread_mensal, spread_vigente_em)
        REFERENCES spreads_recebiveis (id, modalidade, spread_mensal, vigente_em),
    CONSTRAINT liquidacoes_cotacao_fk
        FOREIGN KEY (cotacao_cambial_id, taxa_brl_por_unidade, cotacao_vigente_em)
        REFERENCES cotacoes_cambiais (id, taxa_brl_por_unidade, vigente_em),
    CONSTRAINT liquidacoes_modalidade_ck
        CHECK (modalidade IN ('DUPLICATA_MERCANTIL', 'CHEQUE_PRE_DATADO')),
    CONSTRAINT liquidacoes_valor_face_positivo_ck CHECK (valor_face_brl > 0),
    CONSTRAINT liquidacoes_prazo_positivo_ck CHECK (prazo_meses > 0),
    CONSTRAINT liquidacoes_versao_recebivel_positiva_ck CHECK (versao_recebivel > 0),
    CONSTRAINT liquidacoes_taxa_base_nao_negativa_ck CHECK (taxa_base_mensal >= 0),
    CONSTRAINT liquidacoes_spread_nao_negativo_ck CHECK (spread_mensal >= 0),
    CONSTRAINT liquidacoes_valores_brl_ck
        CHECK (
            valor_presente_brl > 0
            AND desagio_brl >= 0
            AND valor_presente_brl + desagio_brl = valor_face_brl
        ),
    CONSTRAINT liquidacoes_taxas_vigentes_ck
        CHECK (taxa_base_vigente_em <= liquidada_em AND spread_vigente_em <= liquidada_em),
    CONSTRAINT liquidacoes_pagamento_ck
        CHECK (
            valor_pagamento > 0
            AND (
                (
                    moeda_pagamento = 'BRL'
                    AND cotacao_cambial_id IS NULL
                    AND taxa_brl_por_unidade IS NULL
                    AND cotacao_vigente_em IS NULL
                    AND cotacao_valida_ate IS NULL
                    AND valor_pagamento = valor_presente_brl
                )
                OR (
                    moeda_pagamento = 'USD'
                    AND cotacao_cambial_id IS NOT NULL
                    AND taxa_brl_por_unidade IS NOT NULL
                    AND taxa_brl_por_unidade > 0
                    AND cotacao_vigente_em IS NOT NULL
                    AND cotacao_vigente_em <= liquidada_em
                    AND cotacao_valida_ate IS NOT NULL
                    AND cotacao_valida_ate > cotacao_vigente_em
                    AND liquidada_em <= cotacao_valida_ate
                )
            )
        ),
    CONSTRAINT liquidacoes_id_organizacao_uk UNIQUE (id, organizacao_id),
    CONSTRAINT liquidacoes_recebivel_unico_uk UNIQUE (organizacao_id, recebivel_id)
);

CREATE INDEX liquidacoes_extrato_periodo_idx
    ON liquidacoes (organizacao_id, liquidada_em DESC, id DESC);

CREATE INDEX liquidacoes_extrato_moeda_periodo_idx
    ON liquidacoes (organizacao_id, moeda_pagamento, liquidada_em DESC, id DESC);

CREATE TABLE idempotencia (
    id UUID PRIMARY KEY,
    organizacao_id UUID NOT NULL,
    tipo_operacao VARCHAR(80) NOT NULL,
    chave VARCHAR(255) NOT NULL,
    hash_requisicao BYTEA NOT NULL,
    situacao VARCHAR(16) NOT NULL DEFAULT 'EM_ANDAMENTO',
    liquidacao_id UUID,
    resposta_http SMALLINT,
    resposta_corpo JSONB,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    concluido_em TIMESTAMPTZ,
    CONSTRAINT idempotencia_organizacao_fk
        FOREIGN KEY (organizacao_id) REFERENCES organizacoes (id),
    CONSTRAINT idempotencia_liquidacao_organizacao_fk
        FOREIGN KEY (liquidacao_id, organizacao_id)
        REFERENCES liquidacoes (id, organizacao_id),
    CONSTRAINT idempotencia_tipo_nao_vazio_ck CHECK (btrim(tipo_operacao) <> ''),
    CONSTRAINT idempotencia_chave_nao_vazia_ck CHECK (btrim(chave) <> ''),
    CONSTRAINT idempotencia_hash_sha256_ck CHECK (octet_length(hash_requisicao) = 32),
    CONSTRAINT idempotencia_situacao_ck CHECK (situacao IN ('EM_ANDAMENTO', 'CONCLUIDA')),
    CONSTRAINT idempotencia_resposta_ck
        CHECK (
            (
                situacao = 'EM_ANDAMENTO'
                AND liquidacao_id IS NULL
                AND resposta_http IS NULL
                AND resposta_corpo IS NULL
                AND concluido_em IS NULL
            )
            OR (
                situacao = 'CONCLUIDA'
                AND resposta_http IS NOT NULL
                AND resposta_http BETWEEN 100 AND 599
                AND resposta_corpo IS NOT NULL
                AND concluido_em IS NOT NULL
                AND concluido_em >= criado_em
            )
        ),
    CONSTRAINT idempotencia_liquidacao_tipo_ck
        CHECK (
            liquidacao_id IS NULL
            OR (tipo_operacao = 'LIQUIDAR_RECEBIVEL' AND situacao = 'CONCLUIDA')
        ),
    CONSTRAINT idempotencia_liquidacao_concluida_ck
        CHECK (
            tipo_operacao <> 'LIQUIDAR_RECEBIVEL'
            OR situacao <> 'CONCLUIDA'
            OR liquidacao_id IS NOT NULL
        ),
    CONSTRAINT idempotencia_escopo_chave_uk
        UNIQUE (organizacao_id, tipo_operacao, chave),
    CONSTRAINT idempotencia_liquidacao_unica_uk
        UNIQUE (organizacao_id, liquidacao_id)
);

CREATE FUNCTION exigir_idempotencia_concluida() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM idempotencia
        WHERE id = NEW.id
          AND situacao <> 'CONCLUIDA'
    ) THEN
        RAISE EXCEPTION 'A reserva de idempotencia deve ser concluida na mesma transacao'
            USING ERRCODE = '23514';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER idempotencia_concluida_ctrg
    AFTER INSERT OR UPDATE ON idempotencia
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION exigir_idempotencia_concluida();

CREATE FUNCTION impedir_mutacao_idempotencia_concluida() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'TRUNCATE' OR TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Registros idempotentes concluidos sao somente de inclusao'
            USING ERRCODE = '55000';
    END IF;
    IF OLD.situacao = 'CONCLUIDA' THEN
        RAISE EXCEPTION 'Registros idempotentes concluidos sao somente de inclusao'
            USING ERRCODE = '55000';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER idempotencia_concluida_somente_inclusao_trg
    BEFORE UPDATE OR DELETE ON idempotencia
    FOR EACH ROW EXECUTE FUNCTION impedir_mutacao_idempotencia_concluida();

CREATE TRIGGER idempotencia_sem_truncate_trg
    BEFORE TRUNCATE ON idempotencia
    FOR EACH STATEMENT EXECUTE FUNCTION impedir_mutacao_idempotencia_concluida();

CREATE TABLE auditoria_liquidacoes (
    id UUID PRIMARY KEY,
    organizacao_id UUID NOT NULL,
    liquidacao_id UUID NOT NULL,
    usuario_id UUID,
    evento VARCHAR(40) NOT NULL,
    detalhes JSONB NOT NULL,
    registrado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT auditoria_liquidacoes_alvo_organizacao_fk
        FOREIGN KEY (liquidacao_id, organizacao_id)
        REFERENCES liquidacoes (id, organizacao_id),
    CONSTRAINT auditoria_liquidacoes_usuario_organizacao_fk
        FOREIGN KEY (usuario_id, organizacao_id)
        REFERENCES usuarios (id, organizacao_id),
    CONSTRAINT auditoria_liquidacoes_evento_nao_vazio_ck CHECK (btrim(evento) <> '')
);

CREATE INDEX auditoria_liquidacoes_alvo_idx
    ON auditoria_liquidacoes (organizacao_id, liquidacao_id, registrado_em);

CREATE TRIGGER liquidacoes_somente_inclusao_trg
    BEFORE UPDATE OR DELETE ON liquidacoes
    FOR EACH ROW EXECUTE FUNCTION impedir_mutacao_historico_financeiro();

CREATE TRIGGER liquidacoes_sem_truncate_trg
    BEFORE TRUNCATE ON liquidacoes
    FOR EACH STATEMENT EXECUTE FUNCTION impedir_mutacao_historico_financeiro();

CREATE TRIGGER auditoria_liquidacoes_somente_inclusao_trg
    BEFORE UPDATE OR DELETE ON auditoria_liquidacoes
    FOR EACH ROW EXECUTE FUNCTION impedir_mutacao_historico_financeiro();

CREATE TRIGGER auditoria_liquidacoes_sem_truncate_trg
    BEFORE TRUNCATE ON auditoria_liquidacoes
    FOR EACH STATEMENT EXECUTE FUNCTION impedir_mutacao_historico_financeiro();
