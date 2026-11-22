import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Box, Button, Container, Dialog, DialogActions, DialogContent, DialogTitle, Stack, TablePagination, Typography } from '@mui/material'
import { consultarExtrato, SessaoExpirada, validarFiltros, type ConsultaExtrato, type PortaAutenticacao, type PortaExtrato } from '../aplicacao/extrato'
import type { FiltrosExtrato, ItemExtrato, PaginaExtrato as Pagina } from '../dominio/extrato'
import { CampoFormulario } from '../tema/componentes/CampoFormulario'
import { Mensagem } from '../tema/componentes/Feedback'
import { Painel } from '../tema/componentes/Painel'
import { TabelaDados, type EstadoTabela } from '../tema/componentes/TabelaDados'
import { formatarCotacao, formatarMonetario, formatarPercentual } from '../dominio/formatacaoMonetaria'
import { InstanteLocal } from '../tema/componentes/InstanteLocal'

const filtrosIniciais: FiltrosExtrato = { inicio: '', fim: '', cedenteId: '', moeda: '' }

const instantesSnapshot = new Set(['Taxa base vigente em', 'Spread vigente em', 'Cotação vigente em', 'Cotação válida até'])
type DetalheSnapshot = readonly [string, string | number | null]

function detalhesSnapshot(item: ItemExtrato, locale: string): readonly (readonly [string, readonly DetalheSnapshot[]])[] {
  return [
    ['Recebível e identidade', [
      ['Código da liquidação', item.liquidacaoId], ['Cedente', item.cedenteNome], ['ID do cedente', item.cedenteId],
      ['Recebível', item.recebivelId], ['Modalidade', modalidade(item.modalidade)],
      ['Versão do recebível', item.versaoRecebivel], ['Prazo (meses)', item.prazoMeses],
    ]],
    ['Valores', [
      ['Valor de face BRL', formatarMonetario(item.valorFaceBrl, 'BRL', locale)],
      ['Valor presente BRL', formatarMonetario(item.valorPresenteBrl, 'BRL', locale)],
      ['Deságio BRL', formatarMonetario(item.desagioBrl, 'BRL', locale)],
      ['Moeda', item.moeda], ['Pagamento', formatarMonetario(item.valorPagamento, item.moeda, locale)],
    ]],
    ['Taxas e spread', [
      ['Taxa base ID', item.taxaBaseId], ['Taxa base mensal', formatarPercentual(item.taxaBaseMensal, locale)], ['Taxa base vigente em', item.taxaBaseVigenteEm],
      ['Spread ID', item.spreadId], ['Spread mensal', formatarPercentual(item.spreadMensal, locale)], ['Spread vigente em', item.spreadVigenteEm],
    ]],
    ['Câmbio', [
      ['Cotação ID', item.cotacaoCambialId],
      ['BRL por unidade', item.taxaBrlPorUnidade === null ? null : formatarCotacao(item.taxaBrlPorUnidade, locale)],
      ['Cotação vigente em', item.cotacaoVigenteEm], ['Cotação válida até', item.cotacaoValidaAte],
    ]],
    ['Liquidação', [['Data/hora da liquidação', item.liquidadaEm]]],
  ]
}

function modalidade(valor: string): string {
  if (valor === 'CHEQUE_PRE_DATADO') return 'Cheque pré-datado'
  if (valor === 'DUPLICATA_MERCANTIL') return 'Duplicata mercantil'
  return valor
}

