import { ThemeProvider } from '@mui/material/styles'
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, test, vi } from 'vitest'
import { ErroLote, type PortaLotes } from '../src/aplicacao/lotes'
import { bffLotes } from '../src/infraestrutura/bffLotes'
import { mensagemErro } from '../src/aplicacao/operacoes'
import { PaginaImportacaoLote } from '../src/apresentacao/PaginaImportacaoLote'
import { criarPortaLotes, dadosLote, dadosLoteMisto } from './fixtures/lotes'
import { tema } from '../src/tema/tema'

afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals() })

function iniciar(porta: PortaLotes = criarPortaLotes(), token: string | null = 'jwt') {
  const props = {
    porta, token, locale: 'pt-BR', fusoHorario: 'America/Sao_Paulo',
    tratarErro: vi.fn(mensagemErro), aoAtualizarOperacaoEmCurso: vi.fn(), aoAtualizarOperacaoPendente: vi.fn(),
  }
  const tela = render(<ThemeProvider theme={tema}><PaginaImportacaoLote {...props} /></ThemeProvider>)
  return {
    porta, props,
    user: userEvent.setup({ applyAccept: false }),
    rerender: (novoToken: string | null) =>
      tela.rerender(<ThemeProvider theme={tema}><PaginaImportacaoLote {...props} token={novoToken} /></ThemeProvider>),
  }
}

async function prevalidar(user: ReturnType<typeof userEvent.setup>, nome = 'lote.csv') {
  await user.upload(screen.getByLabelText('Arquivo CSV ou XLSX'), new File(['conteúdo do servidor'], nome))
  await user.click(screen.getByRole('button', { name: 'Pré-validar arquivo' }))
  await screen.findByRole('table', { name: 'Pré-validação de todas as linhas' })
}

async function cadastrar(user: ReturnType<typeof userEvent.setup>) {
  await prevalidar(user)
  await user.click(screen.getByRole('button', { name: 'Conferir cadastro' }))
  await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirmar cadastro' }))
  await screen.findByRole('table', { name: 'Recebíveis cadastrados para auditoria' })
}

test('200/200: todos os títulos, dois modais independentes, Escape/cancelamento e resultado integral', async () => {
  const { porta, user } = iniciar(criarPortaLotes(200))
  await prevalidar(user, 'lote.xlsx')
  expect(within(screen.getByRole('table', { name: 'Pré-validação de todas as linhas' })).getAllByRole('row')).toHaveLength(201)
  expect(porta.cadastrar).not.toHaveBeenCalled()
  await user.click(screen.getByRole('button', { name: 'Conferir cadastro' }))
  expect(screen.getByRole('dialog')).toHaveAccessibleName('Confirmar cadastro')
  await user.keyboard('{Escape}')
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  expect(screen.getByRole('button', { name: 'Conferir cadastro' })).toHaveFocus()
  expect(porta.cadastrar).not.toHaveBeenCalled()
  await user.click(screen.getByRole('button', { name: 'Conferir cadastro' }))
  await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirmar cadastro' }))
  await screen.findByRole('table', { name: 'Recebíveis cadastrados para auditoria' })
  expect(porta.liquidar).not.toHaveBeenCalled()
  expect(screen.queryByRole('button', { name: 'Liquidar lote' })).not.toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Obter prévia financeira de todos os itens' }))
  expect(await screen.findByText('200/200 simulações válidas')).toBeVisible()
  const tabela = screen.getByRole('table', { name: 'Simulações financeiras de todos os títulos' })
  expect(within(tabela).getAllByRole('row')).toHaveLength(201)
  expect(screen.getByText('BRL: R$ 18.571.988,00')).toBeVisible()
  await user.click(screen.getByRole('button', { name: 'Liquidar lote' }))
  const modal = screen.getByRole('dialog', { name: 'Confirmar liquidação' })
  expect(within(modal).getByText(/rollback integral/)).toBeVisible()
  expect(porta.liquidar).not.toHaveBeenCalled()
  await user.click(within(modal).getByRole('button', { name: 'Cancelar' }))
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  expect(porta.liquidar).not.toHaveBeenCalled()
  await user.click(screen.getByRole('button', { name: 'Liquidar lote' }))
  await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirmar liquidação' }))
  expect(await screen.findByText(/Todos os 200 títulos foram liquidados/)).toBeVisible()
  expect(within(await screen.findByRole('table', { name: 'Resultados financeiros efetivos' })).getAllByRole('row')).toHaveLength(201)
  expect(porta.liquidar).toHaveBeenCalledTimes(1)
  expect(screen.queryByRole('button', { name: 'Liquidar lote' })).not.toBeInTheDocument()
}, 30000)

