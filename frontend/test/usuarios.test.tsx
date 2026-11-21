import { ThemeProvider } from '@mui/material/styles'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, test, vi } from 'vitest'
import { alterarStatusUsuario, criarUsuario, ErroGestaoUsuarios, listarUsuarios, type PortaGestaoUsuarios } from '../src/aplicacao/gestaoUsuarios'
import type { MinhaConta, NovoUsuarioOrganizacional, UsuarioOrganizacional } from '../src/dominio/usuarios'
import { PaginaAdministracaoUsuarios } from '../src/apresentacao/PaginaAdministracaoUsuarios'
import { tema } from '../src/tema/tema'

const master: MinhaConta = {
  id: 'master-1',
  email: 'master@example.com',
  perfil: 'MASTER',
  capacidades: { perfisCriaveis: ['ADMIN', 'OPERADOR'], podeListarUsuarios: true, podeAlterarStatus: true },
}
const admin: MinhaConta = {
  id: 'admin-1',
  email: 'admin@example.com',
  perfil: 'ADMIN',
  capacidades: { perfisCriaveis: ['OPERADOR'], podeListarUsuarios: true, podeAlterarStatus: true },
}
const operadorConta: MinhaConta = {
  id: 'operador-1',
  email: 'operador@example.com',
  perfil: 'OPERADOR',
  capacidades: { perfisCriaveis: [], podeListarUsuarios: false, podeAlterarStatus: false },
}
const operador: UsuarioOrganizacional = {
  id: 'operador-2', email: 'operador2@example.com', perfil: 'OPERADOR', ativo: true,
}

function criarPorta(conta: MinhaConta, lista: UsuarioOrganizacional[] = []) {
  return {
    minhaConta: vi.fn().mockResolvedValue(conta),
    listar: vi.fn().mockResolvedValue(lista),
    criar: vi.fn().mockImplementation(async (_token: string, novo: NovoUsuarioOrganizacional) => ({
      id: 'usuario-novo', email: novo.email, perfil: novo.perfil, ativo: true,
    })),
    alterarStatus: vi.fn().mockImplementation(async (_token: string, id: string, ativo: boolean) => ({
      ...lista.find((item) => item.id === id) ?? operador,
      ativo,
    })),
  } satisfies PortaGestaoUsuarios
}

function renderizar(porta: PortaGestaoUsuarios, aoExpirar = vi.fn()) {
  render(
    <ThemeProvider theme={tema}>
      <PaginaAdministracaoUsuarios porta={porta} token="jwt" aoExpirar={aoExpirar} />
    </ThemeProvider>,
  )
  return aoExpirar
}

test('MASTER cria ADMIN e confirma a inativação de usuário da própria organização', async () => {
  const user = userEvent.setup()
  const porta = criarPorta(master, [operador])
  renderizar(porta)

  const tabela = await screen.findByRole('table', { name: 'Usuários organizacionais' })
  await user.click(screen.getByRole('combobox', { name: 'Perfil' }))
  await user.click(screen.getByRole('option', { name: 'Administrador' }))
  await user.type(screen.getByRole('textbox', { name: 'E-mail do usuário' }), ' novo-admin@example.com ')
  await user.type(screen.getByLabelText(/Senha inicial/), 'Senha-Inicial-123')
  await user.click(screen.getByRole('button', { name: 'Criar usuário' }))

  expect(porta.criar).toHaveBeenCalledWith('jwt', {
    email: 'novo-admin@example.com', senha: 'Senha-Inicial-123', perfil: 'ADMIN',
  }, expect.any(AbortSignal))
  expect(await within(tabela).findByText('novo-admin@example.com')).toBeVisible()
  expect(screen.getByLabelText(/Senha inicial/)).toHaveValue('')

  const linhaOperador = within(tabela).getByRole('row', { name: /operador2@example.com/ })
  await user.click(within(linhaOperador).getByRole('button', { name: 'Inativar' }))
  const dialogo = screen.getByRole('dialog', { name: 'Inativar usuário?' })
  expect(dialogo).toHaveTextContent('operador2@example.com perderá o acesso')
  await user.click(within(dialogo).getByRole('button', { name: 'Inativar usuário' }))
  await waitFor(() => expect(porta.alterarStatus).toHaveBeenCalledWith('jwt', 'operador-2', false, expect.any(AbortSignal)))
  expect(await within(tabela).findByText('Inativo')).toBeVisible()
})

