-- Histórico permanece sem país/tipo/canônico. Nunca inferir identidade pelo formato.
-- Lock apenas de DDL na migração; nenhuma alteração nas transações financeiras.
LOCK TABLE cedentes IN ACCESS EXCLUSIVE MODE;

CREATE FUNCTION srm_chave_documento(numero TEXT) RETURNS TEXT
LANGUAGE SQL IMMUTABLE STRICT PARALLEL SAFE AS $$
    SELECT regexp_replace(
        translate(numero, 'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ') COLLATE "C",
        '[^A-Z0-9]', '', 'g'
    )
$$;

DO $$
DECLARE grupos BIGINT;
BEGIN
    SELECT count(*) INTO grupos FROM (
        SELECT organizacao_id, srm_chave_documento(documento)
        FROM cedentes
        GROUP BY organizacao_id, srm_chave_documento(documento)
        HAVING count(*) > 1
    ) colisoes;
    IF grupos > 0 THEN
        RAISE EXCEPTION USING
            ERRCODE = '23505',
            MESSAGE = format('B14: %s grupo(s) de colisao normalizada no legado; reconciliacao manual obrigatoria.', grupos),
            HINT = 'Consultar IDs em sessao restrita. Nenhum documento ou tenant foi reclassificado; nao excluir nem fundir automaticamente.';
    END IF;
END
$$;

CREATE TABLE paises_documento (
    codigo VARCHAR(2) PRIMARY KEY CHECK (codigo COLLATE "C" ~ '^[A-Z]{2}$')
);
-- ISO 3166-1 alpha-2 atribuídos, mesma lista da plataforma Java 21 (sem XK/ZZ).
INSERT INTO paises_documento (codigo)
SELECT unnest(string_to_array(
    'AD AE AF AG AI AL AM AO AQ AR AS AT AU AW AX AZ BA BB BD BE BF BG BH BI BJ BL BM BN BO BQ BR BS BT BV BW BY BZ CA CC CD CF CG CH CI CK CL CM CN CO CR CU CV CW CX CY CZ DE DJ DK DM DO DZ EC EE EG EH ER ES ET FI FJ FK FM FO FR GA GB GD GE GF GG GH GI GL GM GN GP GQ GR GS GT GU GW GY HK HM HN HR HT HU ID IE IL IM IN IO IQ IR IS IT JE JM JO JP KE KG KH KI KM KN KP KR KW KY KZ LA LB LC LI LK LR LS LT LU LV LY MA MC MD ME MF MG MH MK ML MM MN MO MP MQ MR MS MT MU MV MW MX MY MZ NA NC NE NF NG NI NL NO NP NR NU NZ OM PA PE PF PG PH PK PL PM PN PR PS PT PW PY QA RE RO RS RU RW SA SB SC SD SE SG SH SI SJ SK SL SM SN SO SR SS ST SV SX SY SZ TC TD TF TG TH TJ TK TL TM TN TO TR TT TV TW TZ UA UG UM US UY UZ VA VC VE VG VI VN VU WF WS YE YT ZA ZM ZW',
    ' '
));

CREATE FUNCTION srm_cnpj_valido(numero TEXT) RETURNS BOOLEAN
LANGUAGE plpgsql IMMUTABLE STRICT PARALLEL SAFE AS $$
DECLARE soma INTEGER; resto INTEGER; peso INTEGER; dv INTEGER; etapa INTEGER; pos INTEGER;
BEGIN
    IF numero COLLATE "C" !~ '^[A-Z0-9]{12}[0-9]{2}$'
       OR numero = repeat(substr(numero, 1, 1), 14) THEN
        RETURN FALSE;
    END IF;
    FOR etapa IN 1..2 LOOP
        soma := 0; peso := 2;
        FOR pos IN REVERSE (11 + etapa)..1 LOOP
            soma := soma + (ascii(substr(numero, pos, 1)) - 48) * peso;
            peso := CASE WHEN peso = 9 THEN 2 ELSE peso + 1 END;
        END LOOP;
        resto := soma % 11;
        dv := CASE WHEN resto < 2 THEN 0 ELSE 11 - resto END;
        IF ascii(substr(numero, 12 + etapa, 1)) - 48 <> dv THEN RETURN FALSE; END IF;
    END LOOP;
    RETURN TRUE;
