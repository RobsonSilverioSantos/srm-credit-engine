ALTER TABLE usuarios
    ADD COLUMN perfil VARCHAR(16) NOT NULL DEFAULT 'OPERADOR';

ALTER TABLE usuarios
    ADD CONSTRAINT usuarios_perfil_ck
        CHECK (perfil IN ('MASTER', 'ADMIN', 'OPERADOR'));

CREATE UNIQUE INDEX usuarios_master_organizacao_uk
    ON usuarios (organizacao_id)
    WHERE perfil = 'MASTER';