test('ADMIN pode criar apenas OPERADOR e ativar operadores inativos', async () => {
  const user = userEvent.setup()
  const inativo = { ...operador, ativo: false }
  const porta = criarPorta(admin, [inativo])
  renderizar(porta)

  const seletor = await screen.findByRole('combobox', { name: 'Perfil' })
  await user.click(seletor)
  expect(screen.queryByRole('option', { name: 'Administrador' })).not.toBeInTheDocument()
  expect(screen.getByRole('option', { name: 'Operador' })).toBeVisible()
  await user.keyboard('{Escape}')

  await user.click(await screen.findByRole('button', { name: 'Ativar' }))
  await waitFor(() => expect(porta.alterarStatus).toHaveBeenCalledWith('jwt', 'operador-2', true, expect.any(AbortSignal)))
  expect(await screen.findByText('Ativo')).toBeVisible()
})

test('camada de aplicação bloqueia ações fora das capacidades recebidas do servidor', async () => {
  const porta = criarPorta(admin)
  const sinal = new AbortController().signal

  expect(() => criarUsuario(porta, 'jwt', admin, {
    email: 'admin-extra@example.com', senha: 'Senha-Inicial-123', perfil: 'ADMIN',
  }, sinal)).toThrow('não pode criar')
  expect(() => listarUsuarios(porta, 'jwt', operadorConta, sinal)).toThrow('não pode listar')
  expect(() => alterarStatusUsuario(porta, 'jwt', admin, {
    id: admin.id, email: admin.email, perfil: 'ADMIN', ativo: true,
  }, false, sinal)).toThrow('não pode alterar')
  expect(porta.criar).not.toHaveBeenCalled()
  expect(porta.listar).not.toHaveBeenCalled()
  expect(porta.alterarStatus).not.toHaveBeenCalled()
})

test('OPERADOR não recebe tela administrativa nem chamadas de listagem', async () => {
  const porta = criarPorta(operadorConta)
  renderizar(porta)
  await waitFor(() => expect(porta.minhaConta).toHaveBeenCalledWith('jwt', expect.any(AbortSignal)))
  expect(screen.queryByText('Administração de usuários')).not.toBeInTheDocument()
  expect(porta.listar).not.toHaveBeenCalled()
})

test('exibe conflitos 409 e retorna ao login quando a sessão expira', async () => {
  const user = userEvent.setup()
  const porta = criarPorta(master, [operador])
  porta.criar.mockRejectedValueOnce(new ErroGestaoUsuarios('Já existe um usuário com este e-mail.', 409))
  const aoExpirar = renderizar(porta)

  await screen.findByRole('table', { name: 'Usuários organizacionais' })
  await user.type(screen.getByRole('textbox', { name: 'E-mail do usuário' }), 'duplicado@example.com')
  await user.type(screen.getByLabelText(/Senha inicial/), 'Senha-Inicial-123')
  await user.click(screen.getByRole('button', { name: 'Criar usuário' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Já existe um usuário')

  porta.criar.mockRejectedValueOnce(new ErroGestaoUsuarios('Sessão expirada.', 401))
  await user.type(screen.getByLabelText(/Senha inicial/), 'Senha-Inicial-123')
  await user.click(screen.getByRole('button', { name: 'Criar usuário' }))
  await waitFor(() => expect(aoExpirar).toHaveBeenCalled())
})

test('em viewport mobile mantém formulário e lista acessíveis sem campo de organização', async () => {
  const larguraOriginal = window.innerWidth
  Object.defineProperty(window, 'innerWidth', { configurable: true, value: 375 })
  window.dispatchEvent(new Event('resize'))
  const porta = criarPorta(master, [operador])
  renderizar(porta)

  expect(await screen.findByRole('textbox', { name: 'E-mail do usuário' })).toBeVisible()
  expect(screen.getByLabelText(/Senha inicial/)).toBeVisible()
  expect(screen.queryByLabelText(/Organização|organizacaoId/i)).not.toBeInTheDocument()
  expect(screen.getByRole('region', { name: 'Usuários organizacionais' })).toHaveAttribute('tabindex', '0')
  expect(screen.getByRole('button', { name: 'Criar usuário' })).toBeVisible()

  Object.defineProperty(window, 'innerWidth', { configurable: true, value: larguraOriginal })
  window.dispatchEvent(new Event('resize'))
})

test('valida e-mail e tamanho UTF-8 da senha sem enviar dados inválidos', async () => {
  const user = userEvent.setup()
  const porta = criarPorta(master)
  renderizar(porta)
  await screen.findByRole('combobox', { name: 'Perfil' })

  await user.type(screen.getByRole('textbox', { name: 'E-mail do usuário' }), 'invalido')
  await user.type(screen.getByLabelText(/Senha inicial/), 'curta')
  await user.click(screen.getByRole('button', { name: 'Criar usuário' }))

  expect(screen.getByText('Informe um e-mail válido.')).toBeVisible()
  expect(screen.getByText('A senha deve ter de 12 a 72 bytes UTF-8.')).toBeVisible()
  expect(porta.criar).not.toHaveBeenCalled()
})
