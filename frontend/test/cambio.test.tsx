import { ThemeProvider } from '@mui/material/styles'
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, test, vi } from 'vitest'
import { ErroCambio } from '../src/aplicacao/cambio'
import { PaginaCambio } from '../src/apresentacao/PaginaCambio'
import type { PortaGestaoUsuarios } from '../src/aplicacao/gestaoUsuarios'
import type { PerfilUsuario } from '../src/dominio/usuarios'
import { tema } from '../src/tema/tema'
import { cotacaoManual, criarPortaCambio, selecaoManual } from './fixtures/cambio'

afterEach(() => vi.useRealTimers())

function montar(perfil: PerfilUsuario = 'ADMIN', porta = criarPortaCambio(), locale = 'pt-BR') {
  const usuarios = {
    minhaConta: vi.fn().mockResolvedValue({
      id: 'eu', email: 'eu@example.test', perfil,
      capacidades: { perfisCriaveis: [], podeListarUsuarios: false, podeAlterarStatus: false },
    }), listar: vi.fn(), criar: vi.fn(), alterarStatus: vi.fn(),
  } satisfies PortaGestaoUsuarios
  const props = {
    porta, usuarios, token: 'jwt-em-memoria' as string | null, ativa: true, locale,
    aoExpirar: vi.fn(), aoAtualizarOperacaoEmCurso: vi.fn(), aoAtualizarOperacaoPendente: vi.fn(),
  }
  const tela = render(<ThemeProvider theme={tema}><PaginaCambio {...props} /></ThemeProvider>)
  return { porta, props, tela, user: userEvent.setup() }
}

async function preencherCadastro(user: ReturnType<typeof userEvent.setup>, taxa = '5,4321') {
  await user.type(await screen.findByRole('textbox', { name: 'Taxa BRL por USD' }), taxa)
  await user.type(screen.getByRole('textbox', { name: 'Justificativa do cadastro' }), 'Exceção operacional')
  await user.click(screen.getByRole('button', { name: 'Cadastrar MANUAL' }))
}

test.each(['MASTER', 'ADMIN'] as const)('%s cadastra sem ativar, confirma autorização separada, retorna PTAX e sincroniza', async (perfil) => {
  const { porta, user } = montar(perfil)
  await preencherCadastro(user)
  expect(porta.cadastrar).not.toHaveBeenCalled()
  let modal = screen.getByRole('dialog', { name: 'Confirmar cadastro MANUAL' })
  expect(within(modal).getByText(/Cadastro não ativa/)).toBeVisible()
  await user.click(within(modal).getByRole('button', { name: 'Confirmar cadastro MANUAL' }))
  await screen.findByText(/cadastrada, ainda não autorizada/)
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  expect(porta.cadastrar).toHaveBeenCalledExactlyOnceWith('jwt-em-memoria', {
    taxaBrlPorUsd: '5.4321', justificativa: 'Exceção operacional',
  })
  expect(porta.selecionar).not.toHaveBeenCalled()
  await user.type(screen.getByRole('textbox', { name: 'Justificativa da seleção' }), 'Autorização explícita')
  await user.click(screen.getByRole('button', { name: 'Autorizar uso da MANUAL' }))
  modal = screen.getByRole('dialog', { name: 'Confirmar autorização MANUAL' })
  expect(porta.selecionar).not.toHaveBeenCalled()
  await user.click(within(modal).getByRole('button', { name: 'Confirmar autorização MANUAL' }))
  await waitFor(() => expect(porta.selecionar).toHaveBeenCalledExactlyOnceWith('jwt-em-memoria', {
    fonte: 'MANUAL', cotacaoId: cotacaoManual.id, justificativa: 'Autorização explícita',
  }))
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  await user.type(screen.getByRole('textbox', { name: 'Justificativa da seleção' }), 'Retorno autorizado')
  await user.click(screen.getByRole('button', { name: 'Retornar à PTAX' }))
  await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirmar retorno à PTAX' }))
  await waitFor(() => expect(porta.selecionar).toHaveBeenLastCalledWith('jwt-em-memoria', {
    fonte: 'BCB_PTAX', justificativa: 'Retorno autorizado',
  }))
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  await user.click(screen.getByRole('button', { name: 'Sincronizar PTAX' }))
  await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirmar sincronização PTAX' }))
  await screen.findByText(/Sincronização concluída: 2/)
  expect(porta.sincronizar).toHaveBeenCalledTimes(1)
}, 15000)

