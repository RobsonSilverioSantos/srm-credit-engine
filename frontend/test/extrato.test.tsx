import { ThemeProvider } from '@mui/material/styles'
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, test, vi } from 'vitest'
import { SessaoExpirada, consultarExtrato, validarFiltros, type ConsultaExtrato } from '../src/aplicacao/extrato'
import { AcessoExtrato, PaginaExtrato } from '../src/apresentacao/PaginaExtrato'
import type { ItemExtrato, PaginaExtrato as Pagina } from '../src/dominio/extrato'
import { autenticacaoHttp, bffExtrato } from '../src/infraestrutura/bffExtrato'
import { tema } from '../src/tema/tema'

const filtros = { inicio: '', fim: '', cedenteId: '', moeda: '' } as const
const consulta: ConsultaExtrato = { ...filtros, pagina: 0, tamanho: 20 }
const item: ItemExtrato = {
  liquidacaoId: 'liquidacao-1', recebivelId: 'recebivel-1', cedenteId: 'cedente-1', cedenteNome: 'Cedente exemplo', modalidade: 'DUPLICATA_MERCANTIL',
  valorFaceBrl: '100000.00', prazoMeses: 3, versaoRecebivel: 1, taxaBaseId: 'taxa-1',
  taxaBaseMensal: '0.0100', taxaBaseVigenteEm: '2026-01-01T00:00:00Z', spreadId: 'spread-1',
  spreadMensal: '0.0150', spreadVigenteEm: '2026-01-01T00:00:00Z', valorPresenteBrl: '92859.94',
  desagioBrl: '7140.06', moeda: 'USD', cotacaoCambialId: 'cotacao-1', taxaBrlPorUnidade: '5.4321',
  cotacaoVigenteEm: '2026-01-01T00:00:00Z', cotacaoValidaAte: '2026-01-01T00:30:00Z',
  valorPagamento: '17094.67', liquidadaEm: '2026-01-01T00:10:00Z',
}
const pagina: Pagina = { itens: [item], pagina: 0, tamanho: 20, total: 25 }
const resposta = (dados: unknown, status = 200) => new Response(JSON.stringify(dados), { status, headers: { 'Content-Type': 'application/json' } })

afterEach(() => vi.restoreAllMocks())

test('valida datas reais e ordem, UUID, moeda e limites de paginação', () => {
  expect(validarFiltros({ ...filtros, inicio: '2026-02-30', cedenteId: 'inválido' })).toEqual({
    inicio: 'Informe uma data válida.', cedenteId: 'Informe um UUID de cedente válido.',
  })
  expect(validarFiltros({ ...filtros, inicio: '2026-02-01', fim: '2026-01-31' })).toHaveProperty('fim')
  expect(validarFiltros({ ...filtros, inicio: '2024-02-29', fim: '2024-02-29', cedenteId: '00000000-0000-4000-8000-000000000001', moeda: 'USD' })).toEqual({})
  const porta = { consultar: vi.fn() }
  expect(() => consultarExtrato(porta, { ...consulta, tamanho: 101 }, 'token', new AbortController().signal)).toThrow('inválidos')
  expect(porta.consultar).not.toHaveBeenCalled()
})

test('adaptador envia filtros, paginação e bearer sem mudar valores ou ordenação', async () => {
  const segundo = { ...item, liquidacaoId: 'liquidacao-2', valorPagamento: '9999999999999999.99' }
  const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(resposta({ ...pagina, itens: [item, segundo] }))
  const signal = new AbortController().signal
  const resultado = await bffExtrato.consultar({ ...consulta, inicio: '2026-01-01', fim: '2026-01-31', cedenteId: '00000000-0000-4000-8000-000000000001', moeda: 'USD' }, 'jwt', signal)
  expect(resultado.itens.map((linha) => linha.valorPagamento)).toEqual(['17094.67', '9999999999999999.99'])
  const [url, opcoes] = fetchMock.mock.calls[0]!
  const parametros = new URL(String(url), 'http://localhost').searchParams
  expect(Object.fromEntries(parametros)).toEqual({ pagina: '0', tamanho: '20', inicio: '2026-01-01', fim: '2026-01-31', cedenteId: '00000000-0000-4000-8000-000000000001', moeda: 'USD' })
  expect(opcoes).toEqual({ headers: { Authorization: 'Bearer jwt' }, signal })
})

