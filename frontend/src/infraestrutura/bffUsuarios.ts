import { ErroGestaoUsuarios, type PortaGestaoUsuarios } from '../aplicacao/gestaoUsuarios'
import type { MinhaConta, PerfilCriavel, PerfilUsuario, UsuarioOrganizacional } from '../dominio/usuarios'

function objeto(valor: unknown): valor is Record<string, unknown> {
  return typeof valor === 'object' && valor !== null && !Array.isArray(valor)
}

function perfilUsuario(valor: unknown): valor is PerfilUsuario {
  return valor === 'MASTER' || valor === 'ADMIN' || valor === 'OPERADOR'
}

function perfilCriavel(valor: unknown): valor is PerfilCriavel {
  return valor === 'ADMIN' || valor === 'OPERADOR'
}

function usuario(valor: unknown): valor is UsuarioOrganizacional {
  return objeto(valor) && typeof valor.id === 'string' && valor.id.length > 0
    && typeof valor.email === 'string' && perfilUsuario(valor.perfil) && typeof valor.ativo === 'boolean'
}

function minhaConta(valor: unknown): valor is MinhaConta {
  if (!objeto(valor) || !objeto(valor.capacidades)) return false
  const capacidades = valor.capacidades
  return typeof valor.id === 'string' && valor.id.length > 0
    && typeof valor.email === 'string' && perfilUsuario(valor.perfil)
    && Array.isArray(capacidades.perfisCriaveis) && capacidades.perfisCriaveis.every(perfilCriavel)
    && typeof capacidades.podeListarUsuarios === 'boolean'
    && typeof capacidades.podeAlterarStatus === 'boolean'
}

function mensagemHttp(status: number, conflitosEmail: boolean): string {
  const mensagens: Record<number, string> = {
    400: 'Dados inválidos. Confira os campos informados.',
    401: 'Sessão expirada. Entre novamente.',
    403: 'Seu perfil não tem permissão para esta ação.',
    404: 'Usuário não encontrado.',
    409: conflitosEmail
      ? 'Já existe um usuário com este e-mail. Confira a lista ou informe outro endereço.'
      : 'O estado do usuário mudou. Atualize a lista antes de tentar novamente.',
  }
  return mensagens[status] ?? `Não foi possível concluir a operação (HTTP ${status}).`
}

async function requisitar<T>(
  caminho: string,
  validar: (valor: unknown) => valor is T,
  token: string,
  signal: AbortSignal,
  opcoes: RequestInit = {},
): Promise<T> {
  let resposta: Response
  try {
    resposta = await fetch(caminho, {
      ...opcoes,
      headers: {
        ...(opcoes.body ? { 'Content-Type': 'application/json' } : {}),
        Authorization: `Bearer ${token}`,
        ...opcoes.headers,
      },
      signal,
    })
  } catch (falha) {
    if (falha instanceof DOMException && falha.name === 'AbortError') throw falha
    throw new ErroGestaoUsuarios('Não foi possível conectar ao servidor. Verifique a conexão.')
  }
  if (!resposta.ok) {
    const conflitosEmail = opcoes.method === 'POST' && caminho === '/bff/v1/usuarios'
    throw new ErroGestaoUsuarios(mensagemHttp(resposta.status, conflitosEmail), resposta.status)
  }

  let corpo: unknown
  try {
    corpo = await resposta.json()
  } catch {
    throw new ErroGestaoUsuarios('Resposta inválida do servidor.')
  }
  if (!validar(corpo)) throw new ErroGestaoUsuarios('Resposta inválida do servidor.')
  return corpo
}

export const bffUsuarios: PortaGestaoUsuarios = {
  minhaConta: (token, signal) =>
    requisitar('/bff/v1/usuarios/me', minhaConta, token, signal),
  listar: (token, signal) =>
    requisitar('/bff/v1/usuarios', (valor): valor is UsuarioOrganizacional[] =>
      Array.isArray(valor) && valor.every(usuario), token, signal),
  criar: (token, novoUsuario, signal) =>
    requisitar('/bff/v1/usuarios', usuario, token, signal, {
      method: 'POST',
      body: JSON.stringify({
        email: novoUsuario.email,
        senha: novoUsuario.senha,
        perfil: novoUsuario.perfil,
      }),
    }),
  alterarStatus: (token, id, ativo, signal) =>
    requisitar(`/bff/v1/usuarios/${encodeURIComponent(id)}/status`, usuario, token, signal, {
      method: 'PATCH',
      body: JSON.stringify({ ativo }),
    }),
}
