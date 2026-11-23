import { separadorDecimalMonetario } from './formatacaoMonetaria'
import type { PerfilUsuario } from './usuarios'

export type FonteCambial = 'BCB_PTAX' | 'MANUAL' | 'DEMO' | 'LEGADO'
export type CotacaoCambial = {
  id: string
  taxaBrlPorUsd: string
  moedaBase: 'BRL'
  moedaCotada: 'USD'
  fonte: FonteCambial
  lado: 'VENDA'
  versao: number
  referencia: string
  observadaEm: string
  dataBoletim: string | null
  horarioOriginal: string | null
  boletim: string | null
  fuso: string | null
  autorId: string | null
  justificativa: string | null
}
export type SelecaoCambial = {
  id: string
  fonte: FonteCambial
  cotacaoId: string | null
  autorizadoEm: string
  validaAte: string | null
  politicaId: string
  autorId: string | null
  justificativa: string | null
}
export type PaginaCambio<T> = { itens: T[]; pagina: number; tamanho: number; total: number }
export type ConsultaCambio = { pagina: number; tamanho: number; fonte?: FonteCambial }
export type NovaCotacaoManual = { taxaBrlPorUsd: string; justificativa: string }
export type NovaSelecaoCambial =
  | { fonte: 'MANUAL'; cotacaoId: string; justificativa: string }
  | { fonte: 'BCB_PTAX'; justificativa: string }

export function podeAdministrarCambio(perfil: PerfilUsuario): boolean {
  return perfil === 'MASTER' || perfil === 'ADMIN'
}

export function erroJustificativa(texto: string): string {
  return texto.trim().length < 1 || texto.trim().length > 1000 || [...texto].some((caractere) => {
    const codigo = caractere.charCodeAt(0)
    return codigo <= 31 || (codigo >= 127 && codigo <= 159)
  })
    ? 'Informe uma justificativa de 1 a 1000 caracteres, sem quebras de linha ou controles.'
    : ''
}

export function normalizarTaxaCambial(texto: string, locale: string):
  { valida: true; valor: string } | { valida: false; erro: string } {
  const decimal = separadorDecimalMonetario(locale)
  const grupo = new Intl.NumberFormat(locale).formatToParts(1000n).find((parte) => parte.type === 'group')?.value
  const partes = texto.trim().split(decimal)
  const inteiro = partes[0] ?? ''
  const agrupado = grupo ? inteiro.split(grupo) : [inteiro]
  const gruposValidos = agrupado.length === 1 || (
    /^[0-9]{1,3}$/.test(agrupado[0] ?? '') && agrupado.slice(1).every((item) => /^[0-9]{3}$/.test(item))
  )
  const canonico = `${agrupado.join('')}${partes.length === 2 ? `.${partes[1]}` : ''}`
  if (partes.length > 2 || !gruposValidos || !/^[0-9]{1,10}(?:\.[0-9]{1,10})?$/.test(canonico)) {
    return { valida: false, erro: 'Use até 10 dígitos inteiros e 10 decimais no formato do idioma selecionado.' }
  }
  if (!/[1-9]/.test(canonico)) return { valida: false, erro: 'Informe uma taxa maior que zero.' }
  return { valida: true, valor: canonico }
}

export function formatarEntradaTaxa(valor: string, locale: string): string {
  const [inteiro, fracao] = valor.split('.')
  return new Intl.NumberFormat(locale, { maximumFractionDigits: 0 }).format(BigInt(inteiro!))
    + (fracao === undefined ? '' : `${separadorDecimalMonetario(locale)}${fracao}`)
}
