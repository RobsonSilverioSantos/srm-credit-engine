import { ThemeProvider } from '@mui/material/styles'
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, test, vi } from 'vitest'
import { ErroOperacao, type PortaOperacoes } from '../src/aplicacao/operacoes'
import type { PortaGestaoUsuarios } from '../src/aplicacao/gestaoUsuarios'
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
    listarCedentes: vi.fn().mockResolvedValue([{ id: 'cedente-1', nome: 'Empresa', documento: '123', ativo: true, pais: null, tipoDocumento: null, documentoCanonico: null, statusDocumento: 'DESCONHECIDO' }]),
    cadastrarCedente: vi.fn().mockResolvedValue({ id: 'cedente-2', nome: 'Novo', documento: '456', ativo: true, pais: 'US', tipoDocumento: 'OUTRO', documentoCanonico: '456', statusDocumento: 'NAO_VERIFICADO' }),
    simular: vi.fn().mockResolvedValue(simulacao),
    cadastrarRecebivel: vi.fn().mockResolvedValue({ id: 'recebivel-1' }),
    liquidar: vi.fn().mockResolvedValue({
      liquidacaoId: 'liquidacao-1', recebivelId: 'recebivel-1', moeda: 'BRL',
      valorPresenteBrl: '92000.01', desagioBrl: '7999.99', valorPagamento: '92000.01', liquidadaEm: '2026-11-20T12:00:00Z',
    }),
  } satisfies PortaOperacoes
}

function criarPortaGestao() {
  return {
    minhaConta: vi.fn().mockResolvedValue({
      id: 'operador-1', email: 'operador@example.com', perfil: 'OPERADOR',
      capacidades: { perfisCriaveis: [], podeListarUsuarios: false, podeAlterarStatus: false },
    }),
    listar: vi.fn().mockResolvedValue([]),
    criar: vi.fn(),
    alterarStatus: vi.fn(),
  } satisfies PortaGestaoUsuarios
}

async function iniciar(operacoes: PortaOperacoes, locale = 'en-US') {
  const user = userEvent.setup()
  render(<ThemeProvider theme={tema}><Aplicacao operacoes={operacoes} gestaoUsuarios={criarPortaGestao()} locale={locale} /></ThemeProvider>)
  await user.type(screen.getByLabelText(/E-mail/), 'operador@example.com')
  await user.type(screen.getByLabelText(/Senha/), 'senha')
  await user.click(screen.getByRole('button', { name: 'Entrar' }))
  await screen.findByRole('combobox', { name: 'Cedente' })
  return user
}

async function preencher(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('combobox', { name: 'Cedente' }))
  await user.click(screen.getByRole('option', { name: 'Empresa — 123' }))
  expect(screen.getByText('País: desconhecido; tipo: desconhecido; status: desconhecido.')).toBeVisible()
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
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
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
    .mockResolvedValue([{ id: 'cedente-2', nome: 'Novo', documento: '456', ativo: true, pais: 'US', tipoDocumento: 'OUTRO', documentoCanonico: '456', statusDocumento: 'NAO_VERIFICADO' }])
  porta.simular.mockRejectedValueOnce(new ErroOperacao('Serviço indisponível.', 503))
  const user = userEvent.setup()
  render(<ThemeProvider theme={tema}><Aplicacao operacoes={porta} gestaoUsuarios={criarPortaGestao()} locale="en-US" /></ThemeProvider>)
  await user.type(screen.getByLabelText(/E-mail/), 'operador@example.com')
  await user.type(screen.getByLabelText(/Senha/), 'senha')
  await user.click(screen.getByRole('button', { name: 'Entrar' }))
  expect(await screen.findByText('Nenhum cedente ativo')).toBeVisible()
  await user.type(screen.getByLabelText(/Nome do cedente/), 'Novo')
  await user.clear(screen.getByLabelText(/País do documento/))
  await user.type(screen.getByLabelText(/País do documento/), 'us')
  await user.type(screen.getByLabelText(/Documento do cedente/), '456')
  await user.click(screen.getByRole('button', { name: 'Cadastrar cedente' }))
  expect(await screen.findByText('Cedente cadastrado.')).toBeVisible()
  expect(porta.cadastrarCedente).toHaveBeenCalledWith('jwt', { nome: 'Novo', documento: '456', pais: 'US', tipoDocumento: 'OUTRO' })
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