export function PaginaExtrato({ porta, token, aoExpirar, locale, fusoHorario, ativa = true, mostrarTitulo = true }: {
  porta: PortaExtrato
  token: string
  aoExpirar: () => void
  locale: string
  fusoHorario?: string
  ativa?: boolean
  mostrarTitulo?: boolean
}) {
  const [filtros, setFiltros] = useState(filtrosIniciais)
  const [validado, setValidado] = useState(false)
  const [consulta, setConsulta] = useState<ConsultaExtrato>({ ...filtrosIniciais, pagina: 0, tamanho: 20 })
  const [resultado, setResultado] = useState<Pagina>()
  const [estado, setEstado] = useState<'carregando' | 'pronto' | 'erro'>('carregando')
  const [erro, setErro] = useState('')
  const [tentativa, setTentativa] = useState(0)
  const [selecionado, setSelecionado] = useState<ItemExtrato>()
  const geracao = useRef(0)
  const erros = validado ? validarFiltros(filtros) : {}

  useEffect(() => () => setSelecionado(undefined), [ativa, token])

  useEffect(() => {
    if (!ativa) return
    const controle = new AbortController()
    const atual = ++geracao.current
    let montada = true
    consultarExtrato(porta, consulta, token, controle.signal).then((pagina) => {
      if (!montada || atual !== geracao.current) return
      const ultima = Math.max(0, Math.ceil(pagina.total / pagina.tamanho) - 1)
      if (pagina.pagina > ultima) {
        setConsulta((atual) => ({ ...atual, pagina: ultima }))
        return
      }
      setResultado(pagina)
      setEstado('pronto')
    }).catch((falha: unknown) => {
      if (!montada || atual !== geracao.current) return
      if (falha instanceof SessaoExpirada) {
        aoExpirar()
      } else {
        setErro(falha instanceof Error ? falha.message : 'Falha ao consultar o extrato.')
        setEstado('erro')
      }
    })
    return () => { montada = false; controle.abort() }
  }, [ativa, porta, token, consulta, tentativa, aoExpirar])

  function atualizarConsulta(proxima: ConsultaExtrato) {
    setSelecionado(undefined)
    geracao.current++
    setEstado('carregando')
    setResultado(undefined)
    setConsulta(proxima)
  }

  function aplicar(evento: FormEvent) {
    evento.preventDefault()
    setValidado(true)
    if (Object.keys(validarFiltros(filtros)).length) return
    atualizarConsulta({ ...filtros, pagina: 0, tamanho: consulta.tamanho })
  }

  const tabela: EstadoTabela<ItemExtrato> = estado === 'carregando' ? { tipo: 'carregando' }
    : estado === 'erro' ? { tipo: 'erro', mensagem: erro, acao: <Button color="inherit" onClick={() => { geracao.current++; setEstado('carregando'); setTentativa((n) => n + 1) }}>Tentar novamente</Button> }
      : resultado ? { tipo: 'pronto', linhas: resultado.itens }
        : { tipo: 'erro', mensagem: 'Não foi possível carregar o resultado do extrato.' }

  return (
    <Stack spacing={3}>
      <Box component="header">
        {mostrarTitulo && <Typography variant="h1">Extrato de liquidações</Typography>}
        <Typography color="text.secondary">Filtros de data inclusivos em UTC; horários exibidos no fuso local, com o instante ISO original disponível para auditoria.</Typography>
      </Box>
      <Painel id="filtros-extrato" titulo="Filtros">
        <Box component="form" noValidate onSubmit={aplicar}>
          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))' } }}>
            <CampoFormulario label="Data inicial (UTC)" type="date" slotProps={{ inputLabel: { shrink: true } }} value={filtros.inicio} erro={erros.inicio} onChange={(e) => setFiltros({ ...filtros, inicio: e.target.value })} />
            <CampoFormulario label="Data final (UTC)" type="date" slotProps={{ inputLabel: { shrink: true } }} value={filtros.fim} erro={erros.fim} onChange={(e) => setFiltros({ ...filtros, fim: e.target.value })} />
            <CampoFormulario label="ID do cedente" value={filtros.cedenteId} erro={erros.cedenteId} helperText="UUID do cedente, opcional." onChange={(e) => setFiltros({ ...filtros, cedenteId: e.target.value.trim() })} />
            <CampoFormulario label="Moeda" value={filtros.moeda} erro={erros.moeda} opcoes={[{ valor: '', rotulo: 'Todas' }, { valor: 'BRL', rotulo: 'BRL' }, { valor: 'USD', rotulo: 'USD' }]} onChange={(e) => {
              const moeda = e.target.value
              if (moeda === '' || moeda === 'BRL' || moeda === 'USD') setFiltros({ ...filtros, moeda })
            }} />
          </Box>
          <Button type="submit" variant="contained" sx={{ mt: 2 }}>Aplicar filtros</Button>
        </Box>
      </Painel>
      <Painel id="liquidacoes" titulo="Liquidações">
        <TabelaDados legenda="Liquidações" chaveLinha={(item) => item.liquidacaoId} estado={tabela}
          vazio={{ titulo: 'Nenhuma liquidação encontrada', descricao: 'Altere os filtros ou consulte novamente mais tarde.' }}
          colunas={[
            { id: 'data', rotulo: 'Data/hora da liquidação', renderizar: (item) => <InstanteLocal isoUtc={item.liquidadaEm} locale={locale} rotulo="Data/hora da liquidação" fusoHorario={fusoHorario} /> },
            { id: 'id', rotulo: 'Código da liquidação', cabecalhoLinha: true, renderizar: (item) => item.liquidacaoId },
            { id: 'cedente', rotulo: 'Cedente', renderizar: (item) => <Box sx={{ overflowWrap: 'anywhere' }}>{item.cedenteNome}</Box> },
            { id: 'modalidade', rotulo: 'Modalidade', renderizar: (item) => modalidade(item.modalidade) },
            { id: 'pagamento', rotulo: 'Pagamento', alinhar: 'right', renderizar: (item) => formatarMonetario(item.valorPagamento, item.moeda, locale) },
            { id: 'detalhes', rotulo: 'Snapshot', renderizar: (item) => <Button aria-label={`Ver detalhes da liquidação ${item.liquidacaoId}`} onClick={() => setSelecionado(item)}>Ver detalhes</Button> },
          ]} />
        {estado === 'pronto' && resultado && <TablePagination component="div" count={resultado.total} page={resultado.pagina} rowsPerPage={resultado.tamanho}
          rowsPerPageOptions={[10, 20, 50, 100]} labelRowsPerPage="Itens por página"
          labelDisplayedRows={({ from, to, count }) => `${from}–${to} de ${count}`}
          getItemAriaLabel={(tipo) => tipo === 'next' ? 'Próxima página' : tipo === 'previous' ? 'Página anterior' : tipo === 'first' ? 'Primeira página' : 'Última página'}
          onPageChange={(_, pagina) => atualizarConsulta({ ...consulta, pagina })}
          onRowsPerPageChange={(e) => atualizarConsulta({ ...consulta, pagina: 0, tamanho: Number(e.target.value) })} />}
      </Painel>
      <Dialog open={ativa && !!selecionado} onClose={() => setSelecionado(undefined)} fullWidth maxWidth="sm" scroll="paper" aria-labelledby="detalhes-liquidacao-titulo">
        <DialogTitle id="detalhes-liquidacao-titulo">Detalhes da liquidação</DialogTitle>
        <DialogContent dividers>
          {selecionado && detalhesSnapshot(selecionado, locale).map(([secao, detalhes]) => <Box component="section" key={secao} sx={{ mb: 2 }}>
            <Typography variant="h2">{secao}</Typography>
            <Box component="dl">
              {detalhes.map(([nome, valor]) => <Box key={nome}><Typography component="dt" fontWeight={700}>{nome}</Typography><Typography component="dd" sx={{ ml: 0, overflowWrap: 'anywhere' }}>
                {typeof valor === 'string' && (instantesSnapshot.has(nome) || nome === 'Data/hora da liquidação')
                  ? <InstanteLocal isoUtc={valor} locale={locale} rotulo={nome} fusoHorario={fusoHorario} />
                  : valor ?? 'Não informado'}
              </Typography></Box>)}
            </Box>
          </Box>)}
        </DialogContent>
        <DialogActions><Button onClick={() => setSelecionado(undefined)}>Fechar</Button></DialogActions>
      </Dialog>
    </Stack>
  )
}

