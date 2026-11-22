import { ThemeProvider } from '@mui/material/styles'
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import { ErroOperacao, type PortaOperacoes } from '../src/aplicacao/operacoes'
import type { PortaGestaoUsuarios } from '../src/aplicacao/gestaoUsuarios'
import type { PortaExtrato } from '../src/aplicacao/extrato'
import { Aplicacao } from '../src/apresentacao/Aplicacao'
import type { Simulacao } from '../src/dominio/operacoes'
import { formatarMonetario } from '../src/dominio/formatacaoMonetaria'
import { tema } from '../src/tema/tema'

beforeEach(() => window.history.replaceState(null, '', '/'))

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

function criarPortaExtrato(): PortaExtrato {
  return {
    consultar: vi.fn().mockResolvedValue({ itens: [], pagina: 0, tamanho: 20, total: 0 }),
  }
}

async function iniciar(
  operacoes: PortaOperacoes,
  locale = 'en-US',
  extrato = criarPortaExtrato(),
  gestaoUsuarios = criarPortaGestao(),
) {
  const user = userEvent.setup()
  render(<ThemeProvider theme={tema}><Aplicacao
    operacoes={operacoes}
    gestaoUsuarios={gestaoUsuarios}
    extrato={extrato}
    locale={locale}
    fusoHorario="America/Sao_Paulo"
  /></ThemeProvider>)
  await user.type(screen.getByLabelText(/E-mail/), 'operador@example.com')
  await user.type(screen.getByLabelText(/Senha/), 'senha')
  await user.click(screen.getByRole('button', { name: 'Entrar' }))
  if (window.location.hash === '#/extrato') {
    await screen.findByText('Nenhuma liquidação encontrada')
  } else {
    await screen.findByRole('combobox', { name: 'Cedente' })
  }
  return user
}

async function navegarA(user: ReturnType<typeof userEvent.setup>, rotulo: string) {
  const menuMovel = screen.queryByRole('button', { name: 'Abrir menu de navegação' })
  const link = screen.queryByRole('link', { name: rotulo })
  if (!link && menuMovel) await user.click(menuMovel)
  await user.click(screen.getByRole('link', { name: rotulo }))
  if (menuMovel) await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
}

async function preencher(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('combobox', { name: 'Cedente' }))
  await user.click(screen.getByRole('option', { name: 'Empresa — 123' }))
  expect(screen.getByText('País: desconhecido; tipo: desconhecido; status: desconhecido.')).toBeVisible()
  await navegarA(user, 'Simulação')
  await user.type(screen.getByLabelText(/Valor de face em BRL/), '100000.00')
  await user.type(screen.getByLabelText(/Prazo em meses/), '3')
}

test('aguarda 500 ms após a última edição antes de simular', async () => {
  const porta = criarPorta()
  const user = await iniciar(porta)
  await navegarA(user, 'Simulação')
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
  expect(screen.getByText('Referência da simulação:')).toBeVisible()
  const instanteReferencia = document.querySelector(`time[datetime="${simulacao.referencia}"]`)
  expect(instanteReferencia).toHaveTextContent('11/20/2026 09:00:00 (America/Sao_Paulo)')
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
  await navegarA(user, 'Simulação')
  await navegarA(user, 'Liquidação')
  await user.click(screen.getByRole('button', { name: 'Confirmar liquidação' }))
  await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Liquidar' }))
  await screen.findByText(/Sessão expirada\. Entre novamente com a mesma conta/)
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  await user.type(screen.getByLabelText(/Senha/), 'senha')
  await user.click(screen.getByRole('button', { name: 'Entrar' }))
  await user.click(await screen.findByRole('button', { name: 'Confirmar liquidação' }))
  await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Liquidar' }))
  await screen.findByText(/Valor presente:.*92,000\.01/)
  expect(screen.getByText(/Código da liquidação: liquidacao-1/)).toBeVisible()
  expect(screen.getByText('Data/hora da liquidação:')).toBeVisible()
  const instanteLiquidacao = document.querySelector('time[datetime="2026-11-20T12:00:00Z"]')
  expect(instanteLiquidacao).toHaveTextContent('11/20/2026 09:00:00 (America/Sao_Paulo)')
  expect(porta.cadastrarRecebivel).toHaveBeenCalledTimes(1)
  expect(porta.liquidar).toHaveBeenCalledTimes(2)
  expect(porta.liquidar.mock.calls[0]?.[3]).toBeTruthy()
  expect(porta.liquidar.mock.calls[1]?.[3]).toBe(porta.liquidar.mock.calls[0]?.[3])
}, 10000)

