import type { FiltrosExtrato, PaginaExtrato } from '../dominio/extrato'

export type ConsultaExtrato = FiltrosExtrato & { pagina: number; tamanho: number }
export type ErrosFiltros = Partial<Record<keyof FiltrosExtrato, string>>

export interface PortaExtrato {
  consultar(consulta: ConsultaExtrato, token: string, signal: AbortSignal): Promise<PaginaExtrato>
}

export interface PortaAutenticacao {
  autenticar(email: string, senha: string, signal: AbortSignal): Promise<string>
}

export class SessaoExpirada extends Error {}

function dataValida(valor: string) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(valor) || valor.startsWith('0000')) return false
  const data = new Date(`${valor}T00:00:00Z`)
  return !Number.isNaN(data.getTime()) && data.toISOString().slice(0, 10) === valor
}

export function validarFiltros(filtros: FiltrosExtrato): ErrosFiltros {
  const erros: ErrosFiltros = {}
  if (filtros.inicio && !dataValida(filtros.inicio)) erros.inicio = 'Informe uma data válida.'
  if (filtros.fim && !dataValida(filtros.fim)) erros.fim = 'Informe uma data válida.'
  if (!erros.inicio && !erros.fim && filtros.inicio && filtros.fim && filtros.inicio > filtros.fim) {
    erros.fim = 'A data final deve ser igual ou posterior à inicial.'
  }
  if (filtros.cedenteId && !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(filtros.cedenteId)) {
    erros.cedenteId = 'Informe um UUID de cedente válido.'
  }
  if (filtros.moeda !== '' && filtros.moeda !== 'BRL' && filtros.moeda !== 'USD') erros.moeda = 'Informe uma moeda válida.'
  return erros
}

export function consultarExtrato(porta: PortaExtrato, consulta: ConsultaExtrato, token: string, signal: AbortSignal) {
  if (Object.keys(validarFiltros(consulta)).length || !Number.isSafeInteger(consulta.pagina) || consulta.pagina < 0 ||
      !Number.isInteger(consulta.tamanho) || consulta.tamanho < 1 || consulta.tamanho > 100) {
    throw new Error('Filtros ou paginação inválidos.')
  }
  return porta.consultar(consulta, token, signal)
}
