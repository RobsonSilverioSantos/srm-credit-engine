import {
  erroJustificativa, podeAdministrarCambio,
  type ConsultaCambio, type CotacaoCambial, type NovaCotacaoManual, type NovaSelecaoCambial,
  type PaginaCambio, type SelecaoCambial,
} from '../dominio/cambio'
import type { PerfilUsuario } from '../dominio/usuarios'

export interface PortaCambio {
  historico(token: string, consulta: ConsultaCambio, signal: AbortSignal): Promise<PaginaCambio<CotacaoCambial>>
  selecoes(token: string, consulta: Omit<ConsultaCambio, 'fonte'>, signal: AbortSignal): Promise<PaginaCambio<SelecaoCambial>>
  cadastrar(token: string, entrada: NovaCotacaoManual): Promise<CotacaoCambial>
  selecionar(token: string, entrada: NovaSelecaoCambial): Promise<SelecaoCambial>
  sincronizar(token: string): Promise<{ versoesIncluidas: number }>
}

export class ErroCambio extends Error {
  constructor(message: string, public readonly status?: number, public readonly resultadoIncerto = false) {
    super(message)
    this.name = 'ErroCambio'
  }
}

export type ComandoCambio =
  | { tipo: 'cadastro'; entrada: NovaCotacaoManual }
  | { tipo: 'selecao'; entrada: NovaSelecaoCambial }
  | { tipo: 'sincronizacao' }

export function executarCambio(porta: PortaCambio, token: string, perfil: PerfilUsuario, comando: ComandoCambio) {
  if (!podeAdministrarCambio(perfil)) throw new ErroCambio('Seu perfil não tem permissão para esta ação.', 403)
  if (comando.tipo !== 'sincronizacao') {
    const erro = erroJustificativa(comando.entrada.justificativa)
    if (erro) throw new ErroCambio(erro, 400)
  }
  if (comando.tipo === 'cadastro') {
    if (!/^[0-9]{1,10}(?:\.[0-9]{1,10})?$/.test(comando.entrada.taxaBrlPorUsd)
      || !/[1-9]/.test(comando.entrada.taxaBrlPorUsd)) throw new ErroCambio('Taxa decimal textual inválida.', 400)
    return porta.cadastrar(token, comando.entrada)
  }
  if (comando.tipo === 'selecao') return porta.selecionar(token, comando.entrada)
  return porta.sincronizar(token)
}
