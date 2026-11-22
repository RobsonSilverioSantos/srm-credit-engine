import { useCallback, useEffect, useLayoutEffect, useRef, useState, type FormEvent } from 'react'
import { Box, Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, Stack, Typography, useMediaQuery, useTheme } from '@mui/material'
import { ErroCambio, executarCambio, type ComandoCambio, type PortaCambio } from '../aplicacao/cambio'
import { ErroGestaoUsuarios, type PortaGestaoUsuarios } from '../aplicacao/gestaoUsuarios'
import { erroJustificativa, formatarEntradaTaxa, normalizarTaxaCambial, podeAdministrarCambio, type CotacaoCambial, type FonteCambial, type SelecaoCambial } from '../dominio/cambio'
import { formatarCotacao } from '../dominio/formatacaoMonetaria'
import type { MinhaConta } from '../dominio/usuarios'
import { CampoFormulario } from '../tema/componentes/CampoFormulario'
import { Carregando, Mensagem } from '../tema/componentes/Feedback'
import { InstanteLocal } from '../tema/componentes/InstanteLocal'
import { Painel } from '../tema/componentes/Painel'
import { HistoricoCambio } from './HistoricoCambio'

type Props = {
  porta: PortaCambio
  usuarios: PortaGestaoUsuarios
  token: string | null
  ativa: boolean
  locale: string
  aoExpirar: () => void
  aoAtualizarOperacaoEmCurso: (valor: boolean) => void
  aoAtualizarOperacaoPendente: (valor: boolean) => void
}

const fontes: { valor: string; rotulo: string }[] = [
  { valor: '', rotulo: 'Todas' }, { valor: 'BCB_PTAX', rotulo: 'BCB PTAX' },
  { valor: 'MANUAL', rotulo: 'MANUAL' }, { valor: 'DEMO', rotulo: 'DEMO (aferição)' }, { valor: 'LEGADO', rotulo: 'LEGADO' },
]
const fuso = 'America/Sao_Paulo'
const grade = { display: 'grid', gap: 2, gridTemplateColumns: { xs: 'minmax(0, 1fr)', sm: 'repeat(2, minmax(0, 1fr))' } }
const mensagemFalha = (falha: unknown) => falha instanceof Error ? falha.message : 'Falha inesperada no câmbio.'

function ValidadeManual({ selecao, locale }: { selecao: SelecaoCambial; locale: string }) {
  const [agora, atualizar] = useState(Date.now)
  useEffect(() => {
    if (!selecao.validaAte) return
    const restante = Date.parse(selecao.validaAte) - Date.now()
    if (restante <= 0) return
    const timer = window.setTimeout(() => atualizar(Date.now()), Math.min(restante, 2_147_483_647))
    return () => window.clearTimeout(timer)
  }, [selecao])
  if (!selecao.validaAte) return <>Sem prazo MANUAL</>
  return <Stack spacing={1}>
    <InstanteLocal isoUtc={selecao.validaAte} locale={locale} fusoHorario={fuso} rotulo="Validade exclusiva" />
    <Typography>{agora >= Date.parse(selecao.validaAte)
      ? 'Prazo encerrado segundo o relógio local. Sem retorno automático à PTAX.'
      : 'Limite exclusivo informado pelo servidor. Elegibilidade revalidada na operação.'}</Typography>
  </Stack>
}

