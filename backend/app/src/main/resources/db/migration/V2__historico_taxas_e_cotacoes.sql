CREATE TABLE taxas_base (
    id UUID PRIMARY KEY,
    taxa_mensal NUMERIC(12, 10) NOT NULL,
    vigente_em TIMESTAMPTZ NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT taxas_base_taxa_nao_negativa_ck CHECK (taxa_mensal >= 0),
    CONSTRAINT taxas_base_vigencia_uk UNIQUE (vigente_em),
    CONSTRAINT taxas_base_snapshot_uk UNIQUE (id, taxa_mensal, vigente_em)
);

CREATE TABLE spreads_recebiveis (
    id UUID PRIMARY KEY,
    modalidade VARCHAR(32) NOT NULL,
    spread_mensal NUMERIC(12, 10) NOT NULL,
    vigente_em TIMESTAMPTZ NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT spreads_recebiveis_modalidade_ck
        CHECK (modalidade IN ('DUPLICATA_MERCANTIL', 'CHEQUE_PRE_DATADO')),
    CONSTRAINT spreads_recebiveis_taxa_nao_negativa_ck CHECK (spread_mensal >= 0),
    CONSTRAINT spreads_recebiveis_modalidade_vigencia_uk UNIQUE (modalidade, vigente_em),
    CONSTRAINT spreads_recebiveis_snapshot_uk
        UNIQUE (id, modalidade, spread_mensal, vigente_em)
);

CREATE TABLE cotacoes_cambiais (
    id UUID PRIMARY KEY,
    moeda_base CHAR(3) NOT NULL DEFAULT 'BRL',
    moeda_cotada CHAR(3) NOT NULL DEFAULT 'USD',
    taxa_brl_por_unidade NUMERIC(20, 10) NOT NULL,
    vigente_em TIMESTAMPTZ NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT cotacoes_moeda_base_ck CHECK (moeda_base = 'BRL'),
    CONSTRAINT cotacoes_moeda_cotada_ck CHECK (moeda_cotada = 'USD'),
    CONSTRAINT cotacoes_taxa_positiva_ck CHECK (taxa_brl_por_unidade > 0),
    CONSTRAINT cotacoes_moeda_vigencia_uk UNIQUE (moeda_base, moeda_cotada, vigente_em),
    CONSTRAINT cotacoes_snapshot_uk UNIQUE (id, taxa_brl_por_unidade, vigente_em)
);

CREATE FUNCTION impedir_mutacao_historico_financeiro() RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Registros financeiros historicos sao somente de inclusao'
        USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER taxas_base_somente_inclusao_trg
    BEFORE UPDATE OR DELETE ON taxas_base
    FOR EACH ROW EXECUTE FUNCTION impedir_mutacao_historico_financeiro();

CREATE TRIGGER taxas_base_sem_truncate_trg
    BEFORE TRUNCATE ON taxas_base
    FOR EACH STATEMENT EXECUTE FUNCTION impedir_mutacao_historico_financeiro();

CREATE TRIGGER spreads_recebiveis_somente_inclusao_trg
    BEFORE UPDATE OR DELETE ON spreads_recebiveis
    FOR EACH ROW EXECUTE FUNCTION impedir_mutacao_historico_financeiro();

CREATE TRIGGER spreads_recebiveis_sem_truncate_trg
    BEFORE TRUNCATE ON spreads_recebiveis
    FOR EACH STATEMENT EXECUTE FUNCTION impedir_mutacao_historico_financeiro();

CREATE TRIGGER cotacoes_cambiais_somente_inclusao_trg
    BEFORE UPDATE OR DELETE ON cotacoes_cambiais
    FOR EACH ROW EXECUTE FUNCTION impedir_mutacao_historico_financeiro();

CREATE TRIGGER cotacoes_cambiais_sem_truncate_trg
    BEFORE TRUNCATE ON cotacoes_cambiais
    FOR EACH STATEMENT EXECUTE FUNCTION impedir_mutacao_historico_financeiro();