export function AcessoExtrato({ autenticacao, porta, locale, fusoHorario }: { autenticacao: PortaAutenticacao; porta: PortaExtrato; locale: string; fusoHorario?: string }) {
  const [token, setToken] = useState('')
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [erro, setErro] = useState('')
  const [entrando, setEntrando] = useState(false)

  async function entrar(evento: FormEvent) {
    evento.preventDefault()
    setEntrando(true)
    setErro('')
    try {
      const novoToken = await autenticacao.autenticar(email, senha, new AbortController().signal)
      setSenha('')
      setToken(novoToken)
    } catch (falha: unknown) {
      setSenha('')
      setErro(falha instanceof Error ? falha.message : 'Falha ao autenticar.')
    } finally {
      setEntrando(false)
    }
  }

  return <Container component="main" maxWidth="lg" sx={{ py: { xs: 3, md: 5 } }}>
    {token ? <PaginaExtrato porta={porta} token={token} locale={locale} fusoHorario={fusoHorario} aoExpirar={() => { setToken(''); setErro('Sessão expirada. Entre novamente.') }} /> :
      <Stack component="form" onSubmit={entrar} spacing={2} sx={{ maxWidth: 420 }}>
        <Typography variant="h1">Acesso ao extrato</Typography>
        {erro && <Mensagem severity="error">{erro}</Mensagem>}
        <CampoFormulario label="E-mail" type="email" autoComplete="username" required value={email} onChange={(e) => setEmail(e.target.value)} />
        <CampoFormulario label="Senha" type="password" autoComplete="current-password" required value={senha} onChange={(e) => setSenha(e.target.value)} />
        <Button type="submit" variant="contained" disabled={entrando}>{entrando ? 'Entrando…' : 'Entrar'}</Button>
      </Stack>}
  </Container>
}
