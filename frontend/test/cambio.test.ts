import { describe, expect, test, vi } from 'vitest'
import { executarCambio } from '../src/aplicacao/cambio'
import { erroJustificativa, formatarEntradaTaxa, normalizarTaxaCambial } from '../src/dominio/cambio'
import { criarPortaCambio } from './fixtures/cambio'

describe('taxa textual localizada sem arredondamento financeiro', () => {
  test.each([
    ['pt-BR', '5,4321', '5.4321'], ['en-US', '5.4321', '5.4321'],
    ['pt-BR', '9.999.999.999,9999999999', '9999999999.9999999999'],
    ['en-US', '9,999,999,999.9999999999', '9999999999.9999999999'],
    ['pt-BR', '0,0000000001', '0.0000000001'], ['en-US', '0005.4300', '0005.4300'],
  ])('%s preserva %s como string %s', (locale, entrada, valor) => {
    expect(normalizarTaxaCambial(entrada, locale)).toEqual({ valida: true, valor })
    expect(normalizarTaxaCambial(formatarEntradaTaxa(valor, locale), locale)).toEqual({
      valida: true, valor: valor.replace(/^0+(?=[0-9])/, ''),
    })
  })
  test.each(['0', '0,0000000000', '-5,43', '+5', '1e3', 'NaN', 'Infinity', '5.4321', '1,', '1,,3',
    '10000000000', '0,00000000001', '1.23,45', '1 234,56', '<script>', ''])('rejeita entrada pt-BR %s', (entrada) => {
    expect(normalizarTaxaCambial(entrada, 'pt-BR').valida).toBe(false)
  })
  test('justificativa rejeita vazia, controle e excesso de comprimento', () => {
    for (const motivo of ['', '   ', 'motivo\nnovo', 'x'.repeat(1001)]) expect(erroJustificativa(motivo)).not.toBe('')
    expect(erroJustificativa('Manutenção excepcional')).toBe('')
  })
})

test.each(['cadastro', 'selecao', 'sincronizacao'] as const)('OPERADOR não executa %s mesmo fora da UI', (tipo) => {
  const porta = criarPortaCambio()
  const entrada = { taxaBrlPorUsd: '5.4321', justificativa: 'motivo', fonte: 'BCB_PTAX' as const }
  expect(() => executarCambio(porta, 'jwt', 'OPERADOR', tipo === 'sincronizacao' ? { tipo } : { tipo, entrada }))
    .toThrow('Seu perfil não tem permissão')
  expect(porta.cadastrar).not.toHaveBeenCalled()
  expect(porta.selecionar).not.toHaveBeenCalled()
  expect(porta.sincronizar).not.toHaveBeenCalled()
})

test('cadastro inválido não chega ao adaptador', () => {
  const porta = criarPortaCambio()
  expect(() => executarCambio(porta, 'jwt', 'ADMIN', {
    tipo: 'cadastro', entrada: { taxaBrlPorUsd: '0.0', justificativa: 'motivo' },
  })).toThrow('Taxa decimal textual inválida')
  expect(porta.cadastrar).not.toHaveBeenCalled()
  vi.clearAllMocks()
})
