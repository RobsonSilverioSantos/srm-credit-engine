import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { Box, Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, Stack, Typography } from '@mui/material'
import type { PortaGestaoUsuarios } from '../aplicacao/gestaoUsuarios'
import { cadastrarParaLiquidacao, confirmarLiquidacao, ErroOperacao, mensagemErro, type PortaOperacoes } from '../aplicacao/operacoes'
import type { Cedente, IntencaoLiquidacao, Liquidacao, Modalidade, Moeda, Sessao, Simulacao } from '../dominio/operacoes'
import { PaginaAdministracaoUsuarios } from './PaginaAdministracaoUsuarios'
import { CampoFormulario } from '../tema/componentes/CampoFormulario'
import { Carregando, EstadoVazio, Mensagem } from '../tema/componentes/Feedback'
import { Painel } from '../tema/componentes/Painel'
import { formatarMonetario, formatarPercentual, formatarCotacao, normalizarEntradaMonetaria } from '../dominio/formatacaoMonetaria'

const grade = { display: 'grid', gap: 2, gridTemplateColumns: { xs: 'minmax(0, 1fr)', sm: 'repeat(2, minmax(0, 1fr))' } }
const modalidades = [
  { valor: 'DUPLICATA_MERCANTIL', rotulo: 'Duplicata mercantil' },
  { valor: 'CHEQUE_PRE_DATADO', rotulo: 'Cheque pré-datado' },
]
const moedas = [{ valor: 'BRL', rotulo: 'BRL' }, { valor: 'USD', rotulo: 'USD' }]

type Props = { operacoes: PortaOperacoes; gestaoUsuarios: PortaGestaoUsuarios; locale: string }

export function Aplicacao({ operacoes, gestaoUsuarios, locale }: Props) {
  const [sessao, setSessao] = useState<Sessao | null>(null)
  const [email, setEmail] = useState('')
  const [identidade, setIdentidade] = useState('')
  const [senha, setSenha] = useState('')
  const [erro, setErro] = useState('')
  const [entrando, setEntrando] = useState(false)
  const [expirada, setExpirada] = useState(false)

  useEffect(() => {
    if (!sessao) return
    const timer = window.setTimeout(() => { setSessao(null); setExpirada(true) }, Math.max(0, sessao.expiraEm - Date.now()))
    return () => window.clearTimeout(timer)
  }, [sessao])

  async function entrar(evento: FormEvent) {
    evento.preventDefault()
    if (entrando) return
    setEntrando(true)
    setErro('')
    try {
      const novaSessao = await operacoes.autenticar(email, senha)
      setIdentidade(email.trim())
      setSessao(novaSessao)
      setSenha('')
      setExpirada(false)
    } catch (falha) {
      setErro(mensagemErro(falha))
    } finally {
      setEntrando(false)
    }
  }

  const expirarSessao = useCallback(() => {
    setSessao(null)
    setExpirada(true)
  }, [])

  const tratarErro = useCallback((falha: unknown) => {
    if (falha instanceof ErroOperacao && falha.status === 401) expirarSessao()
    return mensagemErro(falha)
  }, [expirarSessao])

  return (
    <Box component="main" sx={{ maxWidth: 1120, mx: 'auto', p: { xs: 2, md: 4 } }}>
      <Typography component="h1" variant="h1" gutterBottom>SRM Credit Engine</Typography>
      {!sessao && (
        <Painel id="acesso" titulo="Acesso">
          {expirada && <Mensagem severity="warning">Sessão expirada. Entre novamente com a mesma conta para continuar a operação em andamento.</Mensagem>}
          <Stack component="form" onSubmit={entrar} spacing={2}>
            <CampoFormulario label="E-mail" type="email" required autoComplete="username" value={email}
              disabled={Boolean(identidade)} onChange={(e) => setEmail(e.target.value)} />
            <CampoFormulario label="Senha" type="password" required autoComplete="current-password" value={senha}
              onChange={(e) => setSenha(e.target.value)} />
            {erro && <Mensagem severity="error">{erro}</Mensagem>}
            <Box><Button type="submit" variant="contained" disabled={entrando}>{entrando ? 'Entrando…' : 'Entrar'}</Button></Box>
          </Stack>
        </Painel>
      )}
      <Box hidden={!sessao}>
        <PainelOperacoes operacoes={operacoes} token={sessao?.token ?? null} tratarErro={tratarErro} locale={locale} />
        {sessao && <PaginaAdministracaoUsuarios key={sessao.token} porta={gestaoUsuarios} token={sessao.token} aoExpirar={expirarSessao} />}
      </Box>
    </Box>
  )
}

