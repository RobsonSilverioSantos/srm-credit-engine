import { afterEach, expect, test, vi } from 'vitest'
import { ErroOperacao } from '../src/aplicacao/operacoes'
import { bff } from '../src/infraestrutura/bff'

afterEach(() => vi.unstubAllGlobals())

test('envia simulação ao BFF com bearer, strings decimais e sem chave', async () => {
  const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
    moeda: 'BRL', valorPresenteBrl: '10.00', desagioBrl: '0.00', valorPagamento: '10.00',
    taxaBaseMensal: '0.01', spreadMensal: '0.02', cotacaoBrlPorUsd: null, referencia: '2026-11-20T12:00:00Z',
    cotacaoValidaAte: null,
  }), { status: 200 }))
  vi.stubGlobal('fetch', fetchMock)
  const entrada = { modalidade: 'DUPLICATA_MERCANTIL', valorFaceBrl: '10.00', prazoMeses: 1, moeda: 'BRL' } as const
  const signal = new AbortController().signal
  await bff.simular('token', entrada, signal)
  expect(fetchMock).toHaveBeenCalledWith('/bff/v1/simulacoes', {
    method: 'POST', headers: { Authorization: 'Bearer token', 'Content-Type': 'application/json' },
    body: JSON.stringify(entrada), signal,
  })
})

test('não considera respostas incompletas como sucesso de cadastro ou liquidação', async () => {
  vi.stubGlobal('fetch', vi.fn().mockImplementation(() => Promise.resolve(new Response('{}', { status: 201 }))))
  await expect(bff.cadastrarRecebivel('token', {
    cedenteId: 'id', modalidade: 'DUPLICATA_MERCANTIL', valorFaceBrl: '10.00', prazoMeses: 1, vencimento: null,
  })).rejects.toThrow('Resposta inválida')
  await expect(bff.liquidar('token', 'id', 'BRL', 'chave')).rejects.toThrow('Resposta inválida')
})

test('envia a chave idempotente e informa conflito HTTP', async () => {
  const fetchMock = vi.fn().mockResolvedValue(new Response('', { status: 409 }))
  vi.stubGlobal('fetch', fetchMock)
  await expect(bff.liquidar('token', 'recebivel', 'USD', 'chave-1')).rejects.toMatchObject({
    status: 409, message: expect.stringContaining('conflita'),
  } satisfies Partial<ErroOperacao>)
  expect(fetchMock).toHaveBeenCalledWith('/bff/v1/recebiveis/recebivel/liquidacoes', {
    method: 'POST', headers: { Authorization: 'Bearer token', 'Content-Type': 'application/json', 'Idempotency-Key': 'chave-1' },
    body: '{"moeda":"USD"}',
  })
})

test('autentica no endpoint próprio e trata 401 e indisponibilidade de rede', async () => {
  const fetchMock = vi.fn()
    .mockResolvedValueOnce(new Response(JSON.stringify({ accessToken: 'jwt', expiresIn: 60 }), { status: 200 }))
    .mockResolvedValueOnce(new Response('', { status: 401 }))
    .mockRejectedValueOnce(new TypeError('network'))
  vi.stubGlobal('fetch', fetchMock)
  const sessao = await bff.autenticar('a@example.com', 'senha')
  expect(sessao.token).toBe('jwt')
  expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/v1/auth/token')
  await expect(bff.listarCedentes('jwt')).rejects.toMatchObject({ status: 401 })
  await expect(bff.listarCedentes('jwt')).rejects.toThrow('Não foi possível conectar')
})