export function PaginaCambio({ porta, usuarios, token, ativa, locale, aoExpirar, aoAtualizarOperacaoEmCurso, aoAtualizarOperacaoPendente }: Props) {
  const [conta, setConta] = useState<{ token: string; valor: MinhaConta } | null>(null)
  const [erroConta, setErroConta] = useState('')
  const [tentativaConta, setTentativaConta] = useState(0)
  const [fonte, setFonte] = useState<FonteCambial | ''>('')
  const [revisao, setRevisao] = useState(0)
  const [taxa, setTaxa] = useState('')
  const [motivoCadastro, setMotivoCadastro] = useState('')
  const [motivoSelecao, setMotivoSelecao] = useState('')
  const [manual, setManual] = useState<CotacaoCambial | null>(null)
  const [confirmacao, setConfirmacao] = useState<ComandoCambio | null>(null)
  const [ocupado, setOcupado] = useState(false)
  const [incerto, setIncerto] = useState(false)
  const [erro, setErro] = useState('')
  const [sucesso, setSucesso] = useState('')
  const trava = useRef(false)
  const tokenAtual = useRef(token)
  useLayoutEffect(() => { tokenAtual.current = token }, [token])
  const tema = useTheme()
  const telaPequena = useMediaQuery(tema.breakpoints.down('sm'))
  const perfil = conta?.token === token ? conta.valor.perfil : null
  const administrador = perfil !== null && podeAdministrarCambio(perfil)
  const bloqueado = ocupado || incerto || !token || !administrador || !ativa

  const tratarErro = useCallback((falha: unknown, tokenRequisicao?: string) => {
    if ((falha instanceof ErroCambio || falha instanceof ErroGestaoUsuarios) && falha.status === 401
      && (tokenRequisicao === undefined || tokenAtual.current === tokenRequisicao)) aoExpirar()
    return mensagemFalha(falha)
  }, [aoExpirar])

  useEffect(() => {
    if (!token || !ativa) return
    const controlador = new AbortController()
    let atual = true
    void usuarios.minhaConta(token, controlador.signal).then((valor) => {
      if (!atual) return
      setConta({ token, valor })
      setErroConta('')
    }).catch((falha: unknown) => {
      if (!atual) return
      setConta(null)
      setErroConta(tratarErro(falha))
    })
    return () => { atual = false; controlador.abort() }
  }, [ativa, tentativaConta, token, tratarErro, usuarios])

  const carregarCotacoes = useCallback((pagina: number, tamanho: number, signal: AbortSignal) =>
    porta.historico(token!, { pagina, tamanho, ...(fonte ? { fonte } : {}) }, signal), [porta, token, fonte])
  const carregarSelecoes = useCallback((pagina: number, tamanho: number, signal: AbortSignal) =>
    porta.selecoes(token!, { pagina, tamanho }, signal), [porta, token])

  function prepararCadastro(evento: FormEvent) {
    evento.preventDefault()
    if (bloqueado || trava.current) return
    const normalizada = normalizarTaxaCambial(taxa, locale)
    const erro = normalizada.valida ? erroJustificativa(motivoCadastro) : normalizada.erro
    setErro(erro)
    if (!normalizada.valida || erro) return
    setConfirmacao({ tipo: 'cadastro', entrada: { taxaBrlPorUsd: normalizada.valor, justificativa: motivoCadastro.trim() } })
  }

  function prepararSelecao(fonte: 'MANUAL' | 'BCB_PTAX') {
    if (bloqueado || trava.current) return
    const erro = erroJustificativa(motivoSelecao)
    setErro(erro)
    if (erro || (fonte === 'MANUAL' && !manual)) return
    setConfirmacao({ tipo: 'selecao', entrada: fonte === 'MANUAL'
      ? { fonte, cotacaoId: manual!.id, justificativa: motivoSelecao.trim() }
      : { fonte, justificativa: motivoSelecao.trim() } })
  }

  async function confirmar() {
    if (!confirmacao || bloqueado || trava.current || !token || !perfil) return
    trava.current = true
    setOcupado(true)
    aoAtualizarOperacaoEmCurso(true)
    setErro('')
    setSucesso('')
    const comando = confirmacao
    setConfirmacao(null)
    try {
      const resultado = await executarCambio(porta, token, perfil, comando)
      if ('taxaBrlPorUsd' in resultado) {
        setManual(resultado)
        setTaxa('')
        setMotivoCadastro('')
        setSucesso(`Cotação ${resultado.id} cadastrada, ainda não autorizada. Autorize em uma segunda ação independente.`)
      } else if ('versoesIncluidas' in resultado) {
        setSucesso(`Sincronização concluída: ${resultado.versoesIncluidas} versão(ões) incluída(s). A fonte selecionada não foi alterada.`)
      } else {
        setManual(null)
        setMotivoSelecao('')
        setSucesso(`Seleção ${resultado.id} registrada: ${resultado.fonte}. Solicite nova prévia e reconfirme as operações financeiras. Consulte autorização e validade no histórico.`)
      }
      setRevisao((valor) => valor + 1)
    } catch (falha) {
      if (!(falha instanceof ErroCambio) || falha.resultadoIncerto) {
        setIncerto(true)
        aoAtualizarOperacaoPendente(true)
      }
      setErro(tratarErro(falha, token))
    } finally {
      trava.current = false
      setOcupado(false)
      aoAtualizarOperacaoEmCurso(false)
    }
  }

  const instante = (isoUtc: string, rotulo: string) => <InstanteLocal isoUtc={isoUtc} locale={locale} fusoHorario={fuso} rotulo={rotulo} />
  const tituloConfirmacao = confirmacao?.tipo === 'cadastro' ? 'Confirmar cadastro MANUAL'
    : confirmacao?.tipo === 'sincronizacao' ? 'Confirmar sincronização PTAX'
      : confirmacao?.entrada.fonte === 'MANUAL' ? 'Confirmar autorização MANUAL' : 'Confirmar retorno à PTAX'

  return <Stack spacing={3}>
    <Mensagem severity="info">
      PTAX Fechamento VENDA: referência de mercado, até 96 horas corridas inclusive, sem extensão por feriados.
      MANUAL: cadastro não autoriza uso; autorização até a próxima meia-noite exclusiva em America/Sao_Paulo.
      Sem fallback automático entre PTAX, MANUAL e DEMO. O servidor determina a fonte e a elegibilidade na prévia e na liquidação.
    </Mensagem>
    {!perfil && !erroConta && <Carregando rotulo="Verificando perfil para câmbio…" />}
    {erroConta && <Mensagem severity="error" action={<Button onClick={() => {
      setErroConta(''); setTentativaConta((valor) => valor + 1)
    }}>Verificar perfil novamente</Button>}>{erroConta}</Mensagem>}
    {sucesso && <Mensagem severity="success">{sucesso}</Mensagem>}
    {erro && <Mensagem severity="error">{erro}</Mensagem>}
    {ocupado && <Carregando rotulo="Registrando operação cambial…" />}
    {incerto && <Mensagem severity="warning">
      Resultado incerto: a operação pode ter sido registrada. Não reenvie. Escritas bloqueadas nesta sessão;
      atualize e confira os históricos com o responsável antes de encerrar a sessão ou iniciar outra intenção. Não há retentativa automática.
    </Mensagem>}
    {administrador && <>
      <Painel id="nova-cotacao-manual" titulo="Nova cotação manual" descricao="BRL por 1 USD. O servidor registra autoria e horários. Cadastro sem ativação.">
        <Box component="form" noValidate onSubmit={prepararCadastro}>
          <Box sx={grade}>
            <CampoFormulario label="Taxa BRL por USD" required value={taxa} disabled={bloqueado}
              helperText={`Até 10 dígitos inteiros e 10 decimais. Exemplo: ${locale.startsWith('pt') ? '5,4321' : '5.4321'}.`}
              slotProps={{ htmlInput: { inputMode: 'decimal', maxLength: 40 } }}
              onChange={(evento) => setTaxa(evento.target.value)}
              onBlur={() => {
                const normalizada = normalizarTaxaCambial(taxa, locale)
                if (normalizada.valida) setTaxa(formatarEntradaTaxa(normalizada.valor, locale))
              }} />
            <CampoFormulario label="Justificativa do cadastro" required value={motivoCadastro} disabled={bloqueado}
              slotProps={{ htmlInput: { maxLength: 1000 } }} onChange={(evento) => setMotivoCadastro(evento.target.value)} />
          </Box>
          <Button type="submit" variant="contained" disabled={bloqueado} sx={{ mt: 2 }}>Cadastrar MANUAL</Button>
        </Box>
      </Painel>
      <Painel id="selecao-cambial" titulo="Autorização e fonte cambial" descricao="Escolha uma MANUAL no histórico ou após cadastrá-la. Somente cotações do mesmo dia e nunca autorizadas são aceitas pelo servidor.">
        <Stack spacing={2}>
          <Typography sx={{ overflowWrap: 'anywhere' }}>{manual
            ? `Cotação escolhida: ${manual.id} — ${formatarCotacao(manual.taxaBrlPorUsd, locale)} BRL/USD. Ainda requer autorização.`
            : 'Nenhuma cotação escolhida para autorização.'}</Typography>
          <CampoFormulario label="Justificativa da seleção" required value={motivoSelecao} disabled={bloqueado}
            slotProps={{ htmlInput: { maxLength: 1000 } }} onChange={(evento) => setMotivoSelecao(evento.target.value)} />
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <Button disabled={bloqueado || !manual} variant="contained" onClick={() => prepararSelecao('MANUAL')}>Autorizar uso da MANUAL</Button>
            <Button disabled={bloqueado} variant="outlined" onClick={() => prepararSelecao('BCB_PTAX')}>Retornar à PTAX</Button>
            <Button disabled={bloqueado} onClick={() => setConfirmacao({ tipo: 'sincronizacao' })}>Sincronizar PTAX</Button>
          </Stack>
        </Stack>
      </Painel>
    </>}
    <Painel id="historico-cotacoes" titulo="Histórico de cotações" descricao="Somente leitura. Corrija por novo cadastro e autorização. O histórico não confirma elegibilidade ou a fonte efetiva da operação.">
      <Stack spacing={2}>
        <CampoFormulario label="Filtrar por fonte" value={fonte} opcoes={fontes} onChange={(evento) => {
          const valor = fontes.find((item) => item.valor === evento.target.value)?.valor
          if (valor !== undefined) setFonte(valor as FonteCambial | '')
        }} />
        <Button disabled={ocupado} onClick={() => setRevisao((valor) => valor + 1)}>Atualizar históricos</Button>
        {token && ativa && perfil && <HistoricoCambio<CotacaoCambial> key={`cotacoes-${fonte}-${revisao}-${token}`}
          titulo="Cotações cambiais" carregar={carregarCotacoes} tratarErro={tratarErro} colunas={[
            { id: 'taxa', rotulo: 'Taxa BRL/USD', cabecalhoLinha: true, renderizar: (item) => formatarCotacao(item.taxaBrlPorUsd, locale) },
            { id: 'fonte', rotulo: 'Fonte / lado / versão', renderizar: (item) => `${item.fonte} / ${item.lado} / ${item.versao}` },
            { id: 'referencia', rotulo: 'Referência e boletim', renderizar: (item) => <>{instante(item.referencia, 'Referência')}<Typography>{item.dataBoletim ?? 'Sem data de boletim'} / {item.boletim ?? 'Sem boletim'}</Typography>{item.horarioOriginal && <Typography>{item.horarioOriginal} ({item.fuso})</Typography>}</> },
            { id: 'observacao', rotulo: 'Observada em', renderizar: (item) => instante(item.observadaEm, 'Observada em') },
            { id: 'id', rotulo: 'Código', renderizar: (item) => item.id },
            ...(administrador ? [
              { id: 'auditoria', rotulo: 'Autor / justificativa', renderizar: (item: CotacaoCambial) => `${item.autorId ?? 'Não disponível'} / ${item.justificativa ?? 'Não disponível'}` },
              { id: 'autorizar', rotulo: 'Escolher', renderizar: (item: CotacaoCambial) => item.fonte === 'MANUAL'
                ? <Button disabled={bloqueado} aria-label={`Escolher MANUAL ${item.id}`} onClick={() => {
                  setManual(item)
                  document.getElementById('selecao-cambial')?.scrollIntoView?.({ block: 'center' })
                }}>Escolher para autorização</Button> : '—' },
            ] : []),
          ]} />}
      </Stack>
    </Painel>
    <Painel id="historico-selecoes" titulo="Histórico de seleções e autorizações" descricao="Ordem fornecida pelo servidor, mais recentes primeiro. Registros históricos não significam seleção vigente. Antes da primeira seleção, PTAX é o padrão, exceto DEMO configurado explicitamente.">
      {token && ativa && perfil && <HistoricoCambio<SelecaoCambial> key={`selecoes-${revisao}-${token}`}
        titulo="Seleções e autorizações cambiais" carregar={carregarSelecoes} tratarErro={tratarErro} colunas={[
          { id: 'fonte', rotulo: 'Fonte', cabecalhoLinha: true, renderizar: (item) => item.fonte },
          { id: 'cotacao', rotulo: 'Cotação / seleção', renderizar: (item) => `${item.cotacaoId ?? 'Seleção PTAX'} / ${item.id}` },
          { id: 'autorizacao', rotulo: 'Autorizada em', renderizar: (item) => instante(item.autorizadoEm, 'Autorizada em') },
          { id: 'validade', rotulo: 'Validade exclusiva', renderizar: (item) => <ValidadeManual selecao={item} locale={locale} /> },
          { id: 'politica', rotulo: 'Política', renderizar: (item) => item.politicaId },
          ...(administrador ? [{ id: 'auditoria', rotulo: 'Autor / justificativa', renderizar: (item: SelecaoCambial) => `${item.autorId ?? 'Não disponível'} / ${item.justificativa ?? 'Não disponível'}` }] : []),
        ]} />}
    </Painel>
    <Dialog open={ativa && Boolean(token) && administrador && confirmacao !== null} onClose={() => setConfirmacao(null)} fullScreen={telaPequena}
      fullWidth maxWidth="sm" aria-labelledby="confirmacao-cambio-titulo">
      <DialogTitle id="confirmacao-cambio-titulo">{tituloConfirmacao}</DialogTitle>
      <DialogContent>
        <DialogContentText>
          {confirmacao?.tipo === 'cadastro' ? `Cadastrar ${formatarCotacao(confirmacao.entrada.taxaBrlPorUsd, locale)} BRL por USD? Cadastro não ativa a cotação. Será necessária uma segunda confirmação para autorizar o uso.`
            : confirmacao?.tipo === 'sincronizacao' ? 'Sincronizar os sete dias anteriores? Esta ação não altera a fonte selecionada nem renova a idade de boletins antigos.'
              : confirmacao?.entrada.fonte === 'MANUAL' ? `Autorizar explicitamente a cotação ${confirmacao.entrada.cotacaoId}? Será válida desde a autorização até a próxima meia-noite exclusiva de America/Sao_Paulo. Nova prévia e reconfirmação financeira serão necessárias.`
                : 'Retornar explicitamente à fonte BCB_PTAX? O retorno não garante disponibilidade de fechamento VENDA elegível: o servidor verificará isso na operação financeira. Não há fallback. Solicite nova prévia e reconfirme operações financeiras.'}
        </DialogContentText>
        {confirmacao && confirmacao.tipo !== 'sincronizacao' && <Typography sx={{ mt: 2, overflowWrap: 'anywhere' }}>Justificativa: {confirmacao.entrada.justificativa}</Typography>}
      </DialogContent>
      <DialogActions>
        <Button autoFocus onClick={() => setConfirmacao(null)}>Cancelar</Button>
        <Button variant="contained" disabled={bloqueado} onClick={() => void confirmar()}>{tituloConfirmacao}</Button>
      </DialogActions>
    </Dialog>
  </Stack>
}
