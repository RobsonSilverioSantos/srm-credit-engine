import { expect, test } from 'vitest'
import {
  formatarCotacao,
  formatarEntradaMonetaria,
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

test.each([
  ['25000.00', 'pt-BR', '25.000,00'],
  ['25000.00', 'en-US', '25,000.00'],
  ['12345678901234567.89', 'en-US', '12,345,678,901,234,567.89'],
])('formata entrada editável %s em %s sem ponto flutuante', (valor, locale, esperado) => {
  expect(formatarEntradaMonetaria(valor, locale)).toBe(esperado)
})

test('normaliza agrupamento e dígitos de outras localidades do navegador', () => {
  expect(normalizarEntradaMonetaria('25\u202f000,75', 'fr-FR'))
    .toEqual({ valida: true, valorCanonico: '25000.75' })

  const indiano = formatarEntradaMonetaria('12345678901234567.89', 'hi-IN')
  expect(normalizarEntradaMonetaria(indiano, 'hi-IN'))
    .toEqual({ valida: true, valorCanonico: '12345678901234567.89' })

  const arabe = formatarEntradaMonetaria('25000.75', 'ar-EG')
  expect(normalizarEntradaMonetaria(arabe, 'ar-EG'))
    .toEqual({ valida: true, valorCanonico: '25000.75' })
})

test('formata percentuais sem arredondar a escala original', () => {
  expect(formatarPercentual('0.0100000000', 'pt-BR')).toBe('1,00000000%')
  expect(formatarPercentual('0.0150000000', 'en-US')).toBe('1.50000000%')
})

test.each([
  ['5.4321000000', 'pt-BR', '5,4321'],
  ['5.4321000000', 'en-US', '5.4321'],
  ['1234.5000', 'pt-BR', '1.234,5000'],
  ['1234.5000', 'en-US', '1,234.5000'],
  ['5', 'pt-BR', '5,0000'],
  ['5.4321230000', 'pt-BR', '5,432123'],
  ['5.0000000009', 'pt-BR', '5,0000000009'],
  ['123456789012345678.1234567890', 'en-US', '123,456,789,012,345,678.123456789'],
])('formata cotacao %s em %s sem zeros residuais nem perda de precisao', (valor, locale, esperado) => {
  expect(formatarCotacao(valor, locale)).toBe(esperado)
})

test('rejeita cotacao sem representacao decimal canonica', () => {
  expect(() => formatarCotacao('5,4321', 'pt-BR')).toThrow('Valor decimal canônico inválido.')
})
