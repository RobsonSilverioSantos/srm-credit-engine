package br.com.srm.creditengine.taxas

import java.time.Duration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional

@EnabledIfEnvironmentVariable(named = "SRM_TEST_POSTGRES", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class BootstrapTaxasDemoPostgresTest {
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var gerenciador: PlatformTransactionManager

    @Test
    @Transactional
    fun `carga inicial de demo cria golden cases e e idempotente`() {
        prepararTabelasTemporarias()
        executarBootstrap()
        executarBootstrap()

        assertThat(jdbc.queryForObject("SELECT count(*) FROM taxas_base", Int::class.java)).isEqualTo(1)
        assertThat(jdbc.queryForObject("SELECT taxa_mensal FROM taxas_base", java.math.BigDecimal::class.java))
            .isEqualByComparingTo("0.01")
        assertThat(jdbc.queryForObject("SELECT count(*) FROM spreads_recebiveis", Int::class.java)).isEqualTo(2)
        assertThat(
            jdbc.queryForObject(
                "SELECT spread_mensal FROM spreads_recebiveis WHERE modalidade = 'DUPLICATA_MERCANTIL'",
                java.math.BigDecimal::class.java,
            ),
        ).isEqualByComparingTo("0.015")
        assertThat(
            jdbc.queryForObject(
                "SELECT spread_mensal FROM spreads_recebiveis WHERE modalidade = 'CHEQUE_PRE_DATADO'",
                java.math.BigDecimal::class.java,
            ),
        ).isEqualByComparingTo("0.025")
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cotacoes_cambiais", Int::class.java)).isEqualTo(1)
        assertThat(
            jdbc.queryForObject("SELECT taxa_brl_por_unidade FROM cotacoes_cambiais", java.math.BigDecimal::class.java),
        ).isEqualByComparingTo("5.4321")
    }

    @Test
    @Transactional
    fun `nao altera taxas existentes e adiciona somente cotacao se a anterior venceu`() {
        prepararTabelasTemporarias()
        jdbc.update(
            "INSERT INTO taxas_base (id, taxa_mensal, vigente_em) VALUES (gen_random_uuid(), 0.02, CURRENT_TIMESTAMP - INTERVAL '1 hour')",
        )
        jdbc.update(
            """
            INSERT INTO spreads_recebiveis (id, modalidade, spread_mensal, vigente_em)
            VALUES (gen_random_uuid(), 'CHEQUE_PRE_DATADO', 0.03, CURRENT_TIMESTAMP - INTERVAL '1 hour')
            """.trimIndent(),
        )
        jdbc.update(
            """
            INSERT INTO cotacoes_cambiais (id, taxa_brl_por_unidade, vigente_em)
            VALUES (gen_random_uuid(), 6.0000, CURRENT_TIMESTAMP - INTERVAL '31 minutes')
            """.trimIndent(),
        )

        executarBootstrap()
        executarBootstrap()

        assertThat(jdbc.queryForObject("SELECT count(*) FROM taxas_base", Int::class.java)).isEqualTo(1)
        assertThat(jdbc.queryForObject("SELECT taxa_mensal FROM taxas_base", java.math.BigDecimal::class.java))
            .isEqualByComparingTo("0.02")
        assertThat(jdbc.queryForObject("SELECT count(*) FROM spreads_recebiveis", Int::class.java)).isEqualTo(2)
        assertThat(
            jdbc.queryForObject(
                "SELECT spread_mensal FROM spreads_recebiveis WHERE modalidade = 'CHEQUE_PRE_DATADO'",
                java.math.BigDecimal::class.java,
            ),
        ).isEqualByComparingTo("0.03")
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cotacoes_cambiais", Int::class.java)).isEqualTo(2)
        assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM cotacoes_cambiais WHERE taxa_brl_por_unidade = 6.0000",
                Int::class.java,
            ),
        ).isEqualTo(1)
        assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM cotacoes_cambiais WHERE taxa_brl_por_unidade = 5.4321",
                Int::class.java,
            ),
        ).isEqualTo(1)
    }

    private fun executarBootstrap() {
        BootstrapTaxasDemo().bootstrapTaxas(jdbc, gerenciador, Duration.ofMinutes(30))
            .run(DefaultApplicationArguments(emptyArray<String>()))
    }

    private fun prepararTabelasTemporarias() {
        jdbc.execute(
            """
            CREATE TEMP TABLE taxas_base (
                id UUID PRIMARY KEY,
                taxa_mensal NUMERIC(12,10) NOT NULL,
                vigente_em TIMESTAMPTZ NOT NULL UNIQUE,
                criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
            ) ON COMMIT DROP
            """.trimIndent(),
        )
        jdbc.execute(
            """
            CREATE TEMP TABLE spreads_recebiveis (
                id UUID PRIMARY KEY,
                modalidade VARCHAR(32) NOT NULL,
                spread_mensal NUMERIC(12,10) NOT NULL,
                vigente_em TIMESTAMPTZ NOT NULL,
                criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                UNIQUE(modalidade, vigente_em)
            ) ON COMMIT DROP
            """.trimIndent(),
        )
        jdbc.execute(
            """
            CREATE TEMP TABLE cotacoes_cambiais (
                id UUID PRIMARY KEY,
                moeda_base CHAR(3) NOT NULL DEFAULT 'BRL',
                moeda_cotada CHAR(3) NOT NULL DEFAULT 'USD',
                taxa_brl_por_unidade NUMERIC(20,10) NOT NULL,
                vigente_em TIMESTAMPTZ NOT NULL,
                criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                UNIQUE(moeda_base, moeda_cotada, vigente_em)
            ) ON COMMIT DROP
            """.trimIndent(),
        )
    }
}
