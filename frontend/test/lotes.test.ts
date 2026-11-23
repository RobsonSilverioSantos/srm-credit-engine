import { expect, test } from 'vitest'
import { ErroLote, FluxoLote } from '../src/aplicacao/lotes'
import { ErroOperacao } from '../src/aplicacao/operacoes'
import { criarPortaLotes, dadosLote, dadosLoteMisto } from './fixtures/lotes'

const arquivo = () => new File(['conteudo validado pelo servidor'], 'lote.csv')

async function cadastrado(quantidade = 2) {
  const porta = criarPortaLotes(quantidade)
  let sequencia = 0
  const fluxo = new FluxoLote(porta, () => `chave-${++sequencia}`)
  await fluxo.prevalidar('jwt', arquivo())
  await fluxo.cadastrar('jwt')
  return { porta, fluxo }
}

test('pré-valida sem escrita e cadastro com aviso financeiro não liquida nem solicita prévia B17', async () => {
  const porta = criarPortaLotes()
  const dados = dadosLote()
  dados.previa.itens[0]!.simulacao = null
  dados.previa.itens[0]!.avisoSimulacao = 'Câmbio indisponível'
  porta.prevalidar.mockResolvedValue(dados.previa)
  const fluxo = new FluxoLote(porta)
  await fluxo.prevalidar('jwt', arquivo())
  expect(porta.cadastrar).not.toHaveBeenCalled()
  await fluxo.cadastrar('jwt')
  expect(porta.previsualizar).not.toHaveBeenCalled()
  expect(porta.liquidar).not.toHaveBeenCalled()
  expect(fluxo.snapshot().cadastro?.cadastrados).toBe(2)
})

test('duplo clique e perda de resposta do cadastro preservam chave/comando e bloqueiam substituição', async () => {
  const porta = criarPortaLotes()
  const fluxo = new FluxoLote(porta, () => 'cadastro-estavel')
  await fluxo.prevalidar('jwt', arquivo())
  let rejeitar!: (erro: Error) => void
  porta.cadastrar.mockImplementationOnce(() => new Promise((_, rejeicao) => { rejeitar = rejeicao }))
  const primeiro = fluxo.cadastrar('jwt')
  await fluxo.cadastrar('jwt')
  expect(porta.cadastrar).toHaveBeenCalledTimes(1)
  rejeitar(new ErroLote('Rede interrompida'))
  await expect(primeiro).rejects.toThrow('Rede')
  expect(() => fluxo.novo()).toThrow('pendente')
  await expect(fluxo.prevalidar('jwt', arquivo())).rejects.toThrow('cadastro atual')
  await fluxo.cadastrar('jwt-novo')
  expect(porta.cadastrar.mock.calls[0]!.slice(1)).toEqual(porta.cadastrar.mock.calls[1]!.slice(1))
  expect(fluxo.snapshot().cadastroPendente).toBeNull()
})

test.each(['JA_CADASTRADO', 'JA_LIQUIDADO', 'INVALIDO'] as const)('não cadastra linha %s mesmo com flag confirmavel inconsistente', async (status) => {
  const porta = criarPortaLotes()
  const dados = dadosLote()
  dados.previa.itens[0]!.status = status
  porta.prevalidar.mockResolvedValue(dados.previa)
  const fluxo = new FluxoLote(porta)
  await fluxo.prevalidar('jwt', arquivo())
  await expect(fluxo.cadastrar('jwt')).rejects.toThrow('todas as linhas')
  expect(porta.cadastrar).not.toHaveBeenCalled()
})

test('erro 422 na confirmação mantém relatório atualizado e exige arquivo corrigido', async () => {
  const porta = criarPortaLotes(200)
  const dados = dadosLote(200)
  dados.previa.itens[0]!.status = 'JA_LIQUIDADO'
  porta.cadastrar.mockRejectedValueOnce(new ErroLote('Lote inválido', 422, [dados.previa.itens[0]!]))
  const fluxo = new FluxoLote(porta)
  await fluxo.prevalidar('jwt', arquivo())
  await expect(fluxo.cadastrar('jwt')).rejects.toThrow('Lote inválido')
  expect(fluxo.snapshot().previaCadastro?.itens[0]!.status).toBe('JA_LIQUIDADO')
  expect(fluxo.snapshot().previaCadastro?.itens).toHaveLength(200)
  expect(fluxo.snapshot().previaCadastro?.itens[199]!.comando?.referenciaExterna).toBe('REF-199')
  expect(fluxo.snapshot().cadastroPendente).toBeNull()
  await expect(fluxo.cadastrar('jwt')).rejects.toThrow('todas as linhas')
})

