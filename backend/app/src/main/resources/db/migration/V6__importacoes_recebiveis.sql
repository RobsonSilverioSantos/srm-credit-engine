ALTER TABLE recebiveis ADD CONSTRAINT recebiveis_id_organizacao_uk UNIQUE (id, organizacao_id);

CREATE TABLE importacoes_recebiveis (
    id UUID PRIMARY KEY,
    organizacao_id UUID NOT NULL,
    usuario_id UUID NOT NULL,
    chave VARCHAR(128) NOT NULL,
    hash VARCHAR(64) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (id, organizacao_id),
    UNIQUE (organizacao_id, chave),
    FOREIGN KEY (usuario_id, organizacao_id) REFERENCES usuarios (id, organizacao_id),
    CHECK (hash ~ '^[0-9a-f]{64}$')
);

CREATE TABLE importacoes_recebiveis_itens (
    lote_id UUID NOT NULL,
    organizacao_id UUID NOT NULL,
    referencia_externa VARCHAR(128) NOT NULL,
    recebivel_id UUID NOT NULL UNIQUE,
    posicao INTEGER NOT NULL CHECK (posicao >= 0),
    moeda_pagamento_indicativa VARCHAR(3) NOT NULL CHECK (moeda_pagamento_indicativa IN ('BRL', 'USD')),
    PRIMARY KEY (organizacao_id, referencia_externa),
    UNIQUE (lote_id, posicao),
    FOREIGN KEY (lote_id, organizacao_id) REFERENCES importacoes_recebiveis (id, organizacao_id),
    FOREIGN KEY (recebivel_id, organizacao_id) REFERENCES recebiveis (id, organizacao_id)
);