test('impede saída durante cadastro e confirma antes de descartar intenção pendente', async () => {
  const porta = criarPorta()
  let concluirCadastro: (resposta: { id: string }) => void = () => { throw new Error('cadastro não iniciado') }
  porta.cadastrarRecebivel.mockImplementationOnce(() => new Promise((resolve) => { concluirCadastro = resolve }))
  const user = await iniciar(porta)
  await preencher(user)
  await screen.findByText(/Valor presente:.*92,859\.94/)
  await user.click(screen.getByRole('button', { name: 'Cadastrar recebível' }))

  const sairBloqueado = screen.getByRole('button', { name: 'Aguarde a conclusão da operação para sair' })
  expect(sairBloqueado).toBeDisabled()
  await act(async () => concluirCadastro({ id: 'recebivel-1' }))
  await screen.findByRole('button', { name: 'Confirmar liquidação' })

  const botaoSair = screen.getByRole('button', { name: 'Sair' })
  expect(botaoSair).toBeEnabled()
  const tentativaDescarte = new Event('beforeunload', { cancelable: true })
  expect(window.dispatchEvent(tentativaDescarte)).toBe(false)
  await user.click(botaoSair)
  const dialogo = screen.getByRole('dialog', { name: 'Sair com operação pendente?' })
  expect(within(dialogo).getByText(/consulte os recebíveis no servidor/)).toBeVisible()
  await user.click(within(dialogo).getByRole('button', { name: 'Continuar operação' }))
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  expect(screen.getByRole('button', { name: 'Confirmar liquidação' })).toBeVisible()
  await user.click(botaoSair)
  await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Sair mesmo assim' }))
  expect(await screen.findByLabelText(/E-mail/)).toBeVisible()
})

test('explicita incerteza da liquidação e retenta com a mesma chave idempotente', async () => {
  const porta = criarPorta()
  porta.liquidar.mockRejectedValueOnce(new ErroOperacao('Falha de conexão.'))
  const user = await iniciar(porta)
  await preencher(user)
  await screen.findByText(/Valor presente:.*92,859\.94/)
  await user.click(screen.getByRole('button', { name: 'Cadastrar recebível' }))
  await user.click(await screen.findByRole('button', { name: 'Confirmar liquidação' }))
  await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Liquidar' }))

  expect(await screen.findByText(/O resultado da liquidação é incerto/)).toBeVisible()
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  expect(screen.getByRole('button', { name: 'Confirmar liquidação' })).toBeEnabled()
  await user.click(screen.getByRole('button', { name: 'Confirmar liquidação' }))
  await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Liquidar' }))
  await screen.findByText(/Código da liquidação: liquidacao-1/)
  expect(porta.cadastrarRecebivel).toHaveBeenCalledTimes(1)
  expect(porta.liquidar).toHaveBeenCalledTimes(2)
  expect(porta.liquidar.mock.calls[1]?.[3]).toBe(porta.liquidar.mock.calls[0]?.[3])
}, 10000)

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
  await user.click(screen.getByRole('button', { name: 'Sair' }))
  expect(screen.getByRole('dialog', { name: 'Sair com operação pendente?' })).toBeVisible()
})