test.each(['apto', 'hash', 'faltante', 'duplicado', 'estranho', 'moeda', 'simulacao', 'estado', 'totais'] as const)(
  'gate financeiro rejeita prévia de 200 inválida: %s', async (falha) => {
    const { porta, fluxo } = await cadastrado(200)
    const previa = dadosLote(200).financeira
    switch (falha) {
      case 'apto': previa.aptoParaLiquidar = false; break
      case 'hash': previa.hashPrevia = null; break
      case 'faltante': previa.itens.pop(); break
      case 'duplicado': previa.itens[1] = { ...previa.itens[0]! }; break
      case 'estranho': previa.itens[0]!.recebivelId = 'outro'; break
      case 'moeda': previa.itens[0]!.moedaPagamento = 'USD'; break
      case 'simulacao': previa.itens[0]!.valorPagamento = null; break
      case 'estado': previa.itens[0]!.estado = 'LIQUIDADO'; break
      case 'totais': previa.totaisPorMoeda = {}; break
    }
    porta.previsualizar.mockResolvedValue(previa)
    await fluxo.previsualizar('jwt')
    await expect(fluxo.liquidar('jwt')).rejects.toThrow('100%')
    expect(porta.liquidar).not.toHaveBeenCalled()
  },
)

test('confirma os 200 itens com mesmo hash e chave própria, independente do cadastro', async () => {
  const { porta, fluxo } = await cadastrado(200)
  await fluxo.previsualizar('jwt')
  expect(porta.liquidar).not.toHaveBeenCalled()
  await fluxo.liquidar('jwt')
  const [, lote, comando, chave] = porta.liquidar.mock.calls[0]!
  expect(lote).toBe('lote-a')
  expect(comando.itens).toHaveLength(200)
  expect(comando).toMatchObject({ hashPrevia: 'a'.repeat(64), confirmacaoFinanceira: true })
  expect(chave).not.toBe(porta.cadastrar.mock.calls[0]![2])
  expect(fluxo.snapshot().resultado?.itens).toHaveLength(200)
})

test.each([undefined, 401, 503])('liquidação incerta/HTTP %s preserva intenção e repete somente o mesmo comando', async (status) => {
  const { porta, fluxo } = await cadastrado()
  await fluxo.previsualizar('jwt')
  porta.liquidar.mockRejectedValueOnce(new ErroOperacao('Indisponível', status))
  await expect(fluxo.liquidar('jwt')).rejects.toThrow()
  expect(fluxo.snapshot().resultado).toBeNull()
  expect(() => fluxo.novo()).toThrow('pendente')
  await expect(fluxo.previsualizar('jwt')).rejects.toThrow('pendente')
  await fluxo.liquidar('jwt-novo')
  expect(porta.liquidar.mock.calls[0]!.slice(1)).toEqual(porta.liquidar.mock.calls[1]!.slice(1))
  expect(fluxo.snapshot().resultado).not.toBeNull()
})

test('conflito financeiro exige nova prévia e ato explícito com nova chave; não cria sucesso parcial', async () => {
  const { porta, fluxo } = await cadastrado()
  await fluxo.previsualizar('jwt')
  porta.liquidar.mockRejectedValueOnce(new ErroLote('Rollback integral', 409))
  await expect(fluxo.liquidar('jwt')).rejects.toThrow('Rollback')
  expect(fluxo.snapshot().previaFinanceira).toBeNull()
  expect(fluxo.snapshot().resultado).toBeNull()
  await expect(fluxo.liquidar('jwt')).rejects.toThrow('100%')
  expect(porta.liquidar).toHaveBeenCalledTimes(1)
  await fluxo.previsualizar('jwt')
  expect(porta.liquidar).toHaveBeenCalledTimes(1)
  await fluxo.liquidar('jwt')
  expect(porta.liquidar.mock.calls[0]![3]).not.toBe(porta.liquidar.mock.calls[1]![3])
})

