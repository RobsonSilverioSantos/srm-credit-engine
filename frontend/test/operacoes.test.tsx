import { ThemeProvider } from '@mui/material/styles'
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, test, vi } from 'vitest'
import { ErroOperacao, type PortaOperacoes } from '../src/aplicacao/operacoes'
import { Aplicacao } from '../src/apresentacao/Aplicacao'
import type { Simulacao } from '../src/dominio/operacoes'
import { tema } from '../src/tema/tema'

const simulacao: Simulacao = {
  moeda: 'BRL', valorPresenteBrl: '92859.94', desagioBrl: '7140.06', valorPagamento: '92859.94',
  taxaBaseMensal: '0.0100000000', spreadMensal: '0.0150000000', cotacaoBrlPorUsd: null,
  referencia: '2026-11-20T12:00:00Z', cotacaoValidaAte: null,
}

function criarPorta() {
  return {
    autenticar: vi.fn().mockResolvedValue({ token: 'jwt', expiraEm: Date.now() + 600000 }),
    listarCedentes: vi.fn().mockResolvedValue([{ id: 'cedente-1', nome: 'Empresa', documento: '123', ativo: true }]),
    cadastrarCedente: vi.fn().mockResolvedValue({ id: 'cedente-2', nome: 'Novo', documento: '456', ativo: true }),
    simular: vi.fn().mockResolvedValue(simulacao),
    cadastrarRecebivel: vi.fn().mockResolvedValue({ id: 'recebivel-1' }),
    liquidar: vi.fn().mockResolvedValue({
      liquidacaoId: 'liquidacao-1', recebivelId: 'recebivel-1', moeda: 'BRL',
      valorPresenteBrl: '92000.01', desagioBrl: '7999.99', valorPagamento: '92000.01', liquidadaEm: '2026-11-20T12:00:00Z',
    }),
  } satisfies PortaOperacoes
}

async function iniciar(operacoes: PortaOperacoes, locale = 'en-US') {
  const user = userEvent.setup()
  render(<ThemeProvider theme={tema}><Aplicacao operacoes={operacoes} locale={locale} /></ThemeProvider>)
  await user.type(screen.getByLabelText(/E-mail/), 'operador@example.com')
  await user.type(screen.getByLabelText(/Senha/), 'senha')
  await user.click(screen.getByRole('button', { name: 'Entrar' }))
  await screen.findByRole('combobox', { name: 'Cedente' })
  return user
}

async function preencher(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('combobox', { name: 'Cedente' }))
  await user.click(screen.getByRole('option', { name: 'Empresa — 123' }))
  await user.type(screen.getByLabelText(/Valor de face em BRL/), '100000.00')
  await user.type(screen.getByLabelText(/Prazo em meses/), '3')
}

test('aguarda 500 ms após a última edição antes de simular', async () => {
  const porta = criarPorta()
  await iniciar(porta)
  vi.useFakeTimers()
  try {
    fireEvent.change(screen.getByLabelText(/Valor de face em BRL/), { target: { value: '100.00' } })
    fireEvent.change(screen.getByLabelText(/Prazo em meses/), { target: { value: '3' } })
    await act(async () => { await vi.advanceTimersByTimeAsync(400) })
    fireEvent.change(screen.getByLabelText(/Prazo em meses/), { target: { value: '4' } })
    await act(async () => { await vi.advanceTimersByTimeAsync(499) })
    expect(porta.simular).not.toHaveBeenCalled()
    await act(async () => { await vi.advanceTimersByTimeAsync(1) })
    expect(porta.simular).toHaveBeenCalledTimes(1)
    expect(porta.simular.mock.calls[0]?.[1]).toMatchObject({ valorFaceBrl: '100.00', prazoMeses: 4 })
  } finally {
    vi.useRealTimers()
  }
})

test('descarta respostas antigas e não cadastra durante a prévia', async () => {
  const porta = criarPorta()
  let resolverPrimeira: (valor: Simulacao) => void = () => { throw new Error('simulação não iniciada') }
  porta.simular.mockImplementationOnce(() => new Promise<Simulacao>((resolve) => { resolverPrimeira = resolve }))
  const user = await iniciar(porta)
  await preencher(user)
  await waitFor(() => expect(porta.simular).toHaveBeenCalledTimes(1))
  const campo = screen.getByLabelText(/Valor de face em BRL/)
  await user.clear(campo)
  await user.type(campo, '200000.00')
  await screen.findByText(/Valor presente:.*92,859\.94/)
  resolverPrimeira({ ...simulacao, valorPresenteBrl: '1.00' })
  await waitFor(() => expect(porta.simular).toHaveBeenCalledTimes(2))
  expect(screen.queryByText('Valor presente: BRL 1.00')).not.toBeInTheDocument()
  expect(porta.cadastrarRecebivel).not.toHaveBeenCalled()
  expect(screen.getByText(/Simulação indicativa, sem garantia/)).toBeVisible()
}, 10000)

