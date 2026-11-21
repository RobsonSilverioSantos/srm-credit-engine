import { afterEach, expect, test, vi } from 'vitest'
import { ErroGestaoUsuarios } from '../src/aplicacao/gestaoUsuarios'
import { bffUsuarios } from '../src/infraestrutura/bffUsuarios'

const conta = {
  id: 'usuario-master',
  organizacaoId: 'organizacao-1',
  email: 'master@example.com',
  perfil: 'MASTER',
  ativo: true,
  capacidades: { perfisCriaveis: ['ADMIN', 'OPERADOR'], podeListarUsuarios: true, podeAlterarStatus: true },
}
const usuario = { id: 'usuario-1', email: 'admin@example.com', perfil: 'ADMIN', ativo: true }
const resposta = (dados: unknown, status = 200) =>
  new Response(JSON.stringify(dados), { status, headers: { 'Content-Type': 'application/json' } })

afterEach(() => vi.unstubAllGlobals())

test('consulta capacidades e lista os usuários somente pelo BFF', async () => {
  const fetchMock = vi.fn()
    .mockResolvedValueOnce(resposta(conta))
    .mockResolvedValueOnce(resposta([usuario]))
  vi.stubGlobal('fetch', fetchMock)
  const signal = new AbortController().signal

  await expect(bffUsuarios.minhaConta('jwt', signal)).resolves.toMatchObject({
    id: 'usuario-master', perfil: 'MASTER', capacidades: { perfisCriaveis: ['ADMIN', 'OPERADOR'] },
  })
  await expect(bffUsuarios.listar('jwt', signal)).resolves.toEqual([usuario])
  expect(fetchMock.mock.calls).toEqual([
    ['/bff/v1/usuarios/me', { headers: { Authorization: 'Bearer jwt' }, signal }],
    ['/bff/v1/usuarios', { headers: { Authorization: 'Bearer jwt' }, signal }],
  ])
})

test('cria e altera status sem aceitar organizacaoId ou campos fora do contrato', async () => {
  const fetchMock = vi.fn()
    .mockResolvedValueOnce(resposta(usuario, 201))
    .mockResolvedValueOnce(resposta({ ...usuario, ativo: false }))
  vi.stubGlobal('fetch', fetchMock)
  const signal = new AbortController().signal

  await bffUsuarios.criar('jwt', { email: 'admin@example.com', senha: 'senha inicial', perfil: 'ADMIN' }, signal)
  await bffUsuarios.alterarStatus('jwt', 'user/id', false, signal)

  expect(fetchMock.mock.calls).toEqual([
    ['/bff/v1/usuarios', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer jwt' },
      body: '{"email":"admin@example.com","senha":"senha inicial","perfil":"ADMIN"}',
      signal,
    }],
    ['/bff/v1/usuarios/user%2Fid/status', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer jwt' },
      body: '{"ativo":false}',
      signal,
    }],
  ])
})

test.each([
  [401, 'Sessão expirada'],
  [403, 'não tem permissão'],
])('traduz erro HTTP %i em mensagem específica sem expor o corpo', async (status, mensagem) => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('sensitive server detail', { status })))
  await expect(bffUsuarios.listar('jwt', new AbortController().signal)).rejects.toMatchObject({
    status,
    message: expect.stringContaining(mensagem),
  } satisfies Partial<ErroGestaoUsuarios>)
  await expect(bffUsuarios.listar('jwt', new AbortController().signal)).rejects.not.toThrow('sensitive')
})

test('traduz conflito de e-mail e conflito de status sem expor o corpo', async () => {
  const fetchMock = vi.fn()
    .mockResolvedValueOnce(new Response('sensitive server detail', { status: 409 }))
    .mockResolvedValueOnce(new Response('sensitive server detail', { status: 409 }))
  vi.stubGlobal('fetch', fetchMock)
  const signal = new AbortController().signal

  await expect(bffUsuarios.criar('jwt', { email: 'duplicado@example.com', senha: 'senha', perfil: 'OPERADOR' }, signal))
    .rejects.toMatchObject({ status: 409, message: expect.stringContaining('Já existe um usuário') })
  await expect(bffUsuarios.alterarStatus('jwt', 'user-1', false, signal))
    .rejects.toMatchObject({ status: 409, message: expect.stringContaining('O estado do usuário mudou') })
})

test('recusa capacidades que tentem liberar criação de MASTER', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(resposta({
    ...conta,
    capacidades: { ...conta.capacidades, perfisCriaveis: ['MASTER'] },
  })))
  await expect(bffUsuarios.minhaConta('jwt', new AbortController().signal)).rejects.toThrow('Resposta inválida')
})