test('cadastra cedente e permite nova tentativa de simulação indisponível', async () => {
  const porta = criarPorta()
  porta.listarCedentes.mockResolvedValueOnce([])
    .mockResolvedValue([{ id: 'cedente-2', nome: 'Novo', documento: '456', ativo: true, pais: 'US', tipoDocumento: 'OUTRO', documentoCanonico: '456', statusDocumento: 'NAO_VERIFICADO' }])
  porta.simular.mockRejectedValueOnce(new ErroOperacao('Serviço indisponível.', 503))
  const user = userEvent.setup()
  render(<ThemeProvider theme={tema}><Aplicacao
    operacoes={porta}
    gestaoUsuarios={criarPortaGestao()}
    extrato={criarPortaExtrato()}
    locale="en-US"
    fusoHorario="America/Sao_Paulo"
  /></ThemeProvider>)
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
  await navegarA(user, 'Simulação')
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
  await navegarA(user, 'Simulação')
  await user.type(screen.getByLabelText(/Valor de face em BRL/), '1.234,5')
  await user.type(screen.getByLabelText(/Prazo em meses/), '3')
  await screen.findByText(/Valor presente:.*92\.859,94/)
  expect(porta.simular).toHaveBeenCalledWith(
    'jwt',
    expect.objectContaining({ valorFaceBrl: '1234.50' }),
    expect.any(AbortSignal),
  )
  expect(screen.getByLabelText(/Valor de face em BRL/)).toHaveValue('R$\u00a01.234,50')
  await user.click(screen.getByRole('button', { name: 'Cadastrar recebível' }))
  expect(porta.cadastrarRecebivel).toHaveBeenCalledWith('jwt', expect.objectContaining({ valorFaceBrl: '1234.50' }))
})

test('formata o valor ao sair do campo e restaura a edição com cursor e decimal preservados', async () => {
  const user = await iniciar(criarPorta(), 'pt-BR')
  await navegarA(user, 'Simulação')
  const campo = screen.getByLabelText(/Valor de face em BRL/) as HTMLInputElement
  await user.type(campo, '25000')
  expect(campo).toHaveValue('25000')
  await user.tab()
  await waitFor(() => expect(campo).toHaveValue('R$\u00a025.000,00'))

  const antesDaFracao = campo.value.indexOf(',')
  campo.setSelectionRange(antesDaFracao, antesDaFracao)
  campo.focus()
  await waitFor(() => expect(campo).toHaveValue('25.000,00'))
  expect(campo.selectionStart).toBe('25.000,00'.indexOf(','))
})

test('colagem, substituição de seleção e backspace mantêm a entrada monetária editável', async () => {
  const porta = criarPorta()
  const user = await iniciar(porta, 'pt-BR')
  await navegarA(user, 'Simulação')
  const campo = screen.getByLabelText(/Valor de face em BRL/) as HTMLInputElement
  campo.focus()
  await user.paste('1.234,56')
  expect(campo).toHaveValue('1.234,56')

  campo.setSelectionRange(1, 3)
  await user.keyboard('9')
  expect(campo).toHaveValue('1934,56')
  campo.setSelectionRange(3, 3)
  await user.keyboard('{Backspace}')
  expect(campo).toHaveValue('194,56')

  await user.type(screen.getByLabelText(/Prazo em meses/), '3')
  await screen.findByText(/Valor presente:.*92\.859,94/)
  expect(porta.simular.mock.calls[0]?.[1]).toMatchObject({ valorFaceBrl: '194.56' })
})

test('entrada monetária en-US mantém agrupamento local e payload decimal canônico', async () => {
  const porta = criarPorta()
  const user = await iniciar(porta, 'en-US')
  await navegarA(user, 'Simulação')
  const campo = screen.getByLabelText(/Valor de face em BRL/) as HTMLInputElement
  await user.type(campo, '25,000.00')
  await user.tab()
  await waitFor(() => expect(campo).toHaveValue(formatarMonetario('25000.00', 'BRL', 'en-US')))
  await user.type(screen.getByLabelText(/Prazo em meses/), '3')
  await waitFor(() => expect(porta.simular).toHaveBeenCalled())
  expect(porta.simular.mock.calls[0]?.[1]).toMatchObject({ valorFaceBrl: '25000.00' })
})

