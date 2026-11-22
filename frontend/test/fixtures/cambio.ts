import { vi } from 'vitest'
import type { PortaCambio } from '../../src/aplicacao/cambio'
import type { CotacaoCambial, NovaSelecaoCambial, SelecaoCambial } from '../../src/dominio/cambio'

export const cotacaoManual: CotacaoCambial = {
  id: 'd6d5352d-3d42-4939-a60a-d6d5bbc8bc77', taxaBrlPorUsd: '5.4321000000',
  moedaBase: 'BRL', moedaCotada: 'USD', fonte: 'MANUAL', lado: 'VENDA', versao: 1,
  referencia: '2026-11-22T22:00:00Z', observadaEm: '2026-11-22T22:00:00Z',
  dataBoletim: null, horarioOriginal: null, boletim: null, fuso: null,
  autorId: 'autor-restrito', justificativa: 'motivo restrito',
}
export const selecaoManual: SelecaoCambial = {
  id: 'd3506632-bc97-4e8d-a5b1-d83cb99708dc', fonte: 'MANUAL', cotacaoId: cotacaoManual.id,
  autorizadoEm: '2026-11-23T02:59:00Z', validaAte: '2026-11-23T03:00:00Z',
  politicaId: 'politica-1', autorId: 'autor-restrito', justificativa: 'motivo restrito',
}
export function criarPortaCambio() {
  return {
    historico: vi.fn().mockResolvedValue({ itens: [cotacaoManual], pagina: 0, tamanho: 20, total: 1 }),
    selecoes: vi.fn().mockResolvedValue({ itens: [selecaoManual], pagina: 0, tamanho: 20, total: 1 }),
    cadastrar: vi.fn().mockResolvedValue(cotacaoManual),
    selecionar: vi.fn(async (_token: string, entrada: NovaSelecaoCambial): Promise<SelecaoCambial> => ({
      ...selecaoManual, fonte: entrada.fonte,
      cotacaoId: entrada.fonte === 'MANUAL' ? entrada.cotacaoId : null,
      validaAte: entrada.fonte === 'MANUAL' ? selecaoManual.validaAte : null,
    })),
    sincronizar: vi.fn().mockResolvedValue({ versoesIncluidas: 2 }),
  } satisfies PortaCambio
}
