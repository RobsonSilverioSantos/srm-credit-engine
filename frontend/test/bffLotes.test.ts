import { afterEach, expect, test, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import { bffLotes } from '../src/infraestrutura/bffLotes'
import { dadosLote } from './fixtures/lotes'

afterEach(() => vi.unstubAllGlobals())

function responder(corpo: unknown, status = 200) {
  return vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(corpo), {
    status, headers: { 'Content-Type': 'application/json' },
  })))
}

test('upload multipart usa parte arquivo e Bearer sem Content-Type manual nem organização arbitrária', async () => {
  responder(dadosLote().previa)
  const arquivo = new File(['arquivo'], 'lote.xlsx')
  await bffLotes.prevalidar('jwt', arquivo)
  const [url, opcoes] = vi.mocked(fetch).mock.calls[0]!
  expect(url).toBe('/bff/v1/lotes/recebiveis/validacoes')
  expect(opcoes?.headers).toEqual({ Authorization: 'Bearer jwt' })
  expect(opcoes?.body).toBeInstanceOf(FormData)
  expect((opcoes?.body as FormData).get('arquivo')).toBe(arquivo)
})

test('cadastro B16 envia apenas itens canônicos e chave independente', async () => {
  const dados = dadosLote()
  responder(dados.cadastro, 201)
  const itens = dados.previa.itens.map((item) => item.comando!)
  await bffLotes.cadastrar('jwt', { itens }, 'chave-cadastro')
  expect(fetch).toHaveBeenCalledWith('/bff/v1/lotes/recebiveis', expect.objectContaining({
    method: 'POST', headers: { Authorization: 'Bearer jwt', 'Content-Type': 'application/json', 'Idempotency-Key': 'chave-cadastro' },
    body: JSON.stringify({ itens }),
  }))
})

test.each([
  '00000000000191', '00.000.000/0001-91', '12ABC34501DE35', '12.abc.345/01de-35',
])('CSV transmite documento %s literalmente no arquivo, nunca na URL', async (documento) => {
  const modelo = readFileSync('public/modelos/modelo-lote-cnpj.csv', 'utf8')
  const conteudo = modelo.replace('CEDENTE_CNPJ_AQUI', documento)
  responder(dadosLote().previa)
  const arquivo = new File([conteudo], 'lote.csv', { type: 'text/csv' })
  await bffLotes.prevalidar('jwt', arquivo)
  const [url, opcoes] = vi.mocked(fetch).mock.calls[0]!
  expect(url).toBe('/bff/v1/lotes/recebiveis/validacoes')
  const enviado = (opcoes!.body as FormData).get('arquivo') as File
  const texto = await new Promise((resolve, reject) => {
    const leitor = new FileReader()
    leitor.onload = () => resolve(leitor.result)
    leitor.onerror = () => reject(leitor.error)
    leitor.readAsText(enviado)
  })
  expect(texto).toBe(conteudo)
  expect(opcoes!.headers).toEqual({ Authorization: 'Bearer jwt' })
})

test.each(['modelo-lote-cnpj.xlsx', 'modelo-lote.xlsx'])('XLSX publicado %s é enviado sem transformação', async (nome) => {
  responder(dadosLote().previa)
  const bytes = new Uint8Array(readFileSync(`public/modelos/${nome}`))
  const arquivo = new File([bytes], nome)
  await bffLotes.prevalidar('jwt', arquivo)
  const [, opcoes] = vi.mocked(fetch).mock.calls[0]!
  const enviado = (opcoes!.body as FormData).get('arquivo') as File
  const resultado = await new Promise<ArrayBuffer>((resolve, reject) => {
    const leitor = new FileReader()
    leitor.onload = () => resolve(leitor.result as ArrayBuffer)
    leitor.onerror = () => reject(leitor.error)
    leitor.readAsArrayBuffer(enviado)
  })
  expect(new Uint8Array(resultado)).toEqual(bytes)
})

test('prévia B17 usa todos os itens sem chave, liquidação usa mesmo hash e confirmação explícita', async () => {
  const dados = dadosLote()
  const itens = dados.financeira.itens.map(({ recebivelId, moedaPagamento }) => ({ recebivelId, moedaPagamento }))
  responder(dados.financeira)
  await bffLotes.previsualizar('jwt', 'lote-a', itens)
  expect(fetch).toHaveBeenCalledWith('/bff/v1/lotes/lote-a/previas-liquidacao', expect.objectContaining({
    body: JSON.stringify({ itens }), headers: { Authorization: 'Bearer jwt', 'Content-Type': 'application/json' },
  }))
  responder(dados.liquidacao, 201)
  const comando = { itens, hashPrevia: dados.financeira.hashPrevia!, confirmacaoFinanceira: true as const }
  await bffLotes.liquidar('jwt', 'lote-a', comando, 'chave-financeira')
  expect(fetch).toHaveBeenCalledWith('/bff/v1/lotes/lote-a/liquidacoes', expect.objectContaining({
    body: JSON.stringify(comando), headers: {
      Authorization: 'Bearer jwt', 'Content-Type': 'application/json', 'Idempotency-Key': 'chave-financeira',
    },
  }))
})

test.each([400, 401, 403, 404, 409, 413, 422, 503])('expõe status HTTP %s sem sucesso fictício, inclusive corpo não JSON', async (status) => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('erro do proxy', { status })))
  await expect(bffLotes.prevalidar('jwt', new File(['a'], 'a.csv'))).rejects.toMatchObject({ status })
})

test('422 preserva erros por linha/campo para correção', async () => {
  const dados = dadosLote()
  dados.previa.itens[0]!.status = 'INVALIDO'
  dados.previa.itens[0]!.erros = [{ campo: 'cedenteId', mensagem: 'Cedente indisponível.' }]
  responder({ codigo: 'LOTE_INVALIDO', mensagem: 'Lote inválido', itens: dados.previa.itens }, 422)
  await expect(bffLotes.cadastrar('jwt', { itens: [] }, 'chave')).rejects.toMatchObject({
    status: 422, itens: dados.previa.itens,
  })
})

test('conexão perdida e resposta financeira malformada não simulam sucesso', async () => {
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
  await expect(bffLotes.prevalidar('jwt', new File(['a'], 'a.csv'))).rejects.toThrow('incerto')
  responder({ loteId: 'lote-a', itens: [] })
  await expect(bffLotes.liquidar('jwt', 'lote-a', { itens: [], hashPrevia: '', confirmacaoFinanceira: true }, 'chave'))
    .rejects.toThrow('incompleta')
})

test('rejeita números binários no lugar de strings decimais na prévia', async () => {
  const dados = dadosLote()
  responder({
    ...dados.financeira,
    itens: dados.financeira.itens.map((item) => ({ ...item, valorPagamento: 92859.94 })),
  })
  await expect(bffLotes.previsualizar('jwt', 'lote-a', [])).rejects.toThrow('incompleta')
})
