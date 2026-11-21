export type Moeda = 'BRL' | 'USD'

export type FiltrosExtrato = {
  inicio: string
  fim: string
  cedenteId: string
  moeda: '' | Moeda
}

export type ItemExtrato = {
  liquidacaoId: string
  recebivelId: string
  cedenteId: string
  modalidade: string
  valorFaceBrl: string
  prazoMeses: number
  versaoRecebivel: number
  taxaBaseId: string
  taxaBaseMensal: string
  taxaBaseVigenteEm: string
  spreadId: string
  spreadMensal: string
  spreadVigenteEm: string
  valorPresenteBrl: string
  desagioBrl: string
  moeda: Moeda
  cotacaoCambialId: string | null
  taxaBrlPorUnidade: string | null
  cotacaoVigenteEm: string | null
  cotacaoValidaAte: string | null
  valorPagamento: string
  liquidadaEm: string
}

export type PaginaExtrato = {
  itens: ItemExtrato[]
  pagina: number
  tamanho: number
  total: number
}
