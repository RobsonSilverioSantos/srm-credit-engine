package br.com.srm.creditengine.taxas

import java.time.Duration
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * Dados fictícios de aferição, exclusivos de demonstração local.
 * Nunca reescreve o histórico imutável de taxas ou cotações.
 */
@Configuration(proxyBeanMethods = false)
@Profile("demo")
@ConditionalOnProperty(prefix = "srm.demo", name = ["bootstrap-taxas"], havingValue = "true")
class BootstrapTaxasDemo {
    @Bean
    fun bootstrapTaxas(
        jdbc: JdbcTemplate,
        gerenciador: PlatformTransactionManager,
        @Value("\${srm.cambio.validade:PT30M}") validadeCambial: Duration,
    ): ApplicationRunner = ApplicationRunner {
        require(!validadeCambial.isNegative && !validadeCambial.isZero) {
            "srm.cambio.validade deve ser positiva"
        }
        val logger = LoggerFactory.getLogger(BootstrapTaxasDemo::class.java)

        // Uma transação e um advisory lock exclusivo apenas para este bootstrap
        // impedem duplicações em partidas concorrentes no mesmo banco de demo.
        TransactionTemplate(gerenciador).executeWithoutResult {
            jdbc.execute("SELECT pg_advisory_xact_lock(20261121, 1)")

            val base = jdbc.update(
                """
                INSERT INTO taxas_base (id, taxa_mensal, vigente_em)
                SELECT gen_random_uuid(), 0.0100000000, CURRENT_TIMESTAMP
                WHERE NOT EXISTS (
                    SELECT 1 FROM taxas_base
                    WHERE vigente_em <= CURRENT_TIMESTAMP AND criado_em <= CURRENT_TIMESTAMP
                )
                ON CONFLICT (vigente_em) DO NOTHING
                """.trimIndent(),
            )
            val duplicata = inserirSpreadSeAusente(jdbc, "DUPLICATA_MERCANTIL", "0.0150000000")
            val cheque = inserirSpreadSeAusente(jdbc, "CHEQUE_PRE_DATADO", "0.0250000000")

            // A cotação C3 é um valor FIXO de teste, não uma taxa de mercado.
            // Só cria uma versão nova quando não há cotação vigente e ainda válida.
            // O registro anterior é preservado e a validade continua sendo a
            // janela configurada (30 min por padrão).
            val limiteMicros = Math.addExact(
                Math.multiplyExact(validadeCambial.seconds, 1_000_000L),
                (validadeCambial.nano / 1_000L).toLong(),
            )
            val cambio = jdbc.update(
                """
                INSERT INTO cotacoes_cambiais (id, taxa_brl_por_unidade, vigente_em, fonte)
                SELECT gen_random_uuid(), 5.4321000000, CURRENT_TIMESTAMP, 'DEMO'
                WHERE NOT EXISTS (
                    SELECT 1 FROM cotacoes_cambiais
                    WHERE fonte = 'DEMO' AND moeda_base = 'BRL' AND moeda_cotada = 'USD'
                      AND vigente_em <= CURRENT_TIMESTAMP AND criado_em <= CURRENT_TIMESTAMP
                      AND vigente_em + (CAST(? AS bigint) * INTERVAL '1 microsecond') >= CURRENT_TIMESTAMP
                )
                ON CONFLICT (moeda_base, moeda_cotada, vigente_em) WHERE fonte = 'DEMO' DO NOTHING
                """.trimIndent(),
                limiteMicros,
            )

            logger.info(
                "Bootstrap de taxas de demonstração: base={}, spreadDuplicata={}, spreadCheque={}, cotacaoC3={}",
                base, duplicata, cheque, cambio,
            )
        }
    }

    private fun inserirSpreadSeAusente(jdbc: JdbcTemplate, modalidade: String, spread: String): Int =
        jdbc.update(
            """
            INSERT INTO spreads_recebiveis (id, modalidade, spread_mensal, vigente_em)
            SELECT gen_random_uuid(), ?, CAST(? AS numeric), CURRENT_TIMESTAMP
            WHERE NOT EXISTS (
                SELECT 1 FROM spreads_recebiveis
                WHERE modalidade = ?
                  AND vigente_em <= CURRENT_TIMESTAMP AND criado_em <= CURRENT_TIMESTAMP
            )
            ON CONFLICT (modalidade, vigente_em) DO NOTHING
            """.trimIndent(),
            modalidade, spread, modalidade,
        )
}