test('extrato usa o JWT da sessão atual e navegação não pede novo login', async () => {
  const operacoes = criarPorta()
  const extrato = criarPortaExtrato()
  const user = await iniciar(operacoes, 'pt-BR', extrato)
  await navegarA(user, 'Extrato')

  expect(await screen.findByText('Nenhuma liquidação encontrada')).toBeVisible()
  expect(extrato.consultar).toHaveBeenCalledWith(
    expect.objectContaining({ pagina: 0, tamanho: 20 }),
    'jwt',
    expect.any(AbortSignal),
  )
  expect(operacoes.autenticar).toHaveBeenCalledTimes(1)
  expect(window.location.hash).toBe('#/extrato')
  expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1)
})

test('menu mobile e skip link funcionam por teclado e preservam a rota', async () => {
  const user = await iniciar(criarPorta())
  const botaoMenu = screen.getByRole('button', { name: 'Abrir menu de navegação' })
  botaoMenu.focus()
  await user.keyboard('{Enter}')
  expect(botaoMenu).toHaveAttribute('aria-expanded', 'true')

  const simulacao = screen.getByRole('link', { name: 'Simulação' })
  simulacao.focus()
  await user.keyboard('{Enter}')
  await waitFor(() => expect(screen.getByRole('heading', { name: 'Simulação', level: 1 })).toBeVisible())
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

  const pular = screen.getByRole('link', { name: 'Pular para o conteúdo' })
  pular.focus()
  await user.keyboard('{Enter}')
  expect(document.activeElement).toBe(document.getElementById('conteudo'))
  expect(window.location.hash).toBe('#/simulacao')
})

test('rota direta e histórico do navegador selecionam páginas sem desmontar a aplicação', async () => {
  window.history.replaceState(null, '', '/#/extrato')
  const extrato = criarPortaExtrato()
  const user = await iniciar(criarPorta(), 'en-US', extrato)
  expect(await screen.findByText('Nenhuma liquidação encontrada')).toBeVisible()

  await navegarA(user, 'Simulação')
  await navegarA(user, 'Cedentes')
  await act(async () => { window.history.back() })
  await waitFor(() => expect(screen.getByRole('heading', { name: 'Simulação', level: 1 })).toBeVisible())
  await user.click(screen.getByRole('link', { name: 'Pular para o conteúdo' }))
  expect(window.location.hash).toBe('#/simulacao')
  expect(document.activeElement).toBe(document.getElementById('conteudo'))
  const menu = screen.queryByRole('button', { name: 'Abrir menu de navegação' })
  if (menu) await user.click(menu)
  expect(screen.getByRole('link', { name: 'Simulação' })).toHaveAttribute('aria-current', 'page')
  expect(extrato.consultar).toHaveBeenCalledTimes(1)
})

test('menu de usuários só aparece com capacidade retornada pelo BFF', async () => {
  const gestao = criarPortaGestao()
  gestao.minhaConta.mockResolvedValue({
    id: 'admin-1',
    email: 'admin@example.com',
    perfil: 'ADMIN',
    capacidades: { perfisCriaveis: ['OPERADOR'], podeListarUsuarios: true, podeAlterarStatus: true },
  })
  const user = await iniciar(criarPorta(), 'pt-BR', criarPortaExtrato(), gestao)
  await waitFor(() => expect(gestao.minhaConta).toHaveBeenCalledWith('jwt', expect.any(AbortSignal)))
  await navegarA(user, 'Usuários')
  expect(await screen.findByRole('heading', { name: 'Administração de usuários' })).toBeVisible()
  expect(gestao.minhaConta).toHaveBeenCalledTimes(1)
})

test('menu de usuários não aparece para perfil sem capacidade', async () => {
  const gestao = criarPortaGestao()
  const user = await iniciar(criarPorta(), 'pt-BR', criarPortaExtrato(), gestao)
  await waitFor(() => expect(gestao.minhaConta).toHaveBeenCalledWith('jwt', expect.any(AbortSignal)))
  const menu = screen.queryByRole('button', { name: 'Abrir menu de navegação' })
  if (menu) await user.click(menu)
  expect(screen.queryByRole('link', { name: 'Usuários' })).not.toBeInTheDocument()
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
