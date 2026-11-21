import { isPerfilCriavel, type MinhaConta, type NovoUsuarioOrganizacional, type UsuarioOrganizacional } from '../dominio/usuarios'

export interface PortaGestaoUsuarios {
  minhaConta(token: string, signal: AbortSignal): Promise<MinhaConta>
  listar(token: string, signal: AbortSignal): Promise<UsuarioOrganizacional[]>
  criar(token: string, usuario: NovoUsuarioOrganizacional, signal: AbortSignal): Promise<UsuarioOrganizacional>
  alterarStatus(token: string, id: string, ativo: boolean, signal: AbortSignal): Promise<UsuarioOrganizacional>
}

export class ErroGestaoUsuarios extends Error {
  constructor(message: string, public readonly status?: number) {
    super(message)
    this.name = 'ErroGestaoUsuarios'
  }
}

export function listarUsuarios(
  porta: PortaGestaoUsuarios, token: string, conta: MinhaConta, signal: AbortSignal,
): Promise<UsuarioOrganizacional[]> {
  if (!conta.capacidades.podeListarUsuarios) throw new ErroGestaoUsuarios('Seu perfil não pode listar usuários.', 403)
  return porta.listar(token, signal)
}

export function criarUsuario(
  porta: PortaGestaoUsuarios, token: string, conta: MinhaConta,
  usuario: NovoUsuarioOrganizacional, signal: AbortSignal,
): Promise<UsuarioOrganizacional> {
  if (!conta.capacidades.perfisCriaveis.includes(usuario.perfil)) {
    throw new ErroGestaoUsuarios('Seu perfil não pode criar este tipo de usuário.', 403)
  }
  return porta.criar(token, usuario, signal)
}

export function alterarStatusUsuario(
  porta: PortaGestaoUsuarios, token: string, conta: MinhaConta,
  usuario: UsuarioOrganizacional, ativo: boolean, signal: AbortSignal,
): Promise<UsuarioOrganizacional> {
  const perfilGerenciavel = isPerfilCriavel(usuario.perfil)
    && conta.capacidades.perfisCriaveis.includes(usuario.perfil)
  if (!conta.capacidades.podeAlterarStatus || usuario.id === conta.id || !perfilGerenciavel) {
    throw new ErroGestaoUsuarios('Seu perfil não pode alterar este usuário.', 403)
  }
  return porta.alterarStatus(token, usuario.id, ativo, signal)
}