test('quatro JA_LIQUIDADO permanecem visíveis em B de 200, navegáveis e no relatório baixável', async () => {
  const porta = criarPortaLotes(200)
  const dados = dadosLote(200).previa
  dados.confirmavel = false
  for (const indice of [0, 1, 120, 198]) {
    dados.itens[indice]!.status = 'JA_LIQUIDADO'
    dados.itens[indice]!.erros = [{ campo: 'referenciaExterna', mensagem: 'Recebível já liquidado.' }]
  }
  porta.prevalidar.mockResolvedValue(dados)
  const { user } = iniciar(porta)
  await prevalidar(user)
  expect(screen.getByText('196/200 linhas válidas para cadastro')).toBeVisible()
  const links = within(screen.getByRole('navigation', { name: 'Linhas com impedimentos' })).getAllByRole('link')
  expect(links).toHaveLength(4)
  await user.click(links[2]!)
  expect(document.activeElement).toHaveAttribute('id', 'linha-lote-122')
  expect(screen.getByRole('button', { name: 'Conferir cadastro' })).toBeDisabled()
  const relatorio = screen.getByRole('link', { name: 'Baixar relatório de erros e avisos' }).getAttribute('href')!
  const linhas = JSON.parse(decodeURIComponent(relatorio.split(',')[1]!)) as { status: string }[]
  expect(linhas).toHaveLength(4)
  expect(linhas.every((linha) => linha.status === 'JA_LIQUIDADO')).toBe(true)
  expect(porta.cadastrar).not.toHaveBeenCalled()
}, 15000)

test('modelos CNPJ e UUID têm links distintos e instruções de identidade e preservação textual', () => {
  iniciar()
  for (const [nome, arquivo] of [
    ['CSV por CNPJ', 'modelo-lote-cnpj.csv'], ['XLSX por CNPJ', 'modelo-lote-cnpj.xlsx'],
    ['CSV por UUID (legado)', 'modelo-lote.csv'], ['XLSX por UUID (legado)', 'modelo-lote.xlsx'],
  ]) {
    expect(screen.getByRole('link', { name: `Baixar modelo ${nome}` })).toHaveAttribute('href', `/modelos/${arquivo}`)
  }
  expect(screen.getByText(/não identifica a organização/)).toHaveTextContent('sessão JWT')
  expect(screen.getByText(/Mantenha a coluna como texto/)).toHaveTextContent('zeros à esquerda')
  expect(screen.getByText(/Não misture os dois cabeçalhos/)).toBeVisible()
})