test('máscara CNPJ contextual aceita letras, digitação, colagem e edição', async () => {
  const porta = criarPorta()
  const user = await iniciar(porta)
  await user.type(screen.getByLabelText(/Nome do cedente/), 'Novo')
  const campo = screen.getByLabelText(/Documento do cedente/) as HTMLInputElement
  await user.type(campo, '12abc34501de35')
  expect(campo).toHaveValue('12.ABC.345/01DE-35')
  expect(screen.getByRole('button', { name: 'Cadastrar cedente' })).toBeEnabled()
  campo.setSelectionRange(3, 4)
  await user.keyboard('Z')
  expect(campo).toHaveValue('12.ZBC.345/01DE-35')
  await user.clear(campo)
  fireEvent.paste(campo)
  fireEvent.change(campo, { target: { value: '12.ABC.345/01DE-35' } })
  expect(campo).toHaveValue('12.ABC.345/01DE-35')
  fireEvent.paste(campo)
  fireEvent.change(campo, { target: { value: '12abc34501de35' } })
  expect(campo).toHaveValue('12.ABC.345/01DE-35')
  await user.click(screen.getByRole('button', { name: 'Cadastrar cedente' }))
  expect(porta.cadastrarCedente).toHaveBeenCalledWith('jwt', {
    nome: 'Novo', pais: 'BR', tipoDocumento: 'CNPJ', documento: '12.ABC.345/01DE-35',
  })
})

test('não descarta caracteres inválidos e troca país sem aplicar máscara estrangeira', async () => {
  const porta = criarPorta()
  const user = await iniciar(porta)
  const campo = screen.getByLabelText(/Documento do cedente/)
  fireEvent.paste(campo)
  fireEvent.change(campo, { target: { value: '12.ABC34501DE35' } })
  expect(campo).toHaveValue('12.ABC34501DE35')
  expect(screen.getByRole('button', { name: 'Cadastrar cedente' })).toBeDisabled()
  await user.clear(campo)
  await user.type(campo, '123@')
  expect(campo).toHaveValue('12.3@')
  expect(screen.getByText(/Informe um CNPJ com/)).toBeVisible()
  const pais = screen.getByLabelText(/País do documento/)
  await user.clear(pais)
  await user.type(pais, 'us')
  expect(screen.getByText(/Não é validação fiscal/)).toBeVisible()
  expect(campo).toHaveValue('12.3@')
  await user.clear(campo)
  await user.type(campo, 'ab 12')
  expect(campo).toHaveValue('AB 12')
  expect(screen.getByText(/sem espaços ou pontuação/)).toBeVisible()
  await user.clear(campo)
  await user.type(campo, 'ab123')
  expect(campo).toHaveValue('AB123')
  expect(screen.getByRole('button', { name: 'Cadastrar cedente' })).toBeEnabled()
  await user.clear(pais)
  await user.type(pais, 'zz')
  expect(screen.getByText(/O servidor valida o país/)).toBeVisible()
  expect(screen.getByRole('button', { name: 'Cadastrar cedente' })).toBeEnabled()
  await user.clear(pais)
  await user.type(pais, 'br')
  expect(screen.getByRole('button', { name: 'Cadastrar cedente' })).toBeDisabled()
  expect(porta.cadastrarCedente).not.toHaveBeenCalled()
})

test('backend decide validade e conflito sem perder o documento editado', async () => {
  const porta = criarPorta()
  porta.cadastrarCedente.mockRejectedValueOnce(new ErroOperacao('Dados inválidos.', 400))
    .mockRejectedValueOnce(new ErroOperacao('Conflito.', 409))
  const user = await iniciar(porta)
  await user.type(screen.getByLabelText(/Nome do cedente/), 'Novo')
  await user.type(screen.getByLabelText(/Documento do cedente/), '12345678000100')
  await user.click(screen.getByRole('button', { name: 'Cadastrar cedente' }))
  expect(await screen.findByText(/Dados rejeitados pelo servidor.*nome, país, tipo e documento/)).toBeVisible()
  expect(screen.getByLabelText(/Documento do cedente/)).toHaveValue('12.345.678/0001-00')
  await user.click(screen.getByRole('button', { name: 'Cadastrar cedente' }))
  expect(await screen.findByText(/Pode haver documento duplicado ou outro conflito/)).toBeVisible()
})

