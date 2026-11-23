import { describe, expect, test } from 'vitest'
import { caixaAscii, editarCnpj, erroDocumento, formatarCnpj } from '../src/dominio/documentoCedente'

describe('sintaxe de documentos', () => {
  test('formata somente máscara CNPJ e não remove colagens malformadas', () => {
    expect(formatarCnpj('12abc34501de35', true)).toBe('12.ABC.345/01DE-35')
    expect(formatarCnpj('12.ABC.345/01DE-35', true)).toBe('12.ABC.345/01DE-35')
    for (const invalido of ['12.ABC34501DE35', '12 ABC34501DE35', '12@ABC34501DE35', '12.ABC.345/01DE-3A']) {
      expect(formatarCnpj(invalido, true)).toBe(invalido)
      expect(erroDocumento('BR', 'CNPJ', invalido)).not.toBe('')
    }
  })

  test('identificador estrangeiro aceita apenas ASCII e não infere validade ISO', () => {
    expect(caixaAscii('abß１２')).toBe('ABß１２')
    expect(erroDocumento('US', 'OUTRO', 'A'.repeat(32))).toBe('')
    expect(erroDocumento('ZZ', 'OUTRO', 'ABC123')).toBe('')
    for (const invalido of ['', 'A'.repeat(33), 'AB 1', 'AB-1', 'AB\n', 'ÁBC']) {
      expect(erroDocumento('US', 'OUTRO', invalido)).not.toBe('')
    }
    expect(erroDocumento('B1', 'OUTRO', 'ABC')).not.toBe('')
  })

  test('impede combinações país/tipo não suportadas', () => {
    expect(erroDocumento('BR', 'OUTRO', 'ABC')).toMatch(/BR.*CNPJ/)
    expect(erroDocumento('US', 'CNPJ', '12.ABC.345/01DE-35')).toMatch(/estrangeiro.*Outro/)
  })

  test('reconstrói máscara ao apagar ou substituir sem remover caracteres colados inválidos', () => {
    expect(editarCnpj('12.ABC.345/01DE-35', '12.AC.345/01DE-35', 4, 'deleteContentBackward', false, false))
      .toEqual({ valor: '12.AC3.450/1DE3-5', cursor: 4 })
    expect(editarCnpj('12.ABC.345/01DE-35', '12ABC.345/01DE-35', 2, 'deleteContentBackward', false, false))
      .toEqual({ valor: '1A.BC3.450/1DE3-5', cursor: 1 })
    expect(editarCnpj('12.ABC.345/01DE-35', '12.A@BC.345/01DE-35', 5, 'insertText', false, false))
      .toEqual({ valor: '12.A@BC.345/01DE-35', cursor: 5 })
    expect(editarCnpj('', '12.ABC34501DE35', 15, 'insertFromPaste', true, false).valor)
      .toBe('12.ABC34501DE35')
  })
})