test.each([
  ['inválido', 'CNPJ inválido.'],
  ['inexistente', 'Cedente indisponível.'],
  ['inativo', 'Cedente indisponível.'],
  ['legado ambíguo', 'Identificação do cedente indisponível para importação.'],
])('CNPJ %s: erro seguro por linha preserva as 200 linhas e bloqueia o lote inteiro', async (_, mensagem) => {
  const porta = criarPortaLotes(200)
  const previa = dadosLote(200).previa
  previa.confirmavel = false
  previa.itens[120] = {
    ...previa.itens[120]!, status: 'INVALIDO', comando: null, simulacao: null,
    erros: [{ campo: 'cedenteDocumento', mensagem }],
  }
  porta.prevalidar.mockResolvedValue(previa)
  const { user } = iniciar(porta)
  await prevalidar(user)
  expect(screen.getByText('199/200 linhas válidas para cadastro')).toBeVisible()
  expect(within(screen.getByRole('table', { name: 'Pré-validação de todas as linhas' })).getAllByRole('row')).toHaveLength(201)
  expect(screen.getByText(`cedenteDocumento: ${mensagem}`)).toBeVisible()
  await user.click(screen.getByRole('link', { name: 'Ir à linha 122: INVALIDO' }))
  expect(document.activeElement).toHaveAttribute('id', 'linha-lote-122')
  expect(screen.getByRole('button', { name: 'Conferir cadastro' })).toBeDisabled()
  expect(porta.cadastrar).not.toHaveBeenCalled()
  expect(porta.liquidar).not.toHaveBeenCalled()
}, 15000)

test.each(['csv', 'xlsx'])('upload %s por CNPJ confirma somente UUID canônico e mantém chave no retry 503', async (extensao) => {
  const dados = dadosLote()
  const cedenteId = 'c523d05c-00a5-43e7-a1ba-a206e8aa9391'
  dados.previa.itens.forEach((item) => { item.comando!.cedenteId = cedenteId })
  const transporte = vi.fn()
    .mockResolvedValueOnce(new Response(JSON.stringify(dados.previa), { status: 200 }))
    .mockResolvedValueOnce(new Response('{}', { status: 503 }))
    .mockResolvedValueOnce(new Response(JSON.stringify(dados.cadastro), { status: 201 }))
  vi.stubGlobal('fetch', transporte)
  const { user } = iniciar(bffLotes)
  await prevalidar(user, `lote.${extensao}`)
  expect(screen.getAllByText(cedenteId)).toHaveLength(2)
  expect(transporte).toHaveBeenCalledTimes(1)
  await user.click(screen.getByRole('button', { name: 'Conferir cadastro' }))
  await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirmar cadastro' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('indisponíveis')
  await user.click(screen.getByRole('button', { name: 'Repetir confirmação de cadastro' }))
  await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirmar cadastro' }))
  await screen.findByRole('table', { name: 'Recebíveis cadastrados para auditoria' })
  expect(transporte.mock.calls[1]).toEqual(transporte.mock.calls[2])
  const [url, opcoes] = transporte.mock.calls[1]!
  expect(url).toBe('/bff/v1/lotes/recebiveis')
  expect(JSON.parse(opcoes.body)).toEqual({ itens: dados.previa.itens.map((item) => item.comando) })
  expect(opcoes.body).not.toContain('cedenteDocumento')
  expect(transporte).toHaveBeenCalledTimes(3)
})

test.each([403, 422])('cadastro por CNPJ rejeitado com HTTP %s preserva 200 linhas sem retry ou liquidação', async (status) => {
  const dados = dadosLote(200)
  const rejeitado = structuredClone(dados.previa)
  rejeitado.itens[120] = {
    ...rejeitado.itens[120]!, comando: null, status: 'INVALIDO',
    erros: [{ campo: 'cedenteDocumento', mensagem: 'Cedente indisponível.' }],
  }
  const transporte = vi.fn()
    .mockResolvedValueOnce(new Response(JSON.stringify(dados.previa), { status: 200 }))
    .mockResolvedValueOnce(new Response(JSON.stringify({ itens: status === 422 ? [rejeitado.itens[120]] : [] }), { status }))
  vi.stubGlobal('fetch', transporte)
  const { user } = iniciar(bffLotes)
  await prevalidar(user)
  await user.click(screen.getByRole('button', { name: 'Conferir cadastro' }))
  await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirmar cadastro' }))
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  expect(screen.getByRole('button', { name: 'Conferir cadastro' })).toBeDisabled()
  expect(within(screen.getByRole('table', { name: 'Pré-validação de todas as linhas' })).getAllByRole('row')).toHaveLength(201)
  if (status === 422) expect(screen.getByText('cedenteDocumento: Cedente indisponível.')).toBeVisible()
  else expect(screen.getByText('Acesso não autorizado. Nenhuma confirmação permitida.')).toBeVisible()
  expect(screen.queryByRole('button', { name: 'Liquidar lote' })).not.toBeInTheDocument()
  expect(transporte).toHaveBeenCalledTimes(2)
}, 15000)

