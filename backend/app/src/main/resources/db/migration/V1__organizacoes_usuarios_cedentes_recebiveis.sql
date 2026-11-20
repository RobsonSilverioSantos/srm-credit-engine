CREATE TABLE organizacoes (
    id UUID PRIMARY KEY,
    nome VARCHAR(160) NOT NULL,
    ativa BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT organizacoes_nome_nao_vazio_ck CHECK (btrim(nome) <> '')
);

CREATE TABLE usuarios (
    id UUID PRIMARY KEY,
    organizacao_id UUID NOT NULL,
    email VARCHAR(254) NOT NULL,
    senha_hash VARCHAR(255) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT usuarios_organizacao_fk
        FOREIGN KEY (organizacao_id) REFERENCES organizacoes (id),
    CONSTRAINT usuarios_email_nao_vazio_ck CHECK (btrim(email) <> ''),
    CONSTRAINT usuarios_senha_hash_nao_vazio_ck CHECK (btrim(senha_hash) <> ''),
    CONSTRAINT usuarios_id_organizacao_uk UNIQUE (id, organizacao_id)
);

CREATE UNIQUE INDEX usuarios_email_normalizado_uk ON usuarios (lower(email));

CREATE TABLE cedentes (
    id UUID PRIMARY KEY,
    organizacao_id UUID NOT NULL,
    nome VARCHAR(160) NOT NULL,
    documento VARCHAR(32) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT cedentes_organizacao_fk
        FOREIGN KEY (organizacao_id) REFERENCES organizacoes (id),
    CONSTRAINT cedentes_nome_nao_vazio_ck CHECK (btrim(nome) <> ''),
    CONSTRAINT cedentes_documento_nao_vazio_ck CHECK (btrim(documento) <> ''),
    CONSTRAINT cedentes_id_organizacao_uk UNIQUE (id, organizacao_id),
    CONSTRAINT cedentes_documento_organizacao_uk UNIQUE (organizacao_id, documento)
);

CREATE TABLE recebiveis (
    id UUID PRIMARY KEY,
    organizacao_id UUID NOT NULL,
    cedente_id UUID NOT NULL,
    modalidade VARCHAR(32) NOT NULL,
    valor_face_brl NUMERIC(19, 2) NOT NULL,
    prazo_meses INTEGER NOT NULL,
    vencimento DATE,
    status VARCHAR(16) NOT NULL DEFAULT 'DISPONIVEL',
    versao BIGINT NOT NULL DEFAULT 0,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT recebiveis_cedente_organizacao_fk
        FOREIGN KEY (cedente_id, organizacao_id)
        REFERENCES cedentes (id, organizacao_id),
    CONSTRAINT recebiveis_modalidade_ck
        CHECK (modalidade IN ('DUPLICATA', 'CHEQUE_PRE_DATADO')),
    CONSTRAINT recebiveis_valor_face_positivo_ck CHECK (valor_face_brl > 0),
    CONSTRAINT recebiveis_prazo_positivo_ck CHECK (prazo_meses > 0),
    CONSTRAINT recebiveis_status_ck CHECK (status IN ('DISPONIVEL', 'LIQUIDADO')),
    CONSTRAINT recebiveis_versao_nao_negativa_ck CHECK (versao >= 0),
    CONSTRAINT recebiveis_snapshot_uk
        UNIQUE (id, organizacao_id, modalidade, valor_face_brl, prazo_meses)
);

CREATE INDEX recebiveis_organizacao_status_vencimento_idx
    ON recebiveis (organizacao_id, status, vencimento, id);

CREATE INDEX recebiveis_organizacao_cedente_idx
    ON recebiveis (organizacao_id, cedente_id, id);