test('OPERADOR consulta sem formulário, ações ou auditoria mesmo com dados extras recebidos', async () => {
  const { porta } = montar('OPERADOR')
  await screen.findByRole('table', { name: 'Cotações cambiais' })
  expect(screen.queryByRole('textbox', { name: 'Taxa BRL por USD' })).not.toBeInTheDocument()
  for (const nome of ['Cadastrar MANUAL', 'Autorizar uso da MANUAL', 'Retornar à PTAX', 'Sincronizar PTAX']) {
    expect(screen.queryByRole('button', { name: nome })).not.toBeInTheDocument()
  }
  expect(screen.queryByText(/autor-restrito|motivo restrito/)).not.toBeInTheDocument()
  expect(porta.cadastrar).not.toHaveBeenCalled()
})

test('cancelamento por Escape restaura foco sem gravar; edição e colagem preservam caret', async () => {
  const { user, porta } = montar()
  const campo = await screen.findByRole('textbox', { name: 'Taxa BRL por USD' })
  await user.click(campo)
  await user.paste('9.999.999.999,1234567890')
  expect(campo).toHaveValue('9.999.999.999,1234567890')
  const input = campo as HTMLInputElement
  input.setSelectionRange(14, 14)
  await user.keyboard('{ArrowLeft}{Delete}4')
  expect(input.selectionStart).toBe(14)
  await user.clear(campo)
  await user.type(campo, '5,4321')
  await user.type(screen.getByRole('textbox', { name: 'Justificativa do cadastro' }), 'Motivo')
  const botao = screen.getByRole('button', { name: 'Cadastrar MANUAL' })
  await user.click(botao)
  expect(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancelar' })).toHaveFocus()
  await user.keyboard('{Escape}')
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  expect(botao).toHaveFocus()
  expect(porta.cadastrar).not.toHaveBeenCalled()
})

test('en-US envia decimal textual com escala completa', async () => {
  const { user, porta } = montar('ADMIN', criarPortaCambio(), 'en-US')
  await preencherCadastro(user, '9999999999.1234567890')
  await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirmar cadastro MANUAL' }))
  await waitFor(() => expect(porta.cadastrar).toHaveBeenCalledWith('jwt-em-memoria', {
    taxaBrlPorUsd: '9999999999.1234567890', justificativa: 'Exceção operacional',
  }))
})

test('bloqueia duplo clique e preserva resultado incerto ao navegar e renovar token', async () => {
  const porta = criarPortaCambio()
  let rejeitar!: (erro: Error) => void
  porta.cadastrar.mockImplementation(() => new Promise((_, rejeita) => { rejeitar = rejeita }))
  const { user, props, tela } = montar('ADMIN', porta)
  await preencherCadastro(user)
  const confirmar = within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirmar cadastro MANUAL' })
  fireEvent.click(confirmar)
  fireEvent.click(confirmar)
  expect(porta.cadastrar).toHaveBeenCalledTimes(1)
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  expect(screen.getByRole('button', { name: 'Cadastrar MANUAL' })).toBeDisabled()
  await act(async () => rejeitar(new ErroCambio('Conexão perdida', undefined, true)))
  await screen.findByText(/Resultado incerto/)
  expect(props.aoAtualizarOperacaoPendente).toHaveBeenCalledWith(true)
  tela.rerender(<ThemeProvider theme={tema}><PaginaCambio {...props} token={null} ativa={false} /></ThemeProvider>)
  tela.rerender(<ThemeProvider theme={tema}><PaginaCambio {...props} token="jwt-renovado" /></ThemeProvider>)
  expect(await screen.findByRole('button', { name: 'Cadastrar MANUAL' })).toBeDisabled()
  await user.click(screen.getByRole('button', { name: 'Atualizar históricos' }))
  await waitFor(() => expect(porta.historico).toHaveBeenCalledWith('jwt-renovado', expect.anything(), expect.any(AbortSignal)))
  expect(screen.getByRole('button', { name: 'Cadastrar MANUAL' })).toBeDisabled()
  expect(porta.cadastrar).toHaveBeenCalledTimes(1)
})

test.each([400, 401, 403, 409, 503])('trata HTTP %s, expira somente 401 e bloqueia escrita incerta 503', async (status) => {
  const porta = criarPortaCambio()
  porta.cadastrar.mockRejectedValue(new ErroCambio(`Erro HTTP ${status}`, status, status >= 500))
  const { user, props } = montar('ADMIN', porta)
  await preencherCadastro(user)
  await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirmar cadastro MANUAL' }))
  await screen.findByText(`Erro HTTP ${status}`)
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  expect(props.aoExpirar).toHaveBeenCalledTimes(status === 401 ? 1 : 0)
  expect(screen.getByRole('button', { name: 'Cadastrar MANUAL' })).toHaveProperty('disabled', status >= 500)
})

test('paginação independente, filtro reinicia página e leitura falha permite retry', async () => {
  const porta = criarPortaCambio()
  porta.historico.mockResolvedValue({ itens: [cotacaoManual], pagina: 0, tamanho: 20, total: 45 })
  porta.selecoes.mockRejectedValueOnce(new ErroCambio('Histórico temporariamente indisponível'))
  const { user } = montar('OPERADOR', porta)
  await screen.findByText('Histórico temporariamente indisponível')
  await user.click(screen.getByRole('button', { name: 'Tentar novamente' }))
  await screen.findByRole('table', { name: 'Seleções e autorizações cambiais' })
  await user.click(screen.getByRole('button', { name: 'Próxima página: Cotações cambiais' }))
  await waitFor(() => expect(porta.historico).toHaveBeenLastCalledWith('jwt-em-memoria', { pagina: 1, tamanho: 20 }, expect.any(AbortSignal)))
  await user.click(screen.getByRole('combobox', { name: 'Filtrar por fonte' }))
  await user.click(screen.getByRole('option', { name: 'MANUAL' }))
  await waitFor(() => expect(porta.historico).toHaveBeenLastCalledWith('jwt-em-memoria', { pagina: 0, tamanho: 20, fonte: 'MANUAL' }, expect.any(AbortSignal)))
  const tamanhos = screen.getAllByRole('combobox', { name: 'Registros por página' })
  await user.click(tamanhos[0]!)
  await user.click(screen.getByRole('option', { name: '10' }))
  await waitFor(() => expect(porta.historico).toHaveBeenLastCalledWith('jwt-em-memoria', { pagina: 0, tamanho: 10, fonte: 'MANUAL' }, expect.any(AbortSignal)))
})

test('meia-noite São Paulo usa validaAte do servidor, exclusiva, sem selecionar fallback', async () => {
  vi.useFakeTimers()
  vi.setSystemTime(new Date('2026-11-23T02:59:59.999Z'))
  const { porta } = montar('OPERADOR')
  await act(async () => { await vi.advanceTimersByTimeAsync(0) })
  expect(screen.getByText('Limite exclusivo informado pelo servidor. Elegibilidade revalidada na operação.')).toBeInTheDocument()
  const instante = screen.getByLabelText(/Validade exclusiva:.*America\/Sao_Paulo/)
  expect(instante).toHaveAttribute('dateTime', selecaoManual.validaAte)
  expect(instante).toHaveTextContent('23/11/2026 00:00:00')
  await act(async () => { await vi.advanceTimersByTimeAsync(1) })
  expect(screen.getByText(/Prazo encerrado segundo o relógio local/)).toBeInTheDocument()
  expect(porta.selecionar).not.toHaveBeenCalled()
  expect(porta.sincronizar).not.toHaveBeenCalled()
})

test('ignora respostas de página anterior após troca de filtro', async () => {
  const porta = criarPortaCambio()
  let concluir!: (valor: unknown) => void
  porta.historico.mockImplementationOnce(() => new Promise((resolve) => { concluir = resolve }))
  const { user } = montar('OPERADOR', porta)
  await screen.findByRole('combobox', { name: 'Filtrar por fonte' })
  await user.click(screen.getByRole('combobox', { name: 'Filtrar por fonte' }))
  await user.click(screen.getByRole('option', { name: 'MANUAL' }))
  await screen.findByRole('table', { name: 'Cotações cambiais' })
  await act(async () => concluir({ itens: [{ ...cotacaoManual, taxaBrlPorUsd: '9.9999' }], pagina: 0, tamanho: 20, total: 1 }))
  expect(screen.queryByText('9,9999')).not.toBeInTheDocument()
})

test('401 de POST antigo não expira token renovado antes da conclusão', async () => {
  const porta = criarPortaCambio()
  let rejeitar!: (erro: Error) => void
  porta.cadastrar.mockImplementation(() => new Promise((_, rejeita) => { rejeitar = rejeita }))
  const { user, props, tela } = montar('ADMIN', porta)
  await preencherCadastro(user)
  await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirmar cadastro MANUAL' }))
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  tela.rerender(<ThemeProvider theme={tema}><PaginaCambio {...props} token="jwt-renovado" /></ThemeProvider>)
  await waitFor(() => expect(props.usuarios.minhaConta).toHaveBeenCalledWith('jwt-renovado', expect.any(AbortSignal)))
  await act(async () => rejeitar(new ErroCambio('Sessão antiga expirada', 401)))
  expect(props.aoExpirar).not.toHaveBeenCalled()
  expect(await screen.findByText('Sessão antiga expirada')).toBeInTheDocument()
})

test('consulta já expirada mostra limite original sem renovação ou fallback', async () => {
  const porta = criarPortaCambio()
  porta.selecoes.mockResolvedValue({ itens: [{
    ...selecaoManual, autorizadoEm: '2000-01-01T22:00:00Z', validaAte: '2000-01-02T03:00:00Z',
  }], pagina: 0, tamanho: 20, total: 1 })
  montar('OPERADOR', porta)
  expect(await screen.findByText(/Prazo encerrado segundo o relógio local/)).toBeVisible()
  expect(screen.getByLabelText(/Validade exclusiva:.*America\/Sao_Paulo/)).toHaveAttribute('dateTime', '2000-01-02T03:00:00Z')
  expect(porta.selecionar).not.toHaveBeenCalled()
})

test('servidor rejeita autorização após virada do dia com 409 sem voltar à PTAX', async () => {
  const porta = criarPortaCambio()
  porta.selecionar.mockRejectedValue(new ErroCambio('Cotação de outro dia. Cadastre uma nova cotação.', 409))
  const { user } = montar('ADMIN', porta)
  await user.click(await screen.findByRole('button', { name: `Escolher MANUAL ${cotacaoManual.id}` }))
  await user.type(screen.getByRole('textbox', { name: 'Justificativa da seleção' }), 'Autorização após virada')
  await user.click(screen.getByRole('button', { name: 'Autorizar uso da MANUAL' }))
  await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirmar autorização MANUAL' }))
  expect(await screen.findByText('Cotação de outro dia. Cadastre uma nova cotação.')).toBeVisible()
  expect(porta.selecionar).toHaveBeenCalledTimes(1)
  expect(porta.sincronizar).not.toHaveBeenCalled()
})

test('sincronização 503 bloqueia novas escritas sem tentar selecionar outra fonte', async () => {
  const porta = criarPortaCambio()
  porta.sincronizar.mockRejectedValue(new ErroCambio('PTAX indisponível', 503, true))
  const { user } = montar('ADMIN', porta)
  await user.click(await screen.findByRole('button', { name: 'Sincronizar PTAX' }))
  await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirmar sincronização PTAX' }))
  expect(await screen.findByText(/Resultado incerto/)).toBeVisible()
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  expect(screen.getByRole('button', { name: 'Sincronizar PTAX' })).toBeDisabled()
  expect(porta.selecionar).not.toHaveBeenCalled()
})