test('tipo é selecionável e combinações inconsistentes são bloqueadas', async () => {
  const porta = criarPorta()
  const user = await iniciar(porta)
  await user.type(screen.getByLabelText(/Nome do cedente/), 'Novo')
  const documento = screen.getByLabelText(/Documento do cedente/)
  await user.type(documento, '12345678000100')
  const tipo = screen.getByRole('combobox', { name: /Tipo de documento/ })
  await user.click(tipo)
  await user.click(screen.getByRole('option', { name: /Outro —/ }))
  expect(screen.getByText('Para BR, selecione CNPJ.')).toBeVisible()
  expect(screen.getByRole('button', { name: 'Cadastrar cedente' })).toBeDisabled()
  await user.click(tipo)
  await user.click(screen.getByRole('option', { name: 'CNPJ' }))
  expect(screen.getByRole('button', { name: 'Cadastrar cedente' })).toBeEnabled()
  const pais = screen.getByLabelText(/País do documento/)
  await user.clear(pais)
  await user.type(pais, 'US')
  expect(tipo).toHaveTextContent('Outro')
  await user.click(tipo)
  await user.click(screen.getByRole('option', { name: 'CNPJ' }))
  expect(screen.getByText('Para país estrangeiro, selecione Outro.')).toBeVisible()
  expect(screen.getByRole('button', { name: 'Cadastrar cedente' })).toBeDisabled()
  expect(porta.cadastrarCedente).not.toHaveBeenCalled()
  await user.click(tipo)
  await user.click(screen.getByRole('option', { name: /Outro —/ }))
  await user.clear(documento)
  await user.type(documento, 'abc123')
  await user.click(screen.getByRole('button', { name: 'Cadastrar cedente' }))
  expect(porta.cadastrarCedente).toHaveBeenCalledWith('jwt', {
    nome: 'Novo', pais: 'US', tipoDocumento: 'OUTRO', documento: 'ABC123',
  })
})

test('backspace, delete, seleção e inserção no meio preservam posição e permitem recuperar CNPJ', async () => {
  const user = await iniciar(criarPorta())
  const campo = screen.getByLabelText(/Documento do cedente/) as HTMLInputElement
  await user.type(campo, '12ABC34501DE35')
  campo.setSelectionRange(5, 5)
  await user.keyboard('{Backspace}')
  expect(campo).toHaveValue('12.AC3.450/1DE3-5')
  expect(campo.selectionStart).toBe(4)
  await user.keyboard('B')
  expect(campo).toHaveValue('12.ABC.345/01DE-35')
  expect(campo.selectionStart).toBe(5)
  campo.setSelectionRange(3, 3)
  await user.keyboard('{Backspace}')
  expect(campo).toHaveValue('1A.BC3.450/1DE3-5')
  expect(campo.selectionStart).toBe(1)
  await user.keyboard('2')
  expect(campo).toHaveValue('12.ABC.345/01DE-35')
  campo.setSelectionRange(2, 2)
  await user.keyboard('{Delete}')
  expect(campo).toHaveValue('12.BC3.450/1DE3-5')
  expect(campo.selectionStart).toBe(2)
  await user.keyboard('A')
  expect(campo).toHaveValue('12.ABC.345/01DE-35')
  campo.setSelectionRange(3, 6)
  await user.keyboard('XYZ')
  expect(campo).toHaveValue('12.XYZ.345/01DE-35')
  expect(campo.selectionStart).toBe(6)
})

test('colagem inválida continua visível e não é corrigida ao editar', async () => {
  const user = await iniciar(criarPorta())
  const campo = screen.getByLabelText(/Documento do cedente/) as HTMLInputElement
  await user.click(campo)
  await user.paste('12.ABC34501DE35@')
  expect(campo).toHaveValue('12.ABC34501DE35@')
  await user.keyboard('{Backspace}')
  expect(campo).toHaveValue('12.ABC34501DE35')
  expect(screen.getByRole('button', { name: 'Cadastrar cedente' })).toBeDisabled()
})

test('cursor em caracteres repetidos e substituição entre separadores', async () => {
  const user = await iniciar(criarPorta())
  const campo = screen.getByLabelText(/Documento do cedente/) as HTMLInputElement
  await user.type(campo, '11111111111111')
  campo.setSelectionRange(4, 4)
  await user.keyboard('{Backspace}')
  expect(campo).toHaveValue('11.111.111/1111-1')
  expect(campo.selectionStart).toBe(2)
  await user.keyboard('1')
  expect(campo).toHaveValue('11.111.111/1111-11')
  expect(campo.selectionStart).toBe(4)
  campo.setSelectionRange(1, 5)
  await user.keyboard('AB')
  expect(campo).toHaveValue('1A.B11.111/1111-1')
  expect(campo.selectionStart).toBe(4)
})