test('arquivo inválido e substituição descartam prévia anterior sem chamar parser financeiro local', async () => {
  const { porta, user } = iniciar()
  await prevalidar(user)
  await user.upload(screen.getByLabelText('Arquivo CSV ou XLSX'), new File(['macro'], 'macro.xlsm'))
  expect(screen.queryByRole('button', { name: 'Conferir cadastro' })).not.toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Pré-validar arquivo' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Selecione CSV ou XLSX')
  expect(porta.prevalidar).toHaveBeenCalledTimes(1)
})

test.each(['incompleta', 'câmbio indisponível'] as const)('bloqueia liquidação com prévia %s mesmo em HTTP 200', async (falha) => {
  const porta = criarPortaLotes()
  const dados = dadosLote().financeira
  if (falha === 'incompleta') dados.itens.pop()
  else {
    dados.aptoParaLiquidar = false
    dados.hashPrevia = null
    dados.itens[0]!.impedimento = 'DADOS_FINANCEIROS_INDISPONIVEIS'
    dados.itens[0]!.valorPagamento = null
  }
  porta.previsualizar.mockResolvedValue(dados)
  const { user } = iniciar(porta)
  await cadastrar(user)
  await user.click(screen.getByRole('button', { name: 'Obter prévia financeira de todos os itens' }))
  expect(await screen.findByRole('button', { name: 'Liquidar lote' })).toBeDisabled()
  expect(porta.liquidar).not.toHaveBeenCalled()
})

test('409 informa rollback integral e exige nova prévia sem retry automático', async () => {
  const porta = criarPortaLotes()
  porta.liquidar.mockRejectedValueOnce(new ErroLote('Conflito: rollback integral. Obtenha nova prévia.', 409))
  const { user } = iniciar(porta)
  await cadastrar(user)
  await user.click(screen.getByRole('button', { name: 'Obter prévia financeira de todos os itens' }))
  await user.click(await screen.findByRole('button', { name: 'Liquidar lote' }))
  await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirmar liquidação' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('rollback integral')
  expect(screen.queryByRole('table', { name: 'Resultados financeiros efetivos' })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Liquidar lote' })).not.toBeInTheDocument()
  expect(porta.liquidar).toHaveBeenCalledTimes(1)
  expect(porta.previsualizar).toHaveBeenCalledTimes(1)
})

test('503 financeiro mantém chave/comando, bloqueia nova prévia e pede reconfirmação deliberada', async () => {
  const porta = criarPortaLotes()
  porta.liquidar.mockRejectedValueOnce(new ErroLote('Serviço indisponível', 503))
  const { user, props } = iniciar(porta)
  await cadastrar(user)
  await user.click(screen.getByRole('button', { name: 'Obter prévia financeira de todos os itens' }))
  await user.click(await screen.findByRole('button', { name: 'Liquidar lote' }))
  await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirmar liquidação' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Serviço indisponível')
  expect(screen.getByRole('button', { name: 'Atualizar prévia financeira' })).toBeDisabled()
  expect(screen.getByRole('button', { name: 'Iniciar outro lote' })).toBeDisabled()
  expect(props.aoAtualizarOperacaoPendente).toHaveBeenLastCalledWith(true)
  await user.click(screen.getByRole('button', { name: 'Repetir confirmação de liquidação' }))
  expect(porta.liquidar).toHaveBeenCalledTimes(1)
  await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirmar liquidação' }))
  expect(await screen.findByText(/Todos os 2 títulos foram liquidados/)).toBeVisible()
  expect(porta.liquidar.mock.calls[0]).toEqual(porta.liquidar.mock.calls[1])
})

