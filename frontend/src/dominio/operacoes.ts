export type Modalidade = 'DUPLICATA_MERCANTIL' | 'CHEQUE_PRE_DATADO'
export type Moeda = 'BRL' | 'USD'

export type EntradaSimulacao = {
  modalidade: Modalidade
  valorFaceBrl: string
  prazoMeses: number
  moeda: Moeda
}

export type Simulacao = {
  moeda: Moeda
  valorPresenteBrl: string
  desagioBrl: string
  valorPagamento: string
  taxaBaseMensal: string
  spreadMensal: string
  cotacaoBrlPorUsd: string | null
  referencia: string
  cotacaoValidaAte: string | null
}

export type Cedente = { id: string; nome: string; documento: string; ativo: boolean }
export type EntradaRecebivel = Omit<EntradaSimulacao, 'moeda'> & {
  cedenteId: string
  vencimento: string | null
}
export type Recebivel = { id: string }
export type IntencaoLiquidacao = { id: string; moeda: Moeda; chave: string }
export type Liquidacao = {
  liquidacaoId: string
  recebivelId: string
  moeda: Moeda
  valorPresenteBrl: string
  desagioBrl: string
  valorPagamento: string
  liquidadaEm: string
}
export type Sessao = { token: string; expiraEm: number }
