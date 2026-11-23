package br.com.srm.creditengine.usuarios.adaptador.entrada

import br.com.srm.creditengine.usuarios.aplicacao.porta.entrada.GestaoUsuarios
import br.com.srm.creditengine.usuarios.aplicacao.porta.entrada.MinhaConta
import br.com.srm.creditengine.usuarios.aplicacao.porta.entrada.NovoUsuarioOrganizacional
import br.com.srm.creditengine.usuarios.dominio.PerfilUsuario
import br.com.srm.creditengine.usuarios.dominio.UsuarioOrganizacional
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.parameters.RequestBody as OpenApiRequestBody
import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode

@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequestMapping(value = ["/api/v1", "/bff/v1"])
@SecurityRequirement(name = "bearerAuth")
@ApiResponses(
    ApiResponse(responseCode = "400", description = "Entrada inválida"),
    ApiResponse(responseCode = "401", description = "Autenticação necessária"),
    ApiResponse(responseCode = "403", description = "Perfil sem permissão"),
    ApiResponse(responseCode = "404", description = "Usuário não encontrado na organização"),
    ApiResponse(responseCode = "409", description = "E-mail já cadastrado"),
)
class UsuariosHttp(
    private val gestao: GestaoUsuarios,
) {
    private fun UsuarioOrganizacional.resposta() = UsuarioResposta(
        id = id,
        email = email,
        perfil = perfil.name,
        ativo = ativo,
    )

    private fun MinhaConta.resposta() = MinhaContaResposta(
        id = usuario.id,
        organizacaoId = usuario.organizacaoId,
        email = usuario.email,
        perfil = usuario.perfil.name,
        ativo = usuario.ativo,
        capacidades = CapacidadesUsuarioResposta(
            perfisCriaveis = perfisCriaveis.map(PerfilUsuario::name),
            podeListarUsuarios = podeListarUsuarios,
            podeAlterarStatus = podeAlterarStatus,
        ),
    )

    private fun JsonNode.validarCampos(vararg permitidos: String) {
        require(isObject && propertyNames().all { it in permitidos }) { "Objeto ou campos inválidos" }
    }

    private fun JsonNode.texto(nome: String): String {
        val campo = get(nome)
        require(campo != null && campo.isString) { "Campo inválido" }
        return campo.stringValue()
    }

    private fun uuid(valor: String): UUID {
        require(valor.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))) {
            "UUID inválido"
        }
        return UUID.fromString(valor)
    }

    @GetMapping("/usuarios/me")
    @Operation(summary = "Consultar identidade e capacidades do usuário autenticado")
    fun minhaConta(): MinhaContaResposta = gestao.minhaConta().resposta()

    @GetMapping("/usuarios")
    @Operation(summary = "Listar usuários administráveis da própria organização")
    fun listar(): List<UsuarioResposta> = gestao.listar().map { it.resposta() }

    @PostMapping("/usuarios")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Criar ADMIN ou OPERADOR conforme a hierarquia autorizada",
        requestBody = OpenApiRequestBody(
            required = true,
            content = [Content(schema = Schema(implementation = UsuarioEntrada::class))],
        ),
    )
    fun criar(@RequestBody corpo: JsonNode): ResponseEntity<UsuarioResposta> {
        corpo.validarCampos("email", "senha", "perfil")
        val comando = NovoUsuarioOrganizacional(
            email = corpo.texto("email"),
            senha = corpo.texto("senha"),
            perfil = PerfilUsuario.valueOf(corpo.texto("perfil")),
        )
        val criado = gestao.criar(comando).resposta()
        return ResponseEntity.status(HttpStatus.CREATED).body(criado)
    }

    @PatchMapping("/usuarios/{id}/status")
    @Operation(
        summary = "Ativar ou inativar usuário administrável da própria organização",
        requestBody = OpenApiRequestBody(
            required = true,
            content = [Content(schema = Schema(implementation = StatusUsuarioEntrada::class))],
        ),
    )
    fun alterarStatus(
        @PathVariable id: String,
        @RequestBody corpo: JsonNode,
    ): UsuarioResposta {
        corpo.validarCampos("ativo")
        val campoAtivo = corpo.get("ativo")
        require(campoAtivo != null && campoAtivo.isBoolean) { "Campo ativo inválido" }
        return gestao.alterarStatus(uuid(id), campoAtivo.booleanValue()).resposta()
    }
}