END
$$;

ALTER TABLE cedentes
    ADD COLUMN pais VARCHAR(2) REFERENCES paises_documento(codigo),
    ADD COLUMN tipo_documento VARCHAR(16),
    ADD COLUMN documento_canonico VARCHAR(32),
    ADD CONSTRAINT cedentes_identificacao_ck CHECK (
        (pais IS NULL AND tipo_documento IS NULL AND documento_canonico IS NULL)
        OR
        (pais IS NOT NULL AND tipo_documento IS NOT NULL AND documento_canonico IS NOT NULL
         AND documento = documento_canonico
         AND ((pais = 'BR' AND tipo_documento = 'CNPJ' AND srm_cnpj_valido(documento_canonico))
              OR (pais <> 'BR' AND tipo_documento = 'OUTRO' AND documento_canonico COLLATE "C" ~ '^[A-Z0-9]{1,32}$')))
    ),
    DROP CONSTRAINT cedentes_documento_organizacao_uk,
    ADD CONSTRAINT cedentes_identidade_organizacao_uk
        UNIQUE (organizacao_id, pais, tipo_documento, documento_canonico);

-- Reserva conservadora, NÃO identidade fiscal. Compartilhada por identidades conhecidas
-- distintas; exclusiva quando ligada a legado. UPSERT serializa escritores por chave
-- mesmo em conexões/transações concorrentes, sem SELECT-then-INSERT vulnerável a corrida.
CREATE TABLE cedentes_documentos_reservas (
    organizacao_id UUID NOT NULL REFERENCES organizacoes(id),
    chave TEXT NOT NULL,
    legado_id UUID,
    PRIMARY KEY (organizacao_id, chave)
);
INSERT INTO cedentes_documentos_reservas (organizacao_id, chave, legado_id)
SELECT organizacao_id, srm_chave_documento(documento), id FROM cedentes;

CREATE FUNCTION srm_reservar_documento_cedente() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE legado UUID;
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF ROW(NEW.organizacao_id, NEW.documento, NEW.pais, NEW.tipo_documento, NEW.documento_canonico)
           IS DISTINCT FROM
           ROW(OLD.organizacao_id, OLD.documento, OLD.pais, OLD.tipo_documento, OLD.documento_canonico) THEN
            RAISE EXCEPTION USING ERRCODE = '23514',
                MESSAGE = 'B14: identidade de cedente imutavel; reconciliacao manual obrigatoria.';
        END IF;
        RETURN NEW;
    END IF;
    INSERT INTO cedentes_documentos_reservas AS reserva (organizacao_id, chave, legado_id)
    VALUES (NEW.organizacao_id, srm_chave_documento(NEW.documento),
            CASE WHEN NEW.pais IS NULL THEN NEW.id ELSE NULL END)
    ON CONFLICT (organizacao_id, chave) DO UPDATE SET legado_id = reserva.legado_id
    RETURNING legado_id INTO legado;

    IF (NEW.pais IS NOT NULL AND legado IS NOT NULL)
       OR (NEW.pais IS NULL AND legado IS DISTINCT FROM NEW.id) THEN
        RAISE EXCEPTION USING ERRCODE = '23505',
            MESSAGE = 'B14: possivel coincidencia com legado; reconciliacao manual obrigatoria.';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER cedentes_reservar_documento
BEFORE INSERT OR UPDATE ON cedentes
FOR EACH ROW EXECUTE FUNCTION srm_reservar_documento_cedente();

-- Reservas não são apagadas automaticamente: deletar um cedente não resolve a ambiguidade.
-- Identidades novas com mesmo número em países/tipos diferentes continuam permitidas.
