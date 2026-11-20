package br.com.srm.creditengine.cadastro

import br.com.srm.creditengine.cadastro.aplicacao.CedenteNaoEncontrado
import br.com.srm.creditengine.cadastro.aplicacao.porta.entrada.Cadastro
import br.com.srm.creditengine.cadastro.aplicacao.porta.entrada.NovoCedente
import br.com.srm.creditengine.cadastro.aplicacao.porta.entrada.NovoRecebivel
import br.com.srm.creditengine.cadastro.aplicacao.porta.saida.ContextoOrganizacao
import br.com.srm.creditengine.cadastro.dominio.StatusRecebivel
import br.com.srm.creditengine.precificacao.dominio.Modalidades
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles

@EnabledIfEnvironmentVariable(named = "SRM_TEST_POSTGRES", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = ["srm.demo.bootstrap-organizacao=true"])
@ActiveProfiles("demo")
@Import(CadastroPostgresTest.IdentidadeTeste::class)
class CadastroPostgresTest {
    @TestConfiguration(proxyBeanMethods = false)
    class IdentidadeTeste {
        @Bean
        fun contextoTeste() = ContextoTeste()
    }

    class ContextoTeste : ContextoOrganizacao {
        lateinit var id: UUID
        override fun organizacaoId(): UUID = id
    }

    @Autowired private lateinit var cadastro: Cadastro
    @Autowired private lateinit var contexto: ContextoTeste
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired @Qualifier("bootstrapOrganizacao") private lateinit var bootstrap: CommandLineRunner
    private lateinit var organizacaoA: UUID
    private lateinit var organizacaoB: UUID

    @BeforeEach
    fun preparar() {
        organizacaoA = UUID.randomUUID()
        organizacaoB = UUID.randomUUID()
        jdbc.update("INSERT INTO organizacoes (id, nome) VALUES (?, ?), (?, ?)", organizacaoA, "A", organizacaoB, "B")
        contexto.id = organizacaoA
    }

    @Test
    fun `cadastra e consulta apenas dados da organizacao autenticada`() {
        val cedenteA = cadastro.cadastrarCedente(NovoCedente("Cedente A", "documento"))
        val comando = NovoRecebivel(
            cedenteA.id, Modalidades.DUPLICATA_MERCANTIL, BigDecimal("100.000"), 3, LocalDate.of(2027, 1, 15),
        )
        val recebivelA = cadastro.cadastrarRecebivel(comando)
        assertThat(recebivelA.valorFaceBrl).isEqualTo(BigDecimal("100.00"))
        val persistido = cadastro.buscarRecebivel(recebivelA.id)!!
        assertThat(persistido).isEqualTo(recebivelA)
        assertThat(persistido.status).isEqualTo(StatusRecebivel.DISPONIVEL)
        assertThat(persistido.versao).isZero()
        assertThat(cadastro.buscarCedente(cedenteA.id)).isEqualTo(cedenteA)

        contexto.id = organizacaoB
        val cedenteB = cadastro.cadastrarCedente(NovoCedente("Cedente B", "documento"))
        val recebivelB = cadastro.cadastrarRecebivel(
            comando.copy(cedenteId = cedenteB.id, modalidade = Modalidades.CHEQUE_PRE_DATADO),
        )
        assertThat(cadastro.buscarCedente(cedenteA.id)).isNull()
        assertThat(cadastro.buscarRecebivel(recebivelA.id)).isNull()
        assertThat(cadastro.listarCedentes()).containsExactly(cedenteB)
        assertThat(cadastro.listarRecebiveis()).containsExactly(recebivelB)
        assertThatExceptionOfType(CedenteNaoEncontrado::class.java)
            .isThrownBy { cadastro.cadastrarRecebivel(comando) }
        assertThatExceptionOfType(DataIntegrityViolationException::class.java).isThrownBy {
            jdbc.update(
                """INSERT INTO recebiveis (id, organizacao_id, cedente_id, modalidade, valor_face_brl, prazo_meses)
                   VALUES (?, ?, ?, ?, ?, ?)""",
                UUID.randomUUID(), organizacaoB, cedenteA.id, Modalidades.DUPLICATA_MERCANTIL, BigDecimal.ONE, 1,
            )
        }

        contexto.id = organizacaoA
        assertThat(cadastro.listarCedentes()).containsExactly(cedenteA)
        assertThat(cadastro.listarRecebiveis()).containsExactly(recebivelA)
        assertThat(cadastro.buscarCedente(cedenteB.id)).isNull()
        assertThat(cadastro.buscarRecebivel(recebivelB.id)).isNull()
    }

    @Test
    fun `constraint rejeita documento duplicado na mesma organizacao`() {
        cadastro.cadastrarCedente(NovoCedente("Primeiro", "documento"))
        assertThatExceptionOfType(DataIntegrityViolationException::class.java)
            .isThrownBy { cadastro.cadastrarCedente(NovoCedente("Segundo", "documento")) }
        assertThat(cadastro.listarCedentes()).hasSize(1)
    }

    @Test
    fun `bootstrap explicito cria organizacao demonstrativa sem criar identidade`() {
        val id = UUID.fromString("00000000-0000-4000-8000-000000000001")
        bootstrap.run()
        assertThat(jdbc.queryForObject("SELECT count(*) FROM organizacoes WHERE id = ?", Long::class.java, id))
            .isEqualTo(1)
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuarios WHERE organizacao_id = ?", Long::class.java, id))
            .isZero()
    }
}