test('adaptadores rejeitam expiração, erros HTTP e respostas malformadas', async () => {
  const fetchMock = vi.spyOn(globalThis, 'fetch')
  fetchMock.mockResolvedValueOnce(resposta({}, 401))
  await expect(bffExtrato.consultar(consulta, 'jwt', new AbortController().signal)).rejects.toBeInstanceOf(SessaoExpirada)
  fetchMock.mockResolvedValueOnce(resposta({}, 503))
  await expect(bffExtrato.consultar(consulta, 'jwt', new AbortController().signal)).rejects.toThrow('HTTP 503')
  fetchMock.mockResolvedValueOnce(resposta({ ...pagina, tamanho: 10 }))
  await expect(bffExtrato.consultar(consulta, 'jwt', new AbortController().signal)).rejects.toThrow('Resposta inválida')
  fetchMock.mockResolvedValueOnce(resposta({ ...pagina, itens: [{ ...item, valorPagamento: 42 }] }))
  await expect(bffExtrato.consultar(consulta, 'jwt', new AbortController().signal)).rejects.toThrow('Resposta inválida')
  fetchMock.mockResolvedValueOnce(resposta({ ...pagina, itens: [{ ...item, cedenteNome: null }] }))
  await expect(bffExtrato.consultar(consulta, 'jwt', new AbortController().signal)).rejects.toThrow('Resposta inválida')
  fetchMock.mockResolvedValueOnce(resposta({ accessToken: 'jwt', tokenType: 'Bearer', expiresIn: 60 }))
  await expect(autenticacaoHttp.autenticar('a@b.com', 'senha', new AbortController().signal)).resolves.toBe('jwt')
  expect(fetchMock.mock.calls[5]?.[0]).toBe('/api/v1/auth/token')
})

function renderizar(ui: React.ReactNode) {
  return render(<ThemeProvider theme={tema}>{ui}</ThemeProvider>)
}

test('filtros e paginação são enviados ao servidor; snapshots mantêm os valores recebidos', async () => {
  const user = userEvent.setup()
  const porta = { consultar: vi.fn(async (pedido: ConsultaExtrato) => ({ ...pagina, pagina: pedido.pagina, tamanho: pedido.tamanho })) }
  renderizar(<PaginaExtrato porta={porta} token="jwt" locale="pt-BR" fusoHorario="America/Sao_Paulo" aoExpirar={vi.fn()} />)
  expect(await screen.findByText(/17\.094,67/)).toBeVisible()
  expect(screen.getByText('Código da liquidação')).toBeVisible()
  expect(screen.getByText('liquidacao-1')).toBeVisible()
  expect(screen.getByText('Cedente exemplo')).toBeVisible()
  expect(screen.getByText('Duplicata mercantil')).toBeVisible()
  expect(screen.queryByText('cedente-1')).not.toBeInTheDocument()
  const instanteLiquidacao = screen.getByText('31/12/2025 21:10:00 (America/Sao_Paulo)')
  expect(instanteLiquidacao).toHaveAttribute('dateTime', item.liquidadaEm)
  expect(instanteLiquidacao).toHaveAttribute('title', `ISO 8601 UTC: ${item.liquidadaEm}`)
  const botao = screen.getByRole('button', { name: 'Ver detalhes da liquidação liquidacao-1' })
  await user.click(botao)
  const dialogo = screen.getByRole('dialog', { name: 'Detalhes da liquidação' })
  for (const rotulo of ['Código da liquidação', 'Cedente', 'ID do cedente', 'Recebível', 'Modalidade',
    'Versão do recebível', 'Prazo (meses)', 'Valor de face BRL', 'Valor presente BRL', 'Deságio BRL',
    'Moeda', 'Pagamento', 'Taxa base ID', 'Taxa base mensal', 'Taxa base vigente em', 'Spread ID',
    'Spread mensal', 'Spread vigente em', 'Cotação ID', 'BRL por unidade', 'Cotação vigente em',
    'Cotação válida até', 'Data/hora da liquidação']) {
    expect(within(dialogo).getByText(rotulo, { selector: 'dt' })).toBeVisible()
  }
  expect(porta.consultar).toHaveBeenCalledTimes(1)
  expect(within(dialogo).getByText('cedente-1')).toBeVisible()
  expect(within(dialogo).getByText('5,4321')).toBeVisible()
  expect(within(dialogo).getByText('1,00%')).toBeVisible()
  expect(within(dialogo).getAllByText('31/12/2025 21:00:00 (America/Sao_Paulo)')).toHaveLength(3)
  expect(within(dialogo).getAllByText('31/12/2025 21:00:00 (America/Sao_Paulo)')[0]).toHaveAttribute('dateTime', '2026-01-01T00:00:00Z')
  await user.keyboard('{Escape}')
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  expect(botao).toHaveFocus()
  await user.click(screen.getByRole('button', { name: 'Próxima página' }))
  await waitFor(() => expect(porta.consultar).toHaveBeenLastCalledWith(expect.objectContaining({ pagina: 1, tamanho: 20 }), 'jwt', expect.any(AbortSignal)))
  expect(await screen.findByText('21–25 de 25')).toBeVisible()
  await user.type(screen.getByRole('textbox', { name: 'ID do cedente' }), 'inválido')
  await user.click(screen.getByRole('button', { name: 'Aplicar filtros' }))
  expect(screen.getByText('Informe um UUID de cedente válido.')).toBeVisible()
  expect(porta.consultar).toHaveBeenCalledTimes(2)
  await user.clear(screen.getByRole('textbox', { name: 'ID do cedente' }))
  await user.click(screen.getByRole('combobox', { name: 'Moeda' }))
  await user.click(screen.getByRole('option', { name: 'USD' }))
  await user.click(screen.getByRole('button', { name: 'Aplicar filtros' }))
  await waitFor(() => expect(porta.consultar).toHaveBeenLastCalledWith(expect.objectContaining({ pagina: 0, moeda: 'USD' }), 'jwt', expect.any(AbortSignal)))
  await user.click(screen.getByRole('combobox', { name: 'Itens por página' }))
  await user.click(screen.getByRole('option', { name: '10' }))
  await waitFor(() => expect(porta.consultar).toHaveBeenLastCalledWith(expect.objectContaining({ pagina: 0, tamanho: 10, moeda: 'USD' }), 'jwt', expect.any(AbortSignal)))
  await user.click(screen.getByRole('button', { name: 'Próxima página' }))
  await waitFor(() => expect(porta.consultar).toHaveBeenLastCalledWith(expect.objectContaining({ pagina: 1, tamanho: 10 }), 'jwt', expect.any(AbortSignal)))
  await user.click(screen.getByRole('button', { name: 'Página anterior' }))
  await waitFor(() => expect(porta.consultar).toHaveBeenLastCalledWith(expect.objectContaining({ pagina: 0, tamanho: 10 }), 'jwt', expect.any(AbortSignal)))
})

