import { expect, test } from 'vitest'
import { formatarInstante } from '../src/dominio/formatacaoTemporal'

test('localiza instante UTC em Sao Paulo sem alterar o ISO de origem', () => {
  const iso = '2026-11-21T21:15:58.766998Z'
  expect(formatarInstante(iso, 'pt-BR', 'America/Sao_Paulo')).toBe('21/11/2026 18:15:58')
  expect(iso).toBe('2026-11-21T21:15:58.766998Z')
})

test('localiza o mesmo instante em outro fuso e locale', () => {
  expect(formatarInstante('2026-11-21T21:15:58.766998Z', 'en-US', 'America/New_York'))
    .toBe('11/21/2026 16:15:58')
})

test('rejeita data que não representa instante válido', () => {
  expect(() => formatarInstante('2026-02-30T12:00:00Z', 'pt-BR', 'UTC')).toThrow()
})
