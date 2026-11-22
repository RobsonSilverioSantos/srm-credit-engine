import { ThemeProvider } from '@mui/material/styles'
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, test, vi } from 'vitest'
import { ErroLote } from '../src/aplicacao/lotes'
import { mensagemErro } from '../src/aplicacao/operacoes'
import { PaginaImportacaoLote } from '../src/apresentacao/PaginaImportacaoLote'
import { criarPortaLotes, dadosLote, dadosLoteMisto } from './fixtures/lotes'
import { tema } from '../src/tema/tema'

afterEach(() => vi.useRealTimers())

function iniciar(porta = criarPortaLotes(), token: string | null = 'jwt') {
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