test('cadastra após prévia e mantém a chave na retentativa após expiração', async () => {
  const porta = criarPorta()
  porta.liquidar.mockRejectedValueOnce(new ErroOperacao('Sessão expirada. Entre novamente.', 401))
  const user = await iniciar(porta)
  await preencher(user)
  await screen.findByText(/Valor presente:.*92,859\.94/)
  await user.click(screen.getByRole('button', { name: 'Cadastrar recebível' }))
  expect(porta.cadastrarRecebivel).toHaveBeenCalledWith('jwt', {
    cedenteId: 'cedente-1', modalidade: 'DUPLICATA_MERCANTIL', valorFaceBrl: '100000.00', prazoMeses: 3, vencimento: null,
  })
  await user.click(screen.getByRole('button', { name: 'Confirmar liquidação' }))
  await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Liquidar' }))
  await screen.findByText(/Sessão expirada\. Entre novamente com a mesma conta/)
  await user.type(screen.getByLabelText(/Senha/), 'senha')
  await user.click(screen.getByRole('button', { name: 'Entrar' }))
  await user.click(await screen.findByRole('button', { name: 'Confirmar liquidação' }))
  await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Liquidar' }))
  await screen.findByText(/Valor presente:.*92,000\.01/)
  expect(porta.cadastrarRecebivel).toHaveBeenCalledTimes(1)
  expect(porta.liquidar).toHaveBeenCalledTimes(2)
  expect(porta.liquidar.mock.calls[0]?.[3]).toBeTruthy()
  expect(porta.liquidar.mock.calls[1]?.[3]).toBe(porta.liquidar.mock.calls[0]?.[3])
})

test('bloqueia novo cadastro quando a resposta do servidor é incerta', async () => {
  const porta = criarPorta()
  porta.cadastrarRecebivel.mockRejectedValueOnce(new ErroOperacao('Falha de conexão.'))
  const user = await iniciar(porta)
  await preencher(user)
  await screen.findByText(/Valor presente:.*92,859\.94/)
  await user.click(screen.getByRole('button', { name: 'Cadastrar recebível' }))
  expect(await screen.findByText(/O resultado do cadastro é incerto/)).toBeVisible()
  expect(screen.getByRole('button', { name: 'Cadastrar recebível' })).toBeDisabled()
  expect(porta.liquidar).not.toHaveBeenCalled()
})

test('cadastra cedente e permite nova tentativa de simulação indisponível', async () => {
  const porta = criarPorta()
  porta.listarCedentes.mockResolvedValueOnce([])
    .mockResolvedValue([{ id: 'cedente-2', nome: 'Novo', documento: '456', ativo: true }])
  porta.simular.mockRejectedValueOnce(new ErroOperacao('Serviço indisponível.', 503))
  const user = userEvent.setup()
  render(<ThemeProvider theme={tema}><Aplicacao operacoes={porta} locale="en-US" /></ThemeProvider>)
  await user.type(screen.getByLabelText(/E-mail/), 'operador@example.com')
  await user.type(screen.getByLabelText(/Senha/), 'senha')
  await user.click(screen.getByRole('button', { name: 'Entrar' }))
  expect(await screen.findByText('Nenhum cedente ativo')).toBeVisible()
  await user.type(screen.getByLabelText(/Nome do cedente/), 'Novo')
  await user.type(screen.getByLabelText(/Documento do cedente/), '456')
  await user.click(screen.getByRole('button', { name: 'Cadastrar cedente' }))
  expect(await screen.findByText('Cedente cadastrado.')).toBeVisible()
  expect(porta.cadastrarCedente).toHaveBeenCalledWith('jwt', 'Novo', '456')
  await user.type(screen.getByLabelText(/Valor de face em BRL/), '100.00')
  await user.type(screen.getByLabelText(/Prazo em meses/), '1')
  expect(await screen.findByText(/Serviço indisponível/)).toBeVisible()
  await user.click(screen.getByRole('button', { name: 'Tentar novamente' }))
  await screen.findByText(/Valor presente:.*92,859\.94/)
  expect(porta.simular).toHaveBeenCalledTimes(2)
})

test('normaliza entrada pt-BR para decimal canônico antes da simulação e cadastro', async () => {
  const porta = criarPorta()
  const user = await iniciar(porta, 'pt-BR')
  await user.click(screen.getByRole('combobox', { name: 'Cedente' }))
  await user.click(screen.getByRole('option', { name: 'Empresa — 123' }))
  await user.type(screen.getByLabelText(/Valor de face em BRL/), '1.234,5')
  await user.type(screen.getByLabelText(/Prazo em meses/), '3')
  await screen.findByText(/Valor presente:.*92\.859,94/)
  expect(porta.simular).toHaveBeenCalledWith(
    'jwt',
    expect.objectContaining({ valorFaceBrl: '1234.50' }),
    expect.any(AbortSignal),
  )
  expect(screen.getByLabelText(/Valor de face em BRL/)).toHaveValue('1.234,5')
  await user.click(screen.getByRole('button', { name: 'Cadastrar recebível' }))
  expect(porta.cadastrarRecebivel).toHaveBeenCalledWith('jwt', expect.objectContaining({ valorFaceBrl: '1234.50' }))
})
