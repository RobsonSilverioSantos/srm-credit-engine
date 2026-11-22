import { afterEach, expect, test, vi } from 'vitest'
import { bffCambio } from '../src/infraestrutura/bffCambio'
import { cotacaoManual, selecaoManual } from './fixtures/cambio'

afterEach(() => vi.unstubAllGlobals())
const sinal = () => new AbortController().signal
function resposta(corpo: unknown, status = 200) {
  const fetchMock = vi.fn().mockImplementation(async () => new Response(JSON.stringify(corpo), { status }))
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

test('cadastro envia somente taxa string e justificativa, sem ativar e sem campos do servidor', async () => {
  const fetchMock = resposta(cotacaoManual, 201)
  await expect(bffCambio.cadastrar('jwt-memoria', { taxaBrlPorUsd: '5.4321', justificativa: 'motivo' })).resolves.toEqual(cotacaoManual)
  expect(fetchMock).toHaveBeenCalledExactlyOnceWith('/bff/v1/cotacoes-cambiais/manuais', {
    method: 'POST', headers: { Authorization: 'Bearer jwt-memoria', 'Content-Type': 'application/json' },
    body: '{"taxaBrlPorUsd":"5.4321","justificativa":"motivo"}',
  })
})

test('seleções explícitas MANUAL e PTAX preservam contrato, sem DEMO e sem cotacaoId na PTAX', async () => {
  const fetchMock = resposta(selecaoManual, 201)
  await bffCambio.selecionar('jwt', { fonte: 'MANUAL', cotacaoId: cotacaoManual.id, justificativa: 'autorizar' })
  await bffCambio.selecionar('jwt', { fonte: 'BCB_PTAX', justificativa: 'retorno' })
  expect(fetchMock.mock.calls.map((chamada) => JSON.parse(chamada[1].body))).toEqual([
    { fonte: 'MANUAL', cotacaoId: cotacaoManual.id, justificativa: 'autorizar' },
    { fonte: 'BCB_PTAX', justificativa: 'retorno' },
  ])
})

test('históricos paginam no servidor e filtram apenas pela fonte implementada', async () => {
  const fetchMock = resposta({ itens: [cotacaoManual], pagina: 2, tamanho: 10, total: 25 })
  const signal = sinal()
  await expect(bffCambio.historico('jwt', { pagina: 2, tamanho: 10, fonte: 'MANUAL' }, signal)).resolves.toMatchObject({ total: 25 })
  expect(fetchMock.mock.calls[0]).toEqual(['/bff/v1/cotacoes-cambiais?pagina=2&tamanho=10&fonte=MANUAL', {
    signal, headers: { Authorization: 'Bearer jwt' },
  }])
  fetchMock.mockResolvedValue(new Response(JSON.stringify({ itens: [selecaoManual], pagina: 0, tamanho: 20, total: 1 })))
  await bffCambio.selecoes('jwt', { pagina: 0, tamanho: 20 }, signal)
  expect(fetchMock.mock.calls[1]?.[0]).toBe('/bff/v1/cotacoes-cambiais/selecoes?pagina=0&tamanho=20')
})

test('sincronização não envia body ou altera seleção', async () => {
  const fetchMock = resposta({ versoesIncluidas: 0 })
  await expect(bffCambio.sincronizar('jwt')).resolves.toEqual({ versoesIncluidas: 0 })
  expect(fetchMock).toHaveBeenCalledExactlyOnceWith('/bff/v1/cotacoes-cambiais/ptax/sincronizacoes', {
    method: 'POST', headers: { Authorization: 'Bearer jwt' },
  })
})

test.each([400, 401, 403, 409, 503])('HTTP %s tem erro explícito e nenhum retry de escrita', async (status) => {
  const fetchMock = resposta({ detalhe: 'não deve ser exposto' }, status)
  await expect(bffCambio.cadastrar('jwt', { taxaBrlPorUsd: '5.4', justificativa: 'motivo' })).rejects.toMatchObject({
    name: 'ErroCambio', status, resultadoIncerto: status >= 500,
  })
  expect(fetchMock).toHaveBeenCalledTimes(1)
})

test.each(['rede', 'json', 'contrato'])('falha de %s após POST não autoriza repetir a escrita', async (tipo) => {
  const fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
  if (tipo === 'rede') fetchMock.mockRejectedValue(new TypeError('network'))
  else fetchMock.mockResolvedValue(new Response(tipo === 'json' ? 'invalido' : '{"id":"parcial"}'))
  await expect(bffCambio.cadastrar('jwt', { taxaBrlPorUsd: '5.4', justificativa: 'motivo' })).rejects.toMatchObject({ resultadoIncerto: true })
  expect(fetchMock).toHaveBeenCalledTimes(1)
})

test('resposta de histórico inválida é erro de leitura recuperável', async () => {
  resposta({ itens: [{ ...cotacaoManual, taxaBrlPorUsd: 5.4321 }], pagina: 0, tamanho: 20, total: 1 })
  await expect(bffCambio.historico('jwt', { pagina: 0, tamanho: 20 }, sinal())).rejects.toMatchObject({
    message: 'Resposta inválida do servidor.', resultadoIncerto: false,
  })
})

test('consulta abortada preserva AbortError', async () => {
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new DOMException('cancelada', 'AbortError')))
  await expect(bffCambio.selecoes('jwt', { pagina: 0, tamanho: 20 }, sinal())).rejects.toMatchObject({ name: 'AbortError' })
})