test('diálogo mostra snapshot BRL e fecha ao trocar filtros, tela ou sessão', async () => {
  const user = userEvent.setup()
  const brl = { ...item, modalidade: 'CHEQUE_PRE_DATADO', moeda: 'BRL' as const, cotacaoCambialId: null,
    taxaBrlPorUnidade: null, cotacaoVigenteEm: null, cotacaoValidaAte: null }
  const porta = { consultar: vi.fn().mockResolvedValue({ ...pagina, itens: [brl] }) }
  const aoExpirar = vi.fn()
  const props = { porta, token: 'jwt', locale: 'pt-BR', aoExpirar }
  const tela = renderizar(<PaginaExtrato {...props} />)
  expect(await screen.findByText('Cheque pré-datado')).toBeVisible()
  const abrir = () => user.click(screen.getByRole('button', { name: 'Ver detalhes da liquidação liquidacao-1' }))
  await abrir()
  expect(within(screen.getByRole('dialog')).getAllByText('Não informado')).toHaveLength(4)
  await user.click(screen.getByRole('button', { name: 'Fechar' }))
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  const aplicar = screen.getByRole('button', { name: 'Aplicar filtros' })
  await abrir()
  fireEvent.click(aplicar)
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  await screen.findByText('Cheque pré-datado')
  const proxima = screen.getByRole('button', { name: 'Próxima página' })
  await abrir()
  fireEvent.click(proxima)
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  await screen.findByText('Cheque pré-datado')
  await abrir()
  tela.rerender(<ThemeProvider theme={tema}><PaginaExtrato {...props} ativa={false} /></ThemeProvider>)
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  tela.rerender(<ThemeProvider theme={tema}><PaginaExtrato {...props} ativa /></ThemeProvider>)
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  await abrir()
  tela.rerender(<ThemeProvider theme={tema}><PaginaExtrato {...props} token="outro" /></ThemeProvider>)
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
})

