import type { Modalidade, Moeda, Simulacao } from './operacoes'

export type ComandoImportacao = {
  referenciaExterna: string
  cedenteId: string
  modalidade: Modalidade
  valorFaceBrl: string
  prazoMeses: number
  vencimento: string | null
  moedaPagamento: Moeda
}

export type LinhaLote = {
  linha: number
  status: 'NOVO' | 'JA_CADASTRADO' | 'JA_LIQUIDADO' | 'INVALIDO'
  comando: ComandoImportacao | null
  erros: { campo: string; mensagem: string }[]
  simulacao: Simulacao | null
  avisoSimulacao: string | null
  recebivelId: string | null
}

export type PreviaCadastro = { confirmavel: boolean; itens: LinhaLote[] }
export type CadastroLote = {
  loteId: string
  cadastrados: number
  itens: { referenciaExterna: string; recebivelId: string }[]
}
export type ItemLiquidacaoLote = { recebivelId: string; moedaPagamento: Moeda }
export type ComandoLiquidacaoLote = {
  itens: ItemLiquidacaoLote[]
  hashPrevia: string
  confirmacaoFinanceira: true
}
export type PreviaItemLote = ItemLiquidacaoLote & {
  versao: number
  estado: string
  valorFaceBrl: string
  valorPresenteBrl: string | null
  desagioBrl: string | null
  valorPagamento: string | null
  taxaBaseId: string | null
  taxaBaseMensal: string | null
  taxaBaseVigenteEm: string | null
  spreadId: string | null
  spreadMensal: string | null
  spreadVigenteEm: string | null
  cotacaoId: string | null
  taxaBrlPorUsd: string | null
  cotacaoVigenteEm: string | null
  cotacaoValidaAte: string | null
  fonteCambio: string | null
  impedimento: string | null
}
export type PreviaFinanceiraLote = {
  loteId: string
  referencia: string
  aptoParaLiquidar: boolean
  hashPrevia: string | null
  itens: PreviaItemLote[]
  totaisPorMoeda: Partial<Record<Moeda, string>>
}
export type ResultadoLiquidacaoLote = {
  loteId: string
  liquidadaEm: string
  itens: (ItemLiquidacaoLote & {
    liquidacaoId: string
    valorPresenteBrl: string
    desagioBrl: string
    valorPagamento: string
  })[]
}

export function cadastroCompleto(previa: PreviaCadastro | null): boolean {
  return Boolean(previa?.confirmavel && previa.itens.length > 0
    && previa.itens.every((item) => item.status === 'NOVO' && item.comando && item.erros.length === 0)
    && new Set(previa.itens.map((item) => item.comando?.referenciaExterna)).size === previa.itens.length)
}

export function simulacaoCompleta(item: PreviaItemLote, agora = Date.now()): boolean {
  return item.estado === 'DISPONIVEL' && !item.impedimento
    && Number.isSafeInteger(item.versao) && item.versao >= 0
    && [item.valorPresenteBrl, item.desagioBrl, item.valorPagamento, item.taxaBaseMensal, item.spreadMensal]
      .every((valor) => typeof valor === 'string' && /^\d+(\.\d+)?$/.test(valor))
    && Boolean(item.taxaBaseId && item.spreadId && item.taxaBaseVigenteEm && item.spreadVigenteEm)
    && (item.moedaPagamento === 'BRL' || Boolean(item.cotacaoId && item.taxaBrlPorUsd
      && item.cotacaoVigenteEm && item.cotacaoValidaAte && item.fonteCambio
      && Date.parse(item.cotacaoValidaAte) > agora))
}

export function previaCompleta(previa: PreviaFinanceiraLote | null, itens: ItemLiquidacaoLote[], agora = Date.now()): boolean {
  return Boolean(previa?.aptoParaLiquidar && previa.hashPrevia?.match(/^[0-9a-f]{64}$/)
    && itens.length > 0 && previa.itens.length === itens.length
    && new Set(previa.itens.map((item) => item.recebivelId)).size === itens.length
    && previa.itens.every((item) => simulacaoCompleta(item, agora)
      && itens.some((esperado) => esperado.recebivelId === item.recebivelId && esperado.moedaPagamento === item.moedaPagamento))
    && itens.every((item) => typeof previa.totaisPorMoeda[item.moedaPagamento] === 'string'))
}