test('expiração mantém intenção em memória, oculta modal antigo e reutiliza chave após reautenticar', async () => {
  const porta = criarPortaLotes()
  porta.cadastrar.mockRejectedValueOnce(new ErroLote('Sessão expirada', 401))
  const { user, props, rerender } = iniciar(porta)
  await prevalidar(user)
  await user.click(screen.getByRole('button', { name: 'Conferir cadastro' }))
  await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirmar cadastro' }))
  await screen.findByRole('alert')
  expect(props.tratarErro).toHaveBeenCalledWith(expect.objectContaining({ status: 401 }))
  rerender(null)
  expect(screen.getByRole('button', { name: 'Repetir confirmação de cadastro' })).toBeDisabled()
  rerender('jwt-reautenticado')
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: 'Repetir confirmação de cadastro' }))
  await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirmar cadastro' }))
  await screen.findByRole('table', { name: 'Recebíveis cadastrados para auditoria' })
  expect(porta.cadastrar.mock.calls[0]!.slice(1)).toEqual(porta.cadastrar.mock.calls[1]!.slice(1))
  expect(porta.cadastrar.mock.calls[1]![0]).toBe('jwt-reautenticado')
})

test('troca de sessão fecha modal aberto sem executar confirmação ao reentrar', async () => {
  const { porta, user, rerender } = iniciar()
  await prevalidar(user)
  await user.click(screen.getByRole('button', { name: 'Conferir cadastro' }))
  expect(screen.getByRole('dialog')).toBeVisible()
  rerender(null)
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  rerender('novo-token')
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  expect(porta.cadastrar).not.toHaveBeenCalled()
})

test('duplo clique no modal e navegação de teclado não duplicam cadastro pendente', async () => {
  const porta = criarPortaLotes()
  let concluir!: (valor: ReturnType<typeof dadosLote>['cadastro']) => void
  porta.cadastrar.mockImplementationOnce(() => new Promise((resolve) => { concluir = resolve }))
  const { user } = iniciar(porta)
  await prevalidar(user)
  await user.click(screen.getByRole('button', { name: 'Conferir cadastro' }))
  const botao = within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirmar cadastro' })
  fireEvent.click(botao)
  fireEvent.click(botao)
  expect(porta.cadastrar).toHaveBeenCalledTimes(1)
  expect(botao).toBeDisabled()
  expect(screen.getByRole('button', { name: 'Cancelar' })).toBeDisabled()
  concluir(dadosLote().cadastro)
  await screen.findByRole('table', { name: 'Recebíveis cadastrados para auditoria' })
})

test('exibe BRL/USD e invalida o gate quando o câmbio vence com a tela aberta', async () => {
  const porta = criarPortaLotes()
  const agora = Date.now()
  const dados = dadosLoteMisto(new Date(agora + 60000).toISOString())
  porta.prevalidar.mockResolvedValue(dados.previa)
  porta.previsualizar.mockResolvedValue(dados.financeira)
  const { user } = iniciar(porta)
  await cadastrar(user)
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  vi.useFakeTimers()
  vi.setSystemTime(agora)
  await act(async () => fireEvent.click(screen.getByRole('button', { name: 'Obter prévia financeira de todos os itens' })))
  expect(screen.getByText('BRL: R$ 92.859,94')).toBeVisible()
  expect(screen.getByText(/USD: US\$\s*17\.094,67/)).toBeVisible()
  expect(screen.getByText(/fonte: DEMO/)).toBeVisible()
  expect(screen.getByRole('button', { name: 'Liquidar lote' })).toBeEnabled()
  await act(async () => { vi.advanceTimersByTime(60001) })
  expect(screen.getByText('1/2 simulações válidas')).toBeVisible()
  expect(screen.getByRole('button', { name: 'Liquidar lote' })).toBeDisabled()
  expect(porta.liquidar).not.toHaveBeenCalled()
})