test('resposta financeira parcial 2xx não autoriza sucesso e mantém retry idempotente', async () => {
  const { porta, fluxo } = await cadastrado()
  await fluxo.previsualizar('jwt')
  const resultado = dadosLote().liquidacao
  resultado.itens.pop()
  porta.liquidar.mockResolvedValueOnce(resultado)
  await expect(fluxo.liquidar('jwt')).rejects.toThrow('incompleta')
  expect(fluxo.snapshot().resultado).toBeNull()
  expect(fluxo.snapshot().liquidacaoPendente).not.toBeNull()
  await fluxo.liquidar('jwt')
  expect(porta.liquidar.mock.calls[0]).toEqual(porta.liquidar.mock.calls[1])
})

test('duplo clique financeiro produz uma chamada enquanto a primeira está pendente', async () => {
  const { porta, fluxo } = await cadastrado()
  await fluxo.previsualizar('jwt')
  let concluir!: (valor: ReturnType<typeof dadosLote>['liquidacao']) => void
  porta.liquidar.mockImplementationOnce(() => new Promise((resolve) => { concluir = resolve }))
  const primeira = fluxo.liquidar('jwt')
  await fluxo.liquidar('jwt')
  expect(porta.liquidar).toHaveBeenCalledTimes(1)
  concluir(dadosLote().liquidacao)
  await primeira
})

test('resposta de cadastro parcial é incerta, sem derivar itens financeiros incompletos', async () => {
  const porta = criarPortaLotes()
  const dados = dadosLote()
  dados.cadastro.itens.pop()
  porta.cadastrar.mockResolvedValueOnce(dados.cadastro)
  const fluxo = new FluxoLote(porta)
  await fluxo.prevalidar('jwt', arquivo())
  await expect(fluxo.cadastrar('jwt')).rejects.toThrow('incompleta')
  expect(fluxo.snapshot().cadastro).toBeNull()
  expect(fluxo.snapshot().cadastroPendente).not.toBeNull()
})

test('descarta prévia anterior ao rejeitar novo arquivo inválido', async () => {
  const porta = criarPortaLotes()
  const fluxo = new FluxoLote(porta)
  await fluxo.prevalidar('jwt', arquivo())
  await expect(fluxo.prevalidar('jwt', new File(['inválido'], 'macro.xlsm'))).rejects.toThrow('CSV ou XLSX')
  expect(fluxo.snapshot().previaCadastro).toBeNull()
  expect(porta.prevalidar).toHaveBeenCalledTimes(1)
})

test('câmbio vencido impede nova liquidação, mas não impede replay do comando incerto', async () => {
  const porta = criarPortaLotes()
  const dados = dadosLoteMisto()
  porta.prevalidar.mockResolvedValue(dados.previa)
  porta.previsualizar.mockResolvedValue(dados.financeira)
  porta.liquidar.mockResolvedValue(dados.liquidacao)
  const fluxo = new FluxoLote(porta)
  await fluxo.prevalidar('jwt', arquivo())
  await fluxo.cadastrar('jwt')
  await fluxo.previsualizar('jwt')
  dados.financeira.itens[1]!.cotacaoValidaAte = '2000-01-01T00:00:00Z'
  await expect(fluxo.liquidar('jwt')).rejects.toThrow('100%')
  expect(porta.liquidar).not.toHaveBeenCalled()
  dados.financeira.itens[1]!.cotacaoValidaAte = new Date(Date.now() + 60000).toISOString()
  porta.liquidar.mockRejectedValueOnce(new ErroLote('Resposta perdida'))
  await expect(fluxo.liquidar('jwt')).rejects.toThrow('perdida')
  dados.financeira.itens[1]!.cotacaoValidaAte = '2000-01-01T00:00:00Z'
  await fluxo.liquidar('jwt')
  expect(porta.liquidar.mock.calls[0]).toEqual(porta.liquidar.mock.calls[1])
  expect(fluxo.snapshot().resultado?.itens[1]!.moedaPagamento).toBe('USD')
})