type PainelProps = Pick<Props, 'operacoes' | 'locale'> & { token: string | null; tratarErro: (erro: unknown) => string }
type Previa = { entrada: string; dados: Simulacao }

function PainelOperacoes({ operacoes, token, tratarErro, locale }: PainelProps) {
  const [cedentes, setCedentes] = useState<Cedente[] | null>(null)
  const [erroCedentes, setErroCedentes] = useState('')
  const [atualizacao, setAtualizacao] = useState(0)
  const [nome, setNome] = useState('')
  const [documento, setDocumento] = useState('')
  const [cedenteId, setCedenteId] = useState('')
  const [modalidade, setModalidade] = useState<Modalidade>('DUPLICATA_MERCANTIL')
  const [valor, setValor] = useState('')
  const [prazo, setPrazo] = useState('')
  const [moeda, setMoeda] = useState<Moeda>('BRL')
  const [vencimento, setVencimento] = useState('')
  const [previa, setPrevia] = useState<Previa | null>(null)
  const [estadoPrevia, setEstadoPrevia] = useState<'vazio' | 'carregando' | 'pronto' | 'erro'>('vazio')
  const [erroPrevia, setErroPrevia] = useState('')
  const [tentativaPrevia, setTentativaPrevia] = useState(0)
  const [ocupado, setOcupado] = useState(false)
  const [erro, setErro] = useState('')
  const [sucesso, setSucesso] = useState('')
  const [incerto, setIncerto] = useState(false)
  const [pendente, setPendente] = useState<IntencaoLiquidacao | null>(null)
  const [confirmando, setConfirmando] = useState(false)
  const [liquidacao, setLiquidacao] = useState<Liquidacao | null>(null)
  const trava = useRef(false)

  const entradaMonetaria = normalizarEntradaMonetaria(valor, locale)
  const valorCanonico = entradaMonetaria.valida ? entradaMonetaria.valorCanonico : ''
  const valido = entradaMonetaria.valida
    && /^[1-9][0-9]*$/.test(prazo) && Number(prazo) <= 2147483647
  const chaveEntrada = JSON.stringify({ modalidade, valorFaceBrl: valorCanonico, prazoMeses: Number(prazo), moeda })

  useEffect(() => {
    if (!token) return
    let ativa = true
    operacoes.listarCedentes(token).then((dados) => {
      if (ativa) { setCedentes(dados); setErroCedentes('') }
    }).catch((falha: unknown) => {
      if (ativa) { setCedentes(null); setErroCedentes(tratarErro(falha)) }
    })
    return () => { ativa = false }
  }, [operacoes, token, atualizacao, tratarErro])

  useEffect(() => {
    if (!token || !valido || pendente || incerto || liquidacao) return
    const controlador = new AbortController()
    const timer = window.setTimeout(() => {
      setEstadoPrevia('carregando')
      operacoes.simular(token, {
        modalidade,
        valorFaceBrl: valorCanonico,
        prazoMeses: Number(prazo),
        moeda,
      }, controlador.signal).then((dados) => {
        if (!controlador.signal.aborted) {
          setPrevia({ entrada: chaveEntrada, dados })
          setEstadoPrevia('pronto')
        }
      }).catch((falha: unknown) => {
        if (!controlador.signal.aborted) {
          setPrevia(null)
          setErroPrevia(tratarErro(falha))
          setEstadoPrevia('erro')
        }
      })
    }, 500)
    return () => { window.clearTimeout(timer); controlador.abort() }
  }, [operacoes, token, valido, chaveEntrada, modalidade, valorCanonico, prazo, moeda, pendente, incerto, liquidacao, tentativaPrevia, tratarErro])

  function alterou() {
    setPrevia(null)
    setEstadoPrevia('vazio')
    setErroPrevia('')
    setErro('')
  }

  async function cadastrarCedente(evento: FormEvent) {
    evento.preventDefault()
    if (!token || trava.current) return
    trava.current = true
    setOcupado(true)
    setErro('')
    setSucesso('')
    try {
      const novo = await operacoes.cadastrarCedente(token, nome.trim(), documento.trim())
      setCedenteId(novo.id)
      setNome('')
      setDocumento('')
      setSucesso('Cedente cadastrado.')
      setAtualizacao((n) => n + 1)
    } catch (falha) {
      setErro(`${tratarErro(falha)} Confira a lista de cedentes antes de tentar cadastrar novamente.`)
      setAtualizacao((n) => n + 1)
    } finally {
      trava.current = false
      setOcupado(false)
    }
  }

  async function cadastrarRecebivel() {
    if (!token || !cedenteId || !previa || previa.entrada !== chaveEntrada || trava.current) return
    trava.current = true
    setOcupado(true)
    setErro('')
    setSucesso('')
    try {
      const chave = crypto.randomUUID()
      const intencao = await cadastrarParaLiquidacao(operacoes, token, {
        cedenteId, modalidade, valorFaceBrl: valorCanonico, prazoMeses: Number(prazo), vencimento: vencimento || null,
      }, moeda, chave)
      setPendente(intencao)
      setSucesso('Recebível cadastrado. Confirme a liquidação para prosseguir.')
    } catch (falha) {
      const conclusivo = falha instanceof ErroOperacao && [400, 401, 403, 404, 409].includes(falha.status ?? 0)
      if (!conclusivo) setIncerto(true)
      setErro(`${tratarErro(falha)}${conclusivo ? '' : ' O resultado do cadastro é incerto. Não repita o cadastro antes de verificar os recebíveis no servidor.'}`)
    } finally {
      trava.current = false
      setOcupado(false)
    }
  }

  async function liquidar() {
    if (!token || !pendente || trava.current) return
    trava.current = true
    setOcupado(true)
    setErro('')
    setSucesso('')
    setConfirmando(false)
    try {
      const resultado = await confirmarLiquidacao(operacoes, token, pendente)
      setLiquidacao(resultado)
      setSucesso('Liquidação confirmada.')
    } catch (falha) {
      setErro(`${tratarErro(falha)} A mesma chave será usada ao tentar novamente.`)
    } finally {
      trava.current = false
      setOcupado(false)
    }
  }

  const bloqueado = ocupado || Boolean(pendente) || incerto || Boolean(liquidacao)
  const previaAtual = previa?.entrada === chaveEntrada ? previa.dados : null
  return (
    <Stack spacing={3} sx={{ mt: 3 }}>
      <Typography color="text.secondary">Simule antes de cadastrar e liquidar um recebível.</Typography>
      <Painel id="cedentes" titulo="Cedente">
        <Stack component="form" onSubmit={cadastrarCedente} spacing={2}>
          <Box sx={grade}>
            <CampoFormulario label="Nome do cedente" required value={nome} disabled={bloqueado}
              slotProps={{ htmlInput: { maxLength: 160 } }} onChange={(e) => setNome(e.target.value)} />
            <CampoFormulario label="Documento do cedente" required value={documento} disabled={bloqueado}
              slotProps={{ htmlInput: { maxLength: 32 } }} onChange={(e) => setDocumento(e.target.value)} />
          </Box>
          <Box><Button type="submit" variant="outlined" disabled={bloqueado}>Cadastrar cedente</Button></Box>
        </Stack>
        {erroCedentes && <Mensagem severity="error">{erroCedentes} <Button onClick={() => setAtualizacao((n) => n + 1)}>Tentar novamente</Button></Mensagem>}
        {!cedentes && !erroCedentes && <Carregando rotulo="Carregando cedentes…" />}
        {cedentes && !cedentes.some((c) => c.ativo) && <EstadoVazio titulo="Nenhum cedente ativo" descricao="Cadastre um cedente para continuar." />}
        {cedentes?.some((c) => c.ativo) && <CampoFormulario label="Cedente" value={cedenteId} disabled={bloqueado}
          opcoes={cedentes.filter((c) => c.ativo).map((c) => ({ valor: c.id, rotulo: `${c.nome} — ${c.documento}` }))}
          onChange={(e) => setCedenteId(e.target.value)} />}
      </Painel>
      <Painel id="simulacao" titulo="Simulação pré-cadastro">
        <Mensagem severity="warning">Simulação indicativa, sem garantia de taxas ou câmbio até a liquidação. Nenhum recebível é criado nesta etapa.</Mensagem>
        <Box sx={grade}>
          <CampoFormulario label="Modalidade" value={modalidade} opcoes={modalidades} disabled={bloqueado}
            onChange={(e) => { setModalidade(e.target.value as Modalidade); alterou() }} />
          <CampoFormulario label="Valor de face em BRL" value={valor} required disabled={bloqueado}
            type="text" inputMode="decimal"
            helperText={valor && !entradaMonetaria.valida
              ? entradaMonetaria.erro
              : locale.toLowerCase().startsWith('en') ? 'Example: 1,234.56.' : 'Exemplo: 1.234,56.'}
            erro={valor && !entradaMonetaria.valida ? entradaMonetaria.erro : undefined}
            onChange={(e) => { setValor(e.target.value); alterou() }} />
          <CampoFormulario label="Prazo em meses" value={prazo} required disabled={bloqueado} type="number"
            slotProps={{ htmlInput: { min: 1, step: 1 } }} onChange={(e) => { setPrazo(e.target.value); alterou() }} />
          <CampoFormulario label="Moeda de pagamento" value={moeda} opcoes={moedas} disabled={bloqueado}
            onChange={(e) => { setMoeda(e.target.value as Moeda); alterou() }} />
          <CampoFormulario label="Vencimento informativo" type="date" value={vencimento} disabled={bloqueado}
            slotProps={{ inputLabel: { shrink: true } }} onChange={(e) => setVencimento(e.target.value)} />
        </Box>
        {!valido && <EstadoVazio titulo="Aguardando dados válidos" descricao="Informe valor positivo e prazo inteiro em meses para simular." />}
        {valido && estadoPrevia === 'vazio' && !pendente && !incerto && !liquidacao && <Carregando rotulo="Aguardando simulação…" />}
        {estadoPrevia === 'carregando' && <Carregando rotulo="Simulando…" />}
        {estadoPrevia === 'erro' && <Mensagem severity="error">{erroPrevia} <Button onClick={() => { alterou(); setTentativaPrevia((n) => n + 1) }}>Tentar novamente</Button></Mensagem>}
        {previaAtual && <Valores dados={previaAtual} locale={locale} />}
        <Box><Button variant="contained" disabled={bloqueado || !cedenteId || !previaAtual || estadoPrevia !== 'pronto'}
          onClick={cadastrarRecebivel}>Cadastrar recebível</Button></Box>
      </Painel>
      {sucesso && <Mensagem severity="success">{sucesso}</Mensagem>}
      {erro && <Mensagem severity="error">{erro}</Mensagem>}
      {ocupado && <Carregando rotulo="Processando operação…" />}
      {pendente && !liquidacao && <Painel id="liquidacao" titulo="Liquidação">
        <Typography>Recebível: {pendente.id}. A liquidação será recalculada com as taxas vigentes.</Typography>
        <Box><Button variant="contained" disabled={ocupado} onClick={() => setConfirmando(true)}>Confirmar liquidação</Button></Box>
      </Painel>}
      {liquidacao && <Painel id="resultado" titulo="Liquidação confirmada">
        <Typography>Identificador: {liquidacao.liquidacaoId}</Typography>
        <Valores dados={liquidacao} locale={locale} />
        <Box><Button onClick={() => {
          setPendente(null); setLiquidacao(null); setValor(''); setPrazo(''); setVencimento('')
          setPrevia(null); setEstadoPrevia('vazio'); setSucesso(''); setErro('')
        }}>Nova operação</Button></Box>
      </Painel>}
      <Dialog open={confirmando} onClose={() => setConfirmando(false)} aria-labelledby="confirmar-titulo">
        <DialogTitle id="confirmar-titulo">Confirmar liquidação?</DialogTitle>
        <DialogContent><DialogContentText>O valor será calculado novamente pelo servidor e pode diferir da simulação indicativa.</DialogContentText></DialogContent>
        <DialogActions><Button onClick={() => setConfirmando(false)}>Cancelar</Button><Button onClick={liquidar} disabled={ocupado}>Liquidar</Button></DialogActions>
      </Dialog>
    </Stack>
  )
}

function Valores({ dados, locale }: { dados: Simulacao | Liquidacao; locale: string }) {
  return <Stack spacing={0.5} aria-label="Valores retornados pelo servidor">
    <Typography>Valor presente: {formatarMonetario(dados.valorPresenteBrl, 'BRL', locale)}</Typography>
    <Typography>Deságio: {formatarMonetario(dados.desagioBrl, 'BRL', locale)}</Typography>
    <Typography>Pagamento: {formatarMonetario(dados.valorPagamento, dados.moeda, locale)}</Typography>
    {'taxaBaseMensal' in dados && <>
      <Typography>Taxa base mensal: {formatarPercentual(dados.taxaBaseMensal, locale)}</Typography>
      <Typography>Spread mensal: {formatarPercentual(dados.spreadMensal, locale)}</Typography>
      {dados.cotacaoBrlPorUsd && <Typography>Cotação BRL/USD: {formatarCotacao(dados.cotacaoBrlPorUsd, locale)}</Typography>}
      <Typography>Referência: {dados.referencia}</Typography>
      {dados.cotacaoValidaAte && <Typography>Cotação válida até: {dados.cotacaoValidaAte}</Typography>}
    </>}
  </Stack>
}
