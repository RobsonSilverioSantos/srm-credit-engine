import { vi } from 'vitest'
import type { PortaLotes } from '../../src/aplicacao/lotes'
import type { CadastroLote, PreviaCadastro, PreviaFinanceiraLote, ResultadoLiquidacaoLote } from '../../src/dominio/lotes'

export function dadosLote(quantidade = 2) {
  const previa: PreviaCadastro = {
    confirmavel: true,
    itens: Array.from({ length: quantidade }, (_, indice) => ({
      linha: indice + 2, status: 'NOVO',
      comando: {
        referenciaExterna: `REF-${indice}`, cedenteId: 'cedente', modalidade: 'DUPLICATA_MERCANTIL',
        valorFaceBrl: '100000.00', prazoMeses: 3, vencimento: null, moedaPagamento: 'BRL',
      },
      erros: [], avisoSimulacao: null, recebivelId: null,
      simulacao: {
        moeda: 'BRL', valorPresenteBrl: '92859.94', desagioBrl: '7140.06', valorPagamento: '92859.94',
        taxaBaseMensal: '0.01', spreadMensal: '0.015', cotacaoBrlPorUsd: null,
        referencia: '2026-11-22T12:00:00Z', cotacaoValidaAte: null,
      },
    })),
  }
  const cadastro: CadastroLote = {
    loteId: 'lote-a', cadastrados: quantidade,
    itens: previa.itens.map((item, indice) => ({ referenciaExterna: item.comando!.referenciaExterna, recebivelId: `titulo-${indice}` })),
  }
  const financeira: PreviaFinanceiraLote = {
    loteId: cadastro.loteId, referencia: '2026-11-22T12:00:00Z', aptoParaLiquidar: true, hashPrevia: 'a'.repeat(64),
    totaisPorMoeda: { BRL: quantidade === 200 ? '18571988.00' : '185719.88' },
    itens: cadastro.itens.map(({ recebivelId }) => ({
      recebivelId, versao: 0, estado: 'DISPONIVEL', moedaPagamento: 'BRL', valorFaceBrl: '100000.00',
      valorPresenteBrl: '92859.94', desagioBrl: '7140.06', valorPagamento: '92859.94',
      taxaBaseId: 'base', taxaBaseMensal: '0.01', taxaBaseVigenteEm: '2026-11-22T10:00:00Z',
      spreadId: 'spread', spreadMensal: '0.015', spreadVigenteEm: '2026-11-22T10:00:00Z',
      cotacaoId: null, taxaBrlPorUsd: null, cotacaoVigenteEm: null, cotacaoValidaAte: null, fonteCambio: null,
      impedimento: null,
    })),
  }
  const liquidacao: ResultadoLiquidacaoLote = {
    loteId: cadastro.loteId, liquidadaEm: '2026-11-22T12:01:00Z',
    itens: financeira.itens.map((item, indice) => ({
      recebivelId: item.recebivelId, moedaPagamento: item.moedaPagamento, liquidacaoId: `liquidacao-${indice}`,
      valorPresenteBrl: item.valorPresenteBrl!, desagioBrl: item.desagioBrl!, valorPagamento: item.valorPagamento!,
    })),
  }
  return { previa, cadastro, financeira, liquidacao }
}

export function criarPortaLotes(quantidade = 2) {
  const dados = dadosLote(quantidade)
  return {
    prevalidar: vi.fn().mockResolvedValue(dados.previa),
    cadastrar: vi.fn().mockResolvedValue(dados.cadastro),
    previsualizar: vi.fn().mockResolvedValue(dados.financeira),
    liquidar: vi.fn().mockResolvedValue(dados.liquidacao),
  } satisfies PortaLotes
}

export function dadosLoteMisto(validaAte = new Date(Date.now() + 60000).toISOString()) {
  const dados = dadosLote()
  dados.previa.itens[1]!.comando!.moedaPagamento = 'USD'
  dados.previa.itens[1]!.simulacao = {
    ...dados.previa.itens[1]!.simulacao!, moeda: 'USD', valorPagamento: '17094.67',
    cotacaoBrlPorUsd: '5.4321', cotacaoValidaAte: validaAte,
  }
  Object.assign(dados.financeira.itens[1]!, {
    moedaPagamento: 'USD', valorPagamento: '17094.67', cotacaoId: 'cotacao',
    taxaBrlPorUsd: '5.4321', cotacaoVigenteEm: new Date(Date.now() - 60000).toISOString(),
    cotacaoValidaAte: validaAte, fonteCambio: 'DEMO',
  })
  dados.financeira.totaisPorMoeda = { BRL: '92859.94', USD: '17094.67' }
  dados.liquidacao.itens[1]!.moedaPagamento = 'USD'
  dados.liquidacao.itens[1]!.valorPagamento = '17094.67'
  return dados
}
