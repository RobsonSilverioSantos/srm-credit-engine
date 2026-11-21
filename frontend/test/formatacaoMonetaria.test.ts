import { expect, test } from 'vitest'
import {
  formatarCotacao,
  formatarMonetario,
  formatarPercentual,
  normalizarEntradaMonetaria,
} from '../src/dominio/formatacaoMonetaria'

test.each([
  ['1.234,5', 'pt-BR', '1234.50'],
  ['1,234.50', 'en-US', '1234.50'],
  ['1.234', 'pt-BR', '1234.00'],
  ['1,234', 'en-US', '1234.00'],
  ['00000000000000001,2', 'pt-BR', '1.20'],
  ['12345678901234567.89', 'en-US', '12345678901234567.89'],
])('normaliza "%s" em %s sem converter para ponto flutuante', (entrada, locale, esperado) => {
  expect(normalizarEntradaMonetaria(entrada, locale)).toEqual({ valida: true, valorCanonico: esperado })
})

test.each([
  ['1,234', 'pt-BR'],
  ['1.234', 'en-US'],
  ['1.234,56', 'en-US'],
  ['1,234.56', 'pt-BR'],
  ['10,123', 'pt-BR'],
  ['0,00', 'pt-BR'],
  ['123456789012345678,00', 'pt-BR'],
])('rejeita entrada inválida ou ambígua "%s" em %s', (entrada, locale) => {
  expect(normalizarEntradaMonetaria(entrada, locale).valida).toBe(false)
})

test('formata BRL/USD com arredondamento HALF_EVEN e preserva magnitudes grandes', () => {
  expect(formatarMonetario('2.345', 'BRL', 'pt-BR')).toBe('R$\u00a02,34')
  expect(formatarMonetario('2.355', 'BRL', 'pt-BR')).toBe('R$\u00a02,36')
  expect(formatarMonetario('123456789012345678.99', 'USD', 'en-US'))
    .toBe('$123,456,789,012,345,678.99')
})

test('formata percentuais e cotações sem reduzir a escala decimal', () => {
  expect(formatarPercentual('0.0100000000', 'pt-BR')).toBe('1,00000000%')
  expect(formatarPercentual('0.0150000000', 'en-US')).toBe('1.50000000%')
  expect(formatarCotacao('1234.5000', 'pt-BR')).toBe('1.234,5000')
  expect(formatarCotacao('1234.5000', 'en-US')).toBe('1,234.5000')
})
