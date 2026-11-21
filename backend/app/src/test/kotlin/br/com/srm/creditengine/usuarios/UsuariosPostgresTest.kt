package br.com.srm.creditengine.usuarios

import br.com.srm.creditengine.usuarios.aplicacao.MasterJaProvisionado
import br.com.srm.creditengine.usuarios.aplicacao.porta.entrada.ProvisionarMaster
import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder

@EnabledIfEnvironmentVariable(named = "SRM_TEST_POSTGRES", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UsuariosPostgresTest {
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var passwordEncoder: PasswordEncoder
    @Autowired private lateinit var provisionarMaster: ProvisionarMaster
    @LocalServerPort private var port: Int = 0

    private val http = HttpClient.newHttpClient()
    private val json = ObjectMapper()
    private lateinit var organizacaoA: UUID
    private lateinit var organizacaoB: UUID
    private lateinit var masterA: UUID
    private lateinit var masterB: UUID
    private lateinit var emailMasterA: String
    private lateinit var senhaMasterA: String
    private lateinit var tokenMasterA: String
    private lateinit var tokenMasterB: String

    @BeforeEach
    fun preparar() {
        organizacaoA = UUID.randomUUID()
        organizacaoB = UUID.randomUUID()
        emailMasterA = "master-$organizacaoA@example.invalid"
        val emailMasterB = "master-$organizacaoB@example.invalid"
        senhaMasterA = "Master-Senha-${UUID.randomUUID()}"
        val senhaMasterB = "Master-Senha-${UUID.randomUUID()}"
        jdbc.update(
            "INSERT INTO organizacoes (id, nome) VALUES (?, ?), (?, ?)",
            organizacaoA, "Organização A $organizacaoA", organizacaoB, "Organização B $organizacaoB",
        )
        masterA = provisionarMaster.executar(organizacaoA, emailMasterA, senhaMasterA).id
        masterB = provisionarMaster.executar(organizacaoB, emailMasterB, senhaMasterB).id
        tokenMasterA = login(emailMasterA, senhaMasterA)
        tokenMasterB = login(emailMasterB, senhaMasterB)
    }

    @Test
    fun `bootstrap e local unico e persiste somente hash BCrypt`() {
        val hashOriginal = jdbc.queryForObject(
            "SELECT senha_hash FROM usuarios WHERE id = ?",
            String::class.java,
            masterA,
        )!!
        assertThat(hashOriginal).startsWith("\$2")
        assertThat(hashOriginal).isNotEqualTo(senhaMasterA)
        assertThat(passwordEncoder.matches(senhaMasterA, hashOriginal)).isTrue()

        assertThrows(MasterJaProvisionado::class.java) {
            provisionarMaster.executar(organizacaoA, emailMasterA, "Outra-Senha-${UUID.randomUUID()}")
        }
        assertThat(
            jdbc.queryForObject(
                "SELECT senha_hash FROM usuarios WHERE id = ?",
                String::class.java,
                masterA,
            ),
        ).isEqualTo(hashOriginal)
        assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM usuarios WHERE organizacao_id = ? AND perfil = 'MASTER'",
                Int::class.java,
                organizacaoA,
            ),
        ).isEqualTo(1)

        val resposta = post(
            "/api/v1/usuarios",
            tokenMasterA,
            """{"email":"nao-pode@example.invalid","senha":"Senha-Forte-${UUID.randomUUID()}","perfil":"MASTER"}""",
        )
        assertThat(resposta.statusCode()).isEqualTo(403)

        jdbc.update("UPDATE usuarios SET ativo = FALSE WHERE id = ?", masterA)
        assertThrows(MasterJaProvisionado::class.java) {
            provisionarMaster.executar(organizacaoA, emailMasterA, "Outra-Senha-${UUID.randomUUID()}")
        }
        assertThat(
            jdbc.queryForObject(
                "SELECT senha_hash FROM usuarios WHERE id = ?",
                String::class.java,
                masterA,
            ),
        ).isEqualTo(hashOriginal)

        assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM usuarios WHERE organizacao_id = ? AND perfil = 'MASTER'",
                Int::class.java,
                organizacaoA,
            ),
        ).isEqualTo(1)
    }

    @Test
    fun `hierarquia administrativa lista isolamento status e revogacao funcionam na API e BFF`() {
        val semSessao = requisicao("GET", "/api/v1/usuarios/me")
        assertThat(semSessao.statusCode()).isEqualTo(401)

        val meMaster = get("/api/v1/usuarios/me", tokenMasterA)
        assertThat(meMaster.statusCode()).isEqualTo(200)
        assertThat(meMaster.tree()["id"].asText()).isEqualTo(masterA.toString())
        assertThat(meMaster.tree()["organizacaoId"].asText()).isEqualTo(organizacaoA.toString())
        assertThat(meMaster.tree()["perfil"].asText()).isEqualTo("MASTER")
        assertThat(meMaster.tree()["capacidades"]["perfisCriaveis"].map { it.asText() })
            .containsExactlyInAnyOrder("ADMIN", "OPERADOR")
        assertThat(meMaster.body()).doesNotContain("senha", "senhaHash", "senha_hash", "accessToken")

        val senhaAdmin = "Admin-Senha-${UUID.randomUUID()}"
        val adminResposta = post(
            "/api/v1/usuarios",
            tokenMasterA,
            criarCorpo("admin-$organizacaoA@example.invalid", "ADMIN", senhaAdmin),
        )
        assertThat(adminResposta.statusCode()).isEqualTo(201)
        assertThat(adminResposta.body()).doesNotContain("senha", "senhaHash", "senha_hash", "organizacaoId")
        val adminId = UUID.fromString(adminResposta.tree()["id"].asText())
        val adminEmail = adminResposta.tree()["email"].asText()
        val tokenAdmin = login(adminEmail, senhaAdmin)

        val masterCriaOperador = post(
            "/bff/v1/usuarios",
            tokenMasterA,
            criarCorpo("operador-master-$organizacaoA@example.invalid", "OPERADOR"),
        )
        assertThat(masterCriaOperador.statusCode()).isEqualTo(201)

        val senhaOperador = "Operador-Senha-${UUID.randomUUID()}"
        val operadorResposta = post(
            "/bff/v1/usuarios",
            tokenAdmin,
            criarCorpo("operador-admin-$organizacaoA@example.invalid", "OPERADOR", senhaOperador),
        )
        assertThat(operadorResposta.statusCode()).isEqualTo(201)
        val operadorId = UUID.fromString(operadorResposta.tree()["id"].asText())
        val tokenOperador = login(operadorResposta.tree()["email"].asText(), senhaOperador)

        val adminMe = get("/bff/v1/usuarios/me", tokenAdmin)
        assertThat(adminMe.statusCode()).isEqualTo(200)
        assertThat(adminMe.tree()["perfil"].asText()).isEqualTo("ADMIN")
        assertThat(adminMe.tree()["capacidades"]["perfisCriaveis"].map { it.asText() })
            .containsExactly("OPERADOR")

        val usuariosApi = get("/api/v1/usuarios", tokenMasterA)
        val usuariosBff = get("/bff/v1/usuarios", tokenMasterA)
        assertThat(usuariosApi.statusCode()).isEqualTo(200)
        assertThat(usuariosBff.body()).isEqualTo(usuariosApi.body())
        assertThat(usuariosApi.tree().map { it["perfil"].asText() })
            .contains("ADMIN", "OPERADOR")
            .doesNotContain("MASTER")
        assertThat(usuariosApi.body()).doesNotContain("senha", "senhaHash", "senha_hash")
        assertThat(get("/api/v1/usuarios", tokenMasterB).tree()).isEmpty()

        val listaAdmin = get("/api/v1/usuarios", tokenAdmin)
        assertThat(listaAdmin.statusCode()).isEqualTo(200)
        assertThat(listaAdmin.tree().map { it["perfil"].asText() }).containsOnly("OPERADOR")
        val operadorMe = get("/api/v1/usuarios/me", tokenOperador)
        assertThat(operadorMe.statusCode()).isEqualTo(200)
        assertThat(operadorMe.tree()["capacidades"]["podeListarUsuarios"].asBoolean()).isFalse()
        assertThat(get("/api/v1/usuarios", tokenOperador).statusCode()).isEqualTo(403)
        assertThat(
            post(
                "/api/v1/usuarios",
                tokenOperador,
                criarCorpo("sem-permissao-$organizacaoA@example.invalid", "OPERADOR"),
            ).statusCode(),
        ).isEqualTo(403)
        assertThat(
            post(
                "/api/v1/usuarios",
                tokenAdmin,
                criarCorpo("admin-nao-autorizado-$organizacaoA@example.invalid", "ADMIN"),
            ).statusCode(),
        ).isEqualTo(403)
        assertThat(
            requisicao(
                "PATCH",
                "/api/v1/usuarios/$adminId/status",
                tokenAdmin,
                """{"ativo":false}""",
            ).statusCode(),
        ).isEqualTo(403)
        assertThat(
            requisicao(
                "PATCH",
                "/api/v1/usuarios/$masterA/status",
                tokenMasterA,
                """{"ativo":false}""",
            ).statusCode(),
        ).isEqualTo(403)

        val alteracaoCruzada = requisicao(
            "PATCH",
            "/api/v1/usuarios/$masterB/status",
            tokenMasterA,
            """{"ativo":false}""",
        )
        assertThat(alteracaoCruzada.statusCode()).isEqualTo(404)
        assertThat(alteracaoCruzada.body()).doesNotContain(masterB.toString())
        assertThat(
            requisicao(
                "PATCH",
                "/api/v1/usuarios/$adminId/status",
                tokenMasterB,
                """{"ativo":false}""",
            ).statusCode(),
        ).isEqualTo(404)
        val corpoComOrganizacao = post(
            "/api/v1/usuarios",
            tokenMasterA,
            """{"email":"tentativa-$organizacaoA@example.invalid","senha":"Senha-Forte-${UUID.randomUUID()}","perfil":"OPERADOR","organizacaoId":"$organizacaoB"}""",
        )
        assertThat(corpoComOrganizacao.statusCode()).isEqualTo(400)
        assertThat(
            post(
                "/api/v1/usuarios",
                tokenMasterA,
                criarCorpo("master-nao-pode-$organizacaoA@example.invalid", "MASTER"),
            ).statusCode(),
        ).isEqualTo(403)

        val inativacao = requisicao(
            "PATCH",
            "/bff/v1/usuarios/$operadorId/status",
            tokenAdmin,
            """{"ativo":false}""",
        )
        assertThat(inativacao.statusCode()).isEqualTo(200)
        assertThat(inativacao.tree()["ativo"].asBoolean()).isFalse()
        assertThat(get("/api/v1/usuarios/me", tokenOperador).statusCode()).isEqualTo(401)
        assertThat(get("/api/v1/usuarios/me", tokenAdmin).statusCode()).isEqualTo(200)

        jdbc.update("UPDATE usuarios SET perfil = 'OPERADOR' WHERE id = ?", adminId)
        assertThat(get("/api/v1/usuarios", tokenAdmin).statusCode()).isEqualTo(403)
        assertThat(get("/api/v1/usuarios/me", tokenAdmin).tree()["perfil"].asText()).isEqualTo("OPERADOR")
        assertThat(
            requisicao(
                "PATCH",
                "/api/v1/usuarios/$adminId/status",
                tokenMasterA,
                """{"ativo":false}""",
            ).statusCode(),
        ).isEqualTo(200)
        assertThat(get("/api/v1/usuarios/me", tokenAdmin).statusCode()).isEqualTo(401)
    }

    @Test
    fun `validacoes e unicidade de email impedem entrada ambigua`() {
        val email = "unico-$organizacaoA@example.invalid"
        val criado = post(
            "/api/v1/usuarios",
            tokenMasterA,
            criarCorpo(email, "OPERADOR"),
        )
        assertThat(criado.statusCode()).isEqualTo(201)

        val duplicado = post(
            "/bff/v1/usuarios",
            tokenMasterA,
            criarCorpo(email.uppercase(), "OPERADOR"),
        )
        assertThat(duplicado.statusCode()).isEqualTo(409)

        assertThat(
            post("/api/v1/usuarios", tokenMasterA, """{"email":"invalido","senha":"Senha-Forte-123","perfil":"OPERADOR"}""")
                .statusCode(),
        ).isEqualTo(400)
        assertThat(
            post(
                "/api/v1/usuarios",
                tokenMasterA,
                criarCorpo("senha-curta-$organizacaoA@example.invalid", "OPERADOR", "curta"),
            ).statusCode(),
        ).isEqualTo(400)
        assertThat(
            post(
                "/api/v1/usuarios",
                tokenMasterA,
                """{"email":"campo-extra-$organizacaoA@example.invalid","senha":"Senha-Forte-${UUID.randomUUID()}","perfil":"OPERADOR","ativo":false}""",
            ).statusCode(),
        ).isEqualTo(400)
    }

    private fun criarCorpo(email: String, perfil: String, senha: String = "Senha-Forte-${UUID.randomUUID()}"): String =
        json.writeValueAsString(mapOf("email" to email, "senha" to senha, "perfil" to perfil))

    private fun login(email: String, senha: String): String {
        val resposta = post(
            "/api/v1/auth/token",
            token = null,
            corpo = json.writeValueAsString(mapOf("email" to email, "password" to senha)),
        )
        assertThat(resposta.statusCode()).isEqualTo(200)
        return resposta.tree()["accessToken"].asText()
    }

    private fun get(path: String, token: String) = requisicao("GET", path, token)

    private fun post(path: String, token: String?, corpo: String) =
        requisicao("POST", path, token, corpo)

    private fun requisicao(
        metodo: String,
        path: String,
        token: String? = null,
        corpo: String? = null,
    ): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port$path"))
            .header("Accept", "application/json")
        token?.let { builder.header("Authorization", "Bearer $it") }
        corpo?.let { builder.header("Content-Type", "application/json") }
        val publisher = corpo?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody()
        return http.send(
            builder.method(metodo, publisher).build(),
            HttpResponse.BodyHandlers.ofString(),
        )
    }

    private fun HttpResponse<String>.tree() = json.readTree(body())
}
