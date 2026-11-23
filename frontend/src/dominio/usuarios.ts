export type PerfilUsuario = 'MASTER' | 'ADMIN' | 'OPERADOR'
export type PerfilCriavel = Exclude<PerfilUsuario, 'MASTER'>

export function isPerfilCriavel(perfil: PerfilUsuario): perfil is PerfilCriavel {
  return perfil === 'ADMIN' || perfil === 'OPERADOR'
}

export type UsuarioOrganizacional = {
  id: string
  email: string
  perfil: PerfilUsuario
  ativo: boolean
}

export type MinhaConta = {
  id: string
  email: string
  perfil: PerfilUsuario
  capacidades: {
    perfisCriaveis: PerfilCriavel[]
    podeListarUsuarios: boolean
    podeAlterarStatus: boolean
  }
}

export type NovoUsuarioOrganizacional = {
  email: string
  senha: string
  perfil: PerfilCriavel
}
