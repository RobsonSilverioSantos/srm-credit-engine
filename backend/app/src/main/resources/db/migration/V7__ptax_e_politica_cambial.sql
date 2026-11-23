-- Legados não têm proveniência comprovada. Novas fixtures são DEMO explícitas.
ALTER TABLE cotacoes_cambiais
    ADD COLUMN fonte VARCHAR(16) NOT NULL DEFAULT 'LEGADO',
    ADD COLUMN versao INTEGER NOT NULL DEFAULT 1 CHECK (versao > 0),
    ADD COLUMN data_boletim DATE,
    ADD COLUMN horario_original VARCHAR(40),
    ADD COLUMN fuso VARCHAR(40),
    ADD COLUMN tipo_boletim VARCHAR(32),
    ADD COLUMN lado VARCHAR(8) NOT NULL DEFAULT 'VENDA' CHECK (lado = 'VENDA'),
    ADD COLUMN identidade_publicacao VARCHAR(64),
    ADD COLUMN cotacao_compra NUMERIC(20, 10),
    ADD COLUMN organizacao_id UUID REFERENCES organizacoes(id),
    ADD COLUMN autor_id UUID,
    ADD COLUMN justificativa VARCHAR(1000),
    ADD CONSTRAINT cotacoes_autor_fk FOREIGN KEY (autor_id, organizacao_id) REFERENCES usuarios(id, organizacao_id),
    ADD CONSTRAINT cotacoes_fonte_ck CHECK (fonte IN ('LEGADO', 'DEMO', 'BCB_PTAX', 'MANUAL')),
    ADD CONSTRAINT cotacoes_proveniencia_ck CHECK (
        (fonte IN ('LEGADO', 'DEMO') AND organizacao_id IS NULL AND autor_id IS NULL)
        OR (fonte = 'BCB_PTAX' AND organizacao_id IS NULL AND autor_id IS NULL
            AND data_boletim IS NOT NULL AND horario_original IS NOT NULL AND fuso = 'America/Sao_Paulo'
            AND tipo_boletim = 'FECHAMENTO' AND identidade_publicacao IS NOT NULL AND criado_em >= vigente_em
            AND cotacao_compra > 0 AND cotacao_compra <= taxa_brl_por_unidade)
        OR (fonte = 'MANUAL' AND organizacao_id IS NOT NULL AND autor_id IS NOT NULL
            AND justificativa IS NOT NULL AND length(btrim(justificativa)) BETWEEN 1 AND 1000
            AND vigente_em = criado_em)
    ),
    ADD CONSTRAINT cotacoes_id_org_uk UNIQUE (id, organizacao_id);

ALTER TABLE cotacoes_cambiais ALTER COLUMN fonte SET DEFAULT 'DEMO';
ALTER TABLE cotacoes_cambiais DROP CONSTRAINT cotacoes_moeda_vigencia_uk;
CREATE UNIQUE INDEX cotacoes_demo_vigencia_uk
    ON cotacoes_cambiais (moeda_base, moeda_cotada, vigente_em) WHERE fonte = 'DEMO';
CREATE UNIQUE INDEX cotacoes_ptax_versao_uk
    ON cotacoes_cambiais (data_boletim, tipo_boletim, lado, moeda_base, moeda_cotada, versao) WHERE fonte = 'BCB_PTAX';
CREATE UNIQUE INDEX cotacoes_ptax_publicacao_uk
    ON cotacoes_cambiais (identidade_publicacao) WHERE fonte = 'BCB_PTAX';
CREATE UNIQUE INDEX cotacoes_manual_vigencia_uk
    ON cotacoes_cambiais (organizacao_id, vigente_em) WHERE fonte = 'MANUAL';
CREATE INDEX cotacoes_fonte_corte_idx ON cotacoes_cambiais (fonte, vigente_em DESC, criado_em DESC);

CREATE TABLE politicas_cambiais (
    id UUID PRIMARY KEY,
    versao VARCHAR(80) NOT NULL UNIQUE,
    ptax_max_idade_segundos BIGINT NOT NULL CHECK (ptax_max_idade_segundos > 0),
    vigente_em TIMESTAMPTZ NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
INSERT INTO politicas_cambiais (id, versao, ptax_max_idade_segundos, vigente_em, criado_em)
VALUES ('00000000-0000-4000-8000-000000000026', 'ADR-026-v1', 345600, '-infinity', '-infinity');

CREATE TABLE selecoes_cambiais (
    id UUID PRIMARY KEY,
    revisao BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
    organizacao_id UUID NOT NULL REFERENCES organizacoes(id),
    fonte VARCHAR(16) NOT NULL CHECK (fonte IN ('BCB_PTAX', 'MANUAL')),
    cotacao_manual_id UUID,
    autor_id UUID NOT NULL,
    justificativa VARCHAR(1000) NOT NULL CHECK (length(btrim(justificativa)) BETWEEN 1 AND 1000),
    politica_id UUID NOT NULL REFERENCES politicas_cambiais(id),
    autorizado_em TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    valida_ate TIMESTAMPTZ,
    FOREIGN KEY (autor_id, organizacao_id) REFERENCES usuarios(id, organizacao_id),
    FOREIGN KEY (cotacao_manual_id, organizacao_id) REFERENCES cotacoes_cambiais(id, organizacao_id),
    CHECK ((fonte = 'MANUAL' AND cotacao_manual_id IS NOT NULL AND valida_ate > autorizado_em)
        OR (fonte = 'BCB_PTAX' AND cotacao_manual_id IS NULL AND valida_ate IS NULL))
);
CREATE INDEX selecoes_cambiais_corte_idx ON selecoes_cambiais (organizacao_id, autorizado_em DESC, revisao DESC);
CREATE UNIQUE INDEX selecoes_manual_unica_uk ON selecoes_cambiais (cotacao_manual_id) WHERE fonte = 'MANUAL';

CREATE TRIGGER politicas_cambiais_imutaveis BEFORE UPDATE OR DELETE ON politicas_cambiais
    FOR EACH ROW EXECUTE FUNCTION impedir_mutacao_historico_financeiro();
CREATE TRIGGER politicas_cambiais_sem_truncate BEFORE TRUNCATE ON politicas_cambiais
    FOR EACH STATEMENT EXECUTE FUNCTION impedir_mutacao_historico_financeiro();
CREATE TRIGGER selecoes_cambiais_imutaveis BEFORE UPDATE OR DELETE ON selecoes_cambiais
    FOR EACH ROW EXECUTE FUNCTION impedir_mutacao_historico_financeiro();
CREATE TRIGGER selecoes_cambiais_sem_truncate BEFORE TRUNCATE ON selecoes_cambiais
    FOR EACH STATEMENT EXECUTE FUNCTION impedir_mutacao_historico_financeiro();

ALTER TABLE liquidacoes
    ADD COLUMN cambio_snapshot JSONB,
    ADD COLUMN politica_cambial_id UUID REFERENCES politicas_cambiais(id),
    ADD COLUMN selecao_cambial_id UUID REFERENCES selecoes_cambiais(id);
