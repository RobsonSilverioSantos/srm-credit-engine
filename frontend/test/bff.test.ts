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

test('preserva a revisao financeira no corpo e na retentativa idempotente', async () => {
  const fetchMock = vi.fn().mockResolvedValue(new Response('', { status: 409 }))
  vi.stubGlobal('fetch', fetchMock)
  const revisao = 'a'.repeat(64)
  await expect(bff.liquidar('token', 'recebivel', 'USD', 'chave', revisao)).rejects.toMatchObject({ status: 409 })
  expect(fetchMock.mock.calls[0]?.[1].body).toBe(JSON.stringify({ moeda: 'USD', revisaoCambial: revisao }))
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

test('contrato de cedente B14 envia somente campos autorizados e preserva legado', async () => {
  const legado = { id: 'legado', nome: 'Antigo', documento: '123', ativo: true, pais: null, tipoDocumento: null, documentoCanonico: null, statusDocumento: 'DESCONHECIDO' }
  const novo = { id: 'novo', nome: 'Novo', documento: 'AB123', ativo: true, pais: 'US', tipoDocumento: 'OUTRO', documentoCanonico: 'AB123', statusDocumento: 'NAO_VERIFICADO' }
  const fetchMock = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify(novo), { status: 201 }))
    .mockResolvedValueOnce(new Response(JSON.stringify([legado, novo]), { status: 200 }))
  vi.stubGlobal('fetch', fetchMock)
  const entrada = { nome: 'Novo', documento: 'AB123', pais: 'US', tipoDocumento: 'OUTRO' as const, organizacaoId: 'nao-enviar' }
  expect(await bff.cadastrarCedente('jwt', entrada)).toEqual(novo)
  expect(fetchMock.mock.calls[0]).toEqual(['/bff/v1/cedentes', {
    method: 'POST', headers: { Authorization: 'Bearer jwt', 'Content-Type': 'application/json' },
    body: JSON.stringify({ nome: 'Novo', documento: 'AB123', pais: 'US', tipoDocumento: 'OUTRO' }),
  }])
  expect(await bff.listarCedentes('jwt')).toEqual([legado, novo])
})

test('rejeita resposta B14 incompleta e propaga erros 400 e 409', async () => {
  const fetchMock = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ id: '1', nome: 'X', documento: '123', ativo: true }), { status: 201 }))
    .mockResolvedValueOnce(new Response('', { status: 400 }))
    .mockResolvedValueOnce(new Response('', { status: 409 }))
  vi.stubGlobal('fetch', fetchMock)
  const entrada = { nome: 'X', documento: '123', pais: 'US', tipoDocumento: 'OUTRO' } as const
  await expect(bff.cadastrarCedente('jwt', entrada)).rejects.toThrow('Resposta inválida')
  await expect(bff.cadastrarCedente('jwt', entrada)).rejects.toMatchObject({ status: 400 })
  await expect(bff.cadastrarCedente('jwt', entrada)).rejects.toMatchObject({ status: 409 })
})