test('ignora respostas antigas mesmo quando o adaptador não respeita abort', async () => {
  const user = userEvent.setup()
  let resolverPrimeira: (pagina: Pagina) => void = () => { throw new Error('requisição não iniciada') }
  const porta = { consultar: vi.fn()
    .mockImplementationOnce(() => new Promise<Pagina>((resolve) => { resolverPrimeira = resolve }))
    .mockResolvedValueOnce({ ...pagina, itens: [] , total: 0 }) }
  renderizar(<PaginaExtrato porta={porta} token="jwt" locale="pt-BR" aoExpirar={vi.fn()} />)
  expect(screen.getByRole('status')).toHaveTextContent('Carregando')
  await user.click(screen.getByRole('button', { name: 'Aplicar filtros' }))
  expect(await screen.findByText('Nenhuma liquidação encontrada')).toBeVisible()
  await act(async () => resolverPrimeira(pagina))
  expect(screen.queryByText(/17\.094,67/)).not.toBeInTheDocument()
  expect(screen.getByText('Nenhuma liquidação encontrada')).toBeVisible()
})

test('impede período invertido e aplica datas inclusivas ao corrigir', async () => {
  const user = userEvent.setup()
  const porta = { consultar: vi.fn().mockResolvedValue({ ...pagina, itens: [], total: 0 }) }
  renderizar(<PaginaExtrato porta={porta} token="jwt" locale="pt-BR" aoExpirar={vi.fn()} />)
  await screen.findByText('Nenhuma liquidação encontrada')
  fireEvent.change(screen.getByLabelText('Data inicial (UTC)'), { target: { value: '2026-03-10' } })
  fireEvent.change(screen.getByLabelText('Data final (UTC)'), { target: { value: '2026-03-01' } })
  await user.click(screen.getByRole('button', { name: 'Aplicar filtros' }))
  expect(screen.getByText('A data final deve ser igual ou posterior à inicial.')).toBeVisible()
  expect(porta.consultar).toHaveBeenCalledTimes(1)
  fireEvent.change(screen.getByLabelText('Data final (UTC)'), { target: { value: '2026-03-10' } })
  await user.click(screen.getByRole('button', { name: 'Aplicar filtros' }))
  await waitFor(() => expect(porta.consultar).toHaveBeenLastCalledWith(expect.objectContaining({ inicio: '2026-03-10', fim: '2026-03-10', pagina: 0 }), 'jwt', expect.any(AbortSignal)))
})

test('exibe erro e permite retentativa; ajusta página fora do total atualizado', async () => {
  const user = userEvent.setup()
  const porta = { consultar: vi.fn()
    .mockRejectedValueOnce(new Error('Serviço indisponível'))
    .mockResolvedValueOnce(pagina)
    .mockResolvedValueOnce({ ...pagina, itens: [], pagina: 1, total: 1 })
    .mockResolvedValueOnce({ ...pagina, total: 1 }) }
  renderizar(<PaginaExtrato porta={porta} token="jwt" locale="pt-BR" aoExpirar={vi.fn()} />)
  expect(await screen.findByRole('alert')).toHaveTextContent('Serviço indisponível')
  await user.click(screen.getByRole('button', { name: 'Tentar novamente' }))
  expect(await screen.findByText(/17\.094,67/)).toBeVisible()
  await user.click(screen.getByRole('button', { name: 'Próxima página' }))
  await waitFor(() => expect(porta.consultar).toHaveBeenCalledTimes(4))
  expect(await screen.findByText('1–1 de 1')).toBeVisible()
})

test('expiração abandona dados e solicita nova autenticação', async () => {
  const user = userEvent.setup()
  const autenticacao = { autenticar: vi.fn().mockResolvedValue('jwt') }
  const porta = { consultar: vi.fn().mockRejectedValue(new SessaoExpirada()) }
  renderizar(<AcessoExtrato autenticacao={autenticacao} porta={porta} locale="pt-BR" />)
  await user.type(screen.getByRole('textbox', { name: 'E-mail' }), 'operador@example.com')
  await user.type(screen.getByLabelText(/Senha/), 'senha')
  await user.click(screen.getByRole('button', { name: 'Entrar' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Sessão expirada')
  expect(screen.getByLabelText(/Senha/)).toHaveValue('')
  expect(within(screen.getByRole('main')).queryByRole('table')).not.toBeInTheDocument()
})
