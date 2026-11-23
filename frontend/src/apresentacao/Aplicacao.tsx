import { useCallback, useEffect, useLayoutEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { Box, Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, Link, Stack, Typography } from '@mui/material'
import type { PortaGestaoUsuarios } from '../aplicacao/gestaoUsuarios'
import type { PortaExtrato } from '../aplicacao/extrato'
import { cadastrarParaLiquidacao, confirmarLiquidacao, ErroOperacao, mensagemErro, type PortaOperacoes } from '../aplicacao/operacoes'
import type { Cedente, IntencaoLiquidacao, Liquidacao, Modalidade, Moeda, Sessao, Simulacao, TipoDocumento } from '../dominio/operacoes'
import { PaginaAdministracaoUsuarios } from './PaginaAdministracaoUsuarios'
import { PaginaExtrato } from './PaginaExtrato'
import { LayoutOperacional } from './LayoutOperacional'
import { hashDaPagina, paginaDaHash, type PaginaOperacional } from './navegacaoOperacional'
import { CampoFormulario } from '../tema/componentes/CampoFormulario'
import { Carregando, EstadoVazio, Mensagem } from '../tema/componentes/Feedback'
import { Painel } from '../tema/componentes/Painel'
import { InstanteLocal } from '../tema/componentes/InstanteLocal'
import { formatarEntradaMonetaria, formatarMonetario, formatarPercentual, formatarCotacao, normalizarEntradaMonetaria, separadorDecimalMonetario } from '../dominio/formatacaoMonetaria'
import { caixaAscii, editarCnpj, erroDocumento, erroTipoDocumento } from '../dominio/documentoCedente'
import type { PortaLotes } from '../aplicacao/lotes'
import { PaginaImportacaoLote } from './PaginaImportacaoLote'
import type { PortaCambio } from '../aplicacao/cambio'
import { PaginaCambio } from './PaginaCambio'

const grade = { display: 'grid', gap: 2, gridTemplateColumns: { xs: 'minmax(0, 1fr)', sm: 'repeat(2, minmax(0, 1fr))' } }
const modalidades = [
  { valor: 'DUPLICATA_MERCANTIL', rotulo: 'Duplicata mercantil' },
  { valor: 'CHEQUE_PRE_DATADO', rotulo: 'Cheque pré-datado' },
]
const moedas = [{ valor: 'BRL', rotulo: 'BRL' }, { valor: 'USD', rotulo: 'USD' }]

type Props = { operacoes: PortaOperacoes; gestaoUsuarios: PortaGestaoUsuarios; extrato: PortaExtrato; lotes: PortaLotes; cambio: PortaCambio; locale: string; fusoHorario?: string }

export function Aplicacao({ operacoes, gestaoUsuarios, extrato, lotes, cambio, locale, fusoHorario }: Props) {
  const [sessao, setSessao] = useState<Sessao | null>(null)
  const [email, setEmail] = useState('')
  const [identidade, setIdentidade] = useState('')
  const [senha, setSenha] = useState('')
  const [erro, setErro] = useState('')
  const [entrando, setEntrando] = useState(false)
  const [expirada, setExpirada] = useState(false)
  const [pagina, setPagina] = useState<PaginaOperacional>(() => paginaDaHash(window.location.hash))
  const [usuariosVisiveis, setUsuariosVisiveis] = useState<boolean | null>(null)
  const [operacaoEmCurso, setOperacaoEmCurso] = useState(false)
  const [operacaoPendente, setOperacaoPendente] = useState(false)
  const [loteEmCurso, setLoteEmCurso] = useState(false)
  const [lotePendente, setLotePendente] = useState(false)
  const [cambioEmCurso, setCambioEmCurso] = useState(false)
  const [cambioPendente, setCambioPendente] = useState(false)
  const [confirmarSaida, setConfirmarSaida] = useState(false)

  useEffect(() => {
    function atualizarPagina() {
      setPagina(paginaDaHash(window.location.hash))
    }
    window.addEventListener('hashchange', atualizarPagina)
    return () => window.removeEventListener('hashchange', atualizarPagina)
  }, [])

  useEffect(() => {
    if (!sessao) return
    const timer = window.setTimeout(() => { setSessao(null); setExpirada(true) }, Math.max(0, sessao.expiraEm - Date.now()))
    return () => window.clearTimeout(timer)
  }, [sessao])

  useEffect(() => {
    if (!operacaoEmCurso && !operacaoPendente && !loteEmCurso && !lotePendente && !cambioEmCurso && !cambioPendente) return
    function prevenirDescarte(evento: BeforeUnloadEvent) {
      evento.preventDefault()
      evento.returnValue = ''
    }
    window.addEventListener('beforeunload', prevenirDescarte)
    return () => window.removeEventListener('beforeunload', prevenirDescarte)
  }, [operacaoEmCurso, operacaoPendente, loteEmCurso, lotePendente, cambioEmCurso, cambioPendente])

  const navegar = useCallback((destino: PaginaOperacional) => {
    const paginaPermitida = destino === 'usuarios' && usuariosVisiveis === false ? 'cedentes' : destino
    const hash = hashDaPagina(paginaPermitida)
    if (window.location.hash === hash) setPagina(paginaPermitida)
    else window.location.hash = hash
  }, [usuariosVisiveis])

  const verificarAcessoUsuarios = useCallback((permitido: boolean) => {
    setUsuariosVisiveis(permitido)
  }, [])

  useEffect(() => {
    if (sessao && pagina === 'usuarios' && usuariosVisiveis === false) {
      window.location.hash = hashDaPagina('cedentes')
    }
  }, [pagina, sessao, usuariosVisiveis])

  async function entrar(evento: FormEvent) {
    evento.preventDefault()
    if (entrando) return
    setEntrando(true)
    setErro('')
    try {
      const novaSessao = await operacoes.autenticar(email, senha)
      setIdentidade(email.trim())
      setUsuariosVisiveis(null)
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

  const sair = useCallback(() => {
    setSessao(null)
    setExpirada(false)
    setIdentidade('')
    setEmail('')
    setSenha('')
    setErro('')
    setUsuariosVisiveis(null)
    setOperacaoEmCurso(false)
    setOperacaoPendente(false)
    setLoteEmCurso(false)
    setLotePendente(false)
    setCambioEmCurso(false)
    setCambioPendente(false)
    setConfirmarSaida(false)
    navegar('cedentes')
  }, [navegar])

  const solicitarSaida = useCallback(() => {
    if (operacaoEmCurso || loteEmCurso || cambioEmCurso) return
    if (operacaoPendente || lotePendente || cambioPendente) {
      setConfirmarSaida(true)
      return
    }
    sair()
  }, [operacaoEmCurso, operacaoPendente, loteEmCurso, lotePendente, cambioEmCurso, cambioPendente, sair])

  const tratarErro = useCallback((falha: unknown) => {
    if (falha instanceof ErroOperacao && falha.status === 401) expirarSessao()
    return mensagemErro(falha)
  }, [expirarSessao])

  return (
    <Box>
      {!sessao && (
        <Box component="main" sx={{ maxWidth: 1120, mx: 'auto', p: { xs: 2, md: 4 } }}>
          <Typography component="h1" variant="h1" gutterBottom>SRM Credit Engine</Typography>
          <Painel id="acesso" titulo="Acesso">
            {expirada && <Mensagem severity="warning">Sessão expirada. Entre novamente com a mesma conta para continuar a operação em andamento.</Mensagem>}
            <Stack component="form" onSubmit={entrar} spacing={2}>
              <CampoFormulario label="E-mail" type="email" required autoComplete="username" value={email}
                disabled={Boolean(identidade)} onChange={(e) => setEmail(e.target.value)} />
              <CampoFormulario label="Senha" type="password" required autoComplete="current-password" value={senha}
                onChange={(e) => setSenha(e.target.value)} />
              {erro && <Mensagem severity="error">{erro}</Mensagem>}
              <Box><Button type="submit" variant="contained" disabled={entrando}>{entrando ? 'Entrando…' : 'Entrar'}</Button></Box>
              <Link href="/catalogo.html">Catálogo visual (demonstração)</Link>
            </Stack>
          </Painel>
        </Box>
      )}
      {(sessao || expirada) && <Box hidden={!sessao}>
        <LayoutOperacional
          pagina={pagina}
          usuariosVisiveis={usuariosVisiveis === true}
          sairDesabilitado={operacaoEmCurso || loteEmCurso || cambioEmCurso}
          aoNavegar={navegar}
          aoSair={solicitarSaida}
        >
          {(operacaoPendente || lotePendente || cambioPendente) && (
            <Dialog open={confirmarSaida} onClose={() => setConfirmarSaida(false)} aria-labelledby="confirmar-saida-titulo">
              <DialogTitle id="confirmar-saida-titulo">Sair com operação pendente?</DialogTitle>
              <DialogContent>
                <DialogContentText>
                  Sair encerra a sessão e descarta a intenção ou o bloqueio mantido apenas em memória. Antes de iniciar outro cadastro, consulte os recebíveis no servidor e confirme o resultado da operação. Para câmbio, confira também os históricos de cotações e autorizações.
                </DialogContentText>
              </DialogContent>
              <DialogActions>
                <Button onClick={() => setConfirmarSaida(false)}>Continuar operação</Button>
                <Button color="error" onClick={sair}>Sair mesmo assim</Button>
              </DialogActions>
            </Dialog>
          )}
          <PainelOperacoes
            operacoes={operacoes}
            token={sessao?.token ?? null}
            tratarErro={tratarErro}
            locale={locale}
            fusoHorario={fusoHorario}
            pagina={pagina}
            navegar={navegar}
            aoAtualizarOperacaoEmCurso={setOperacaoEmCurso}
            aoAtualizarOperacaoPendente={setOperacaoPendente}
            bloqueadoPorLote={loteEmCurso || lotePendente}
          />
          <Box hidden={pagina !== 'importacao'}>
            <PaginaImportacaoLote
              porta={lotes}
              token={sessao?.token ?? null}
              tratarErro={tratarErro}
              locale={locale}
              fusoHorario={fusoHorario}
              aoAtualizarOperacaoEmCurso={setLoteEmCurso}
              aoAtualizarOperacaoPendente={setLotePendente}
              bloqueadoExternamente={operacaoEmCurso || operacaoPendente}
            />
          </Box>
          <Box hidden={pagina !== 'cambio'}>
            <PaginaCambio porta={cambio} usuarios={gestaoUsuarios} token={sessao?.token ?? null}
              ativa={pagina === 'cambio'} locale={locale} aoExpirar={expirarSessao}
              aoAtualizarOperacaoEmCurso={setCambioEmCurso} aoAtualizarOperacaoPendente={setCambioPendente} />
          </Box>
          {sessao && <Box hidden={pagina !== 'extrato'}>
            <PaginaExtrato
              porta={extrato}
              token={sessao.token}
              aoExpirar={expirarSessao}
              locale={locale}
              fusoHorario={fusoHorario}
              ativa={pagina === 'extrato'}
              mostrarTitulo={false}
            />
          </Box>}
          {sessao && <Box hidden={pagina !== 'usuarios'}>
            <PaginaAdministracaoUsuarios
              porta={gestaoUsuarios}
              token={sessao.token}
              aoExpirar={expirarSessao}
              aoVerificarCapacidade={verificarAcessoUsuarios}
            />
          </Box>}
        </LayoutOperacional>
      </Box>}
    </Box>
  )
}

type PainelProps = Pick<Props, 'operacoes' | 'locale' | 'fusoHorario'> & {
  token: string | null
  tratarErro: (erro: unknown) => string
  pagina: PaginaOperacional
  navegar: (pagina: PaginaOperacional) => void
  aoAtualizarOperacaoEmCurso: (emCurso: boolean) => void
  aoAtualizarOperacaoPendente: (pendente: boolean) => void
  bloqueadoPorLote: boolean
}
type Previa = { entrada: string; dados: Simulacao }

function PainelOperacoes({
  operacoes, token, tratarErro, locale, fusoHorario, pagina, navegar,
  aoAtualizarOperacaoEmCurso, aoAtualizarOperacaoPendente, bloqueadoPorLote,
}: PainelProps) {
  const [cedentes, setCedentes] = useState<Cedente[] | null>(null)
  const [erroCedentes, setErroCedentes] = useState('')
  const [atualizacao, setAtualizacao] = useState(0)
  const [nome, setNome] = useState('')
  const [documento, setDocumento] = useState('')
  const [pais, setPais] = useState('BR')
  const [tipoDocumento, setTipoDocumento] = useState<TipoDocumento>('CNPJ')
  const colandoDocumento = useRef(false)
  const selecaoDocumento = useRef(false)
  const cursorDocumento = useRef<{ campo: HTMLInputElement; posicao: number } | null>(null)
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
  const [previaObsoleta, setPreviaObsoleta] = useState(false)
  const [liquidacao, setLiquidacao] = useState<Liquidacao | null>(null)
  const trava = useRef(false)
  const campoValor = useRef<HTMLInputElement>(null)
  const cursorValor = useRef<{ inicio: number; fim: number; formatado: string } | null>(null)
  const [valorEmFoco, setValorEmFoco] = useState(false)

  function atualizarOcupado(emCurso: boolean) {
    setOcupado(emCurso)
    aoAtualizarOperacaoEmCurso(emCurso)
  }

  const erroIdentificacao = erroDocumento(pais, tipoDocumento, documento)
  const erroTipo = erroTipoDocumento(pais, tipoDocumento)
  useLayoutEffect(() => {
    const cursor = cursorDocumento.current
    if (cursor && document.activeElement === cursor.campo) cursor.campo.setSelectionRange(cursor.posicao, cursor.posicao)
    cursorDocumento.current = null
  }, [documento])
  useLayoutEffect(() => {
    const cursor = cursorValor.current
    const campo = campoValor.current
    if (!cursor || !campo || document.activeElement !== campo) return
    const editavel = valor
    campo.setSelectionRange(
      mapearCursorMonetario(cursor.formatado, editavel, cursor.inicio, locale),
      mapearCursorMonetario(cursor.formatado, editavel, cursor.fim, locale),
    )
    cursorValor.current = null
  }, [locale, valor, valorEmFoco])
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
    if (!token || trava.current || erroIdentificacao || bloqueadoPorLote) return
    trava.current = true
    atualizarOcupado(true)
    setErro('')
    setSucesso('')
    try {
      const novo = await operacoes.cadastrarCedente(token, { nome: nome.trim(), documento, pais, tipoDocumento })
      setCedenteId(novo.id)
      setNome('')
      setDocumento('')
      setSucesso('Cedente cadastrado.')
      setAtualizacao((n) => n + 1)
    } catch (falha) {
      if (falha instanceof ErroOperacao && falha.status === 400) {
        setErro('Dados rejeitados pelo servidor. Confira nome, país, tipo e documento, incluindo os dígitos do CNPJ.')
      } else if (falha instanceof ErroOperacao && falha.status === 409) {
        setErro('Conflito no cadastro. Pode haver documento duplicado ou outro conflito; confira os dados e a lista de cedentes.')
      } else {
        setErro(`${tratarErro(falha)} Confira a lista de cedentes antes de tentar cadastrar novamente.`)
      }
      setAtualizacao((n) => n + 1)
    } finally {
      trava.current = false
      atualizarOcupado(false)
    }
  }

  async function cadastrarRecebivel() {
    if (!token || !cedenteId || !previa || previa.entrada !== chaveEntrada || trava.current || bloqueadoPorLote) return
    trava.current = true
    atualizarOcupado(true)
    setErro('')
    setSucesso('')
    try {
      const chave = crypto.randomUUID()
      const intencao = await cadastrarParaLiquidacao(operacoes, token, {
        cedenteId, modalidade, valorFaceBrl: valorCanonico, prazoMeses: Number(prazo), vencimento: vencimento || null,
      }, moeda, chave, previa.dados.revisaoCambial ?? undefined)
      setPendente(intencao)
      aoAtualizarOperacaoPendente(true)
      setSucesso('Recebível cadastrado. Confirme a liquidação para prosseguir.')
      navegar('liquidacao')
    } catch (falha) {
      const conclusivo = falha instanceof ErroOperacao && [400, 401, 403, 404, 409].includes(falha.status ?? 0)
      if (!conclusivo) {
        setIncerto(true)
        aoAtualizarOperacaoPendente(true)
      }
      setErro(`${tratarErro(falha)}${conclusivo ? '' : ' O resultado do cadastro é incerto. Não repita o cadastro antes de verificar os recebíveis no servidor.'}`)
    } finally {
      trava.current = false
      atualizarOcupado(false)
    }
  }

  async function renovarPrevia() {
    if (!token || !pendente || !previaObsoleta || trava.current || bloqueadoPorLote) return
    trava.current = true
    atualizarOcupado(true)
    try {
      const dados = await operacoes.simular(token, {
        modalidade, valorFaceBrl: valorCanonico, prazoMeses: Number(prazo), moeda: pendente.moeda,
      }, new AbortController().signal)
      setPrevia({ entrada: chaveEntrada, dados })
      setPendente({ ...pendente, chave: crypto.randomUUID(), revisaoCambial: dados.revisaoCambial ?? undefined })
      setPreviaObsoleta(false)
      setErro('')
      setSucesso('Prévia atualizada. Confira os valores e confirme novamente a liquidação do mesmo recebível.')
    } catch (falha) {
      setErro(tratarErro(falha))
    } finally {
      trava.current = false
      atualizarOcupado(false)
    }
  }

  async function liquidar() {
    if (!token || !pendente || previaObsoleta || trava.current || bloqueadoPorLote) return
    trava.current = true
    atualizarOcupado(true)
    setErro('')
    setSucesso('')
    setConfirmando(false)
    try {
      const resultado = await confirmarLiquidacao(operacoes, token, pendente)
      setLiquidacao(resultado)
      aoAtualizarOperacaoPendente(false)
      setSucesso('Liquidação confirmada.')
    } catch (falha) {
      const conclusivo = falha instanceof ErroOperacao && [400, 401, 403, 404, 409].includes(falha.status ?? 0)
      if (falha instanceof ErroOperacao && falha.status === 409) setPreviaObsoleta(true)
      const mensagem = tratarErro(falha)
      setErro(falha instanceof ErroOperacao && falha.status === 409
        ? `${mensagem} Atualize a prévia e confirme novamente o mesmo recebível, sem recadastrá-lo.`
        : conclusivo
        ? `${mensagem} A mesma chave será usada ao tentar novamente.`
        : `${mensagem} O resultado da liquidação é incerto; uma nova tentativa usará a mesma chave de idempotência.`)
    } finally {
      trava.current = false
      atualizarOcupado(false)
    }
  }

  const bloqueado = ocupado || Boolean(pendente) || incerto || Boolean(liquidacao)
  const previaAtual = previa?.entrada === chaveEntrada ? previa.dados : null
  const cedenteSelecionado = cedentes?.find((cedente) => cedente.id === cedenteId)
  const operacaoAtiva = pagina === 'cedentes' || pagina === 'simulacao' || pagina === 'liquidacao'
  return (
    <Stack spacing={3} sx={{ mt: 3 }}>
      {operacaoAtiva && sucesso && <Mensagem severity="success">{sucesso}</Mensagem>}
      {operacaoAtiva && erro && <Mensagem severity="error">{erro}</Mensagem>}
      {operacaoAtiva && ocupado && <Carregando rotulo="Processando operação…" />}
      {operacaoAtiva && bloqueadoPorLote && <Mensagem severity="warning">Novas escritas bloqueadas: resolva a confirmação do lote em Importação em lote antes de iniciar outra operação. Consultas e navegação continuam disponíveis.</Mensagem>}
      <Box hidden={pagina !== 'cedentes'}>
        <Typography color="text.secondary" sx={{ mb: 2 }}>Cadastre cedentes e selecione a parte vinculada ao recebível.</Typography>
        <Painel id="cedentes" titulo="Cedentes">
        <Stack component="form" onSubmit={cadastrarCedente} spacing={2}>
          <Box sx={grade}>
            <CampoFormulario label="Nome do cedente" required value={nome} disabled={bloqueado}
              slotProps={{ htmlInput: { maxLength: 160 } }} onChange={(e) => { setNome(e.target.value); setErro('') }} />
            <CampoFormulario label="País do documento (ISO alpha-2)" required value={pais} disabled={bloqueado}
              helperText="Duas letras, por exemplo BR ou US. O servidor valida o país."
              erro={pais && !/^[A-Z]{2}(?![\s\S])/.test(pais) ? 'Informe duas letras ASCII.' : undefined}
              onChange={(e) => {
                const novoPais = caixaAscii(e.target.value)
                setPais(novoPais)
                if (/^[A-Z]{2}(?![\s\S])/.test(novoPais)) setTipoDocumento(novoPais === 'BR' ? 'CNPJ' : 'OUTRO')
                setErro('')
              }} />
            <CampoFormulario label="Tipo de documento" value={tipoDocumento} disabled={bloqueado}
              opcoes={[{ valor: 'CNPJ', rotulo: 'CNPJ' }, { valor: 'OUTRO', rotulo: 'Outro — identificador não verificado' }]}
              erro={erroTipo || undefined}
              helperText="BR exige CNPJ; países estrangeiros exigem Outro."
              onChange={(e) => { setTipoDocumento(e.target.value as TipoDocumento); setErro('') }} />
            <CampoFormulario label="Documento do cedente" required value={documento} disabled={bloqueado}
              helperText={tipoDocumento === 'CNPJ'
                ? 'CNPJ numérico ou alfanumérico; o servidor valida os dígitos verificadores.'
                : 'Identificador não verificado: 1 a 32 letras ASCII ou números. Não é validação fiscal.'}
              erro={documento && !erroTipo && erroIdentificacao ? erroIdentificacao : undefined}
              slotProps={{ htmlInput: {
                onPaste: () => { colandoDocumento.current = true },
                onKeyDown: (e: KeyboardEvent<HTMLInputElement>) => {
                  const campo = e.currentTarget
                  selecaoDocumento.current = campo.selectionStart !== campo.selectionEnd
                },
              } }}
              onChange={(e) => {
                const campo = e.target as HTMLInputElement
                const texto = campo.value
                const posicao = campo.selectionStart ?? texto.length
                const colagem = colandoDocumento.current
                colandoDocumento.current = false
                const eventoInput = e.nativeEvent as InputEvent
                const editado = pais === 'BR' && tipoDocumento === 'CNPJ' && !eventoInput.isComposing
                  ? editarCnpj(documento, texto, posicao, eventoInput.inputType ?? '', colagem, selecaoDocumento.current, eventoInput.data)
                  : { valor: caixaAscii(texto), cursor: posicao }
                selecaoDocumento.current = false
                const { valor: novo, cursor } = editado
                if (novo !== texto) {
                  if (novo === documento) campo.setSelectionRange(cursor, cursor)
                  else cursorDocumento.current = { campo, posicao: cursor }
                }
                setDocumento(novo)
                setErro('')
              }} />
          </Box>
          {tipoDocumento === 'OUTRO' && <Typography variant="body2">Identificador não verificado. Não é validação fiscal.</Typography>}
          <Box><Button type="submit" variant="outlined" disabled={bloqueado || bloqueadoPorLote || Boolean(erroIdentificacao)}>Cadastrar cedente</Button></Box>
        </Stack>
        {erroCedentes && <Mensagem severity="error">{erroCedentes} <Button onClick={() => setAtualizacao((n) => n + 1)}>Tentar novamente</Button></Mensagem>}
        {!cedentes && !erroCedentes && <Carregando rotulo="Carregando cedentes…" />}
        {cedentes && !cedentes.some((c) => c.ativo) && <EstadoVazio titulo="Nenhum cedente ativo" descricao="Cadastre um cedente para continuar." />}
        {cedentes?.some((c) => c.ativo) && <CampoFormulario label="Cedente" value={cedenteId} disabled={bloqueado}
          opcoes={cedentes.filter((c) => c.ativo).map((c) => ({ valor: c.id, rotulo: `${c.nome} — ${c.documento}` }))}
          onChange={(e) => setCedenteId(e.target.value)} />}
        {cedenteSelecionado && <Typography variant="body2">
          País: {cedenteSelecionado.pais ?? 'desconhecido'}; tipo: {cedenteSelecionado.tipoDocumento ?? 'desconhecido'}; status: {
            cedenteSelecionado.statusDocumento === 'VALIDADO_DV' ? 'dígitos verificadores validados'
              : cedenteSelecionado.statusDocumento === 'NAO_VERIFICADO' ? 'não verificado' : 'desconhecido'
          }.
        </Typography>}
        </Painel>
      </Box>
      <Box hidden={pagina !== 'simulacao'}>
        <Typography color="text.secondary" sx={{ mb: 2 }}>Simule antes de cadastrar e liquidar um recebível.</Typography>
        <Painel id="simulacao" titulo="Simulação pré-cadastro">
        <Mensagem severity="warning">Simulação indicativa, sem garantia de taxas ou câmbio até a liquidação. Nenhum recebível é criado nesta etapa.</Mensagem>
        <Box sx={grade}>
          <CampoFormulario label="Modalidade" value={modalidade} opcoes={modalidades} disabled={bloqueado}
            onChange={(e) => { setModalidade(e.target.value as Modalidade); alterou() }} />
          <CampoFormulario label="Valor de face em BRL"
            value={valorEmFoco ? valor : entradaMonetaria.valida ? formatarMonetario(entradaMonetaria.valorCanonico, 'BRL', locale) : valor}
            required disabled={bloqueado}
            type="text" inputMode="decimal"
            inputRef={campoValor}
            helperText={valor && !entradaMonetaria.valida
              ? entradaMonetaria.erro
              : valorEmFoco
                ? (locale.toLowerCase().startsWith('en') ? 'Edit the decimal value; currency formatting is applied when you leave the field.' : 'Edite o valor decimal; a formatação monetária é aplicada ao sair do campo.')
                : locale.toLowerCase().startsWith('en') ? 'Example: 1,234.56.' : 'Exemplo: 1.234,56.'}
            erro={valor && !entradaMonetaria.valida ? entradaMonetaria.erro : undefined}
            onFocus={(evento) => {
              const campo = evento.target
              const inicio = campo.selectionStart ?? campo.value.length
              const fim = campo.selectionEnd ?? campo.value.length
              cursorValor.current = { inicio, fim, formatado: campo.value }
              const editavel = entradaMonetaria.valida
                ? formatarEntradaMonetaria(entradaMonetaria.valorCanonico, locale)
                : valor
              if (editavel !== valor) setValor(editavel)
              setValorEmFoco(true)
            }}
            onBlur={() => setValorEmFoco(false)}
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
        {previaAtual && !pendente && <Valores dados={previaAtual} locale={locale} fusoHorario={fusoHorario} />}
        <Box><Button variant="contained" disabled={bloqueado || bloqueadoPorLote || !cedenteId || !previaAtual || estadoPrevia !== 'pronto'}
          onClick={cadastrarRecebivel}>Cadastrar recebível</Button></Box>
        </Painel>
      </Box>
      <Box hidden={pagina !== 'liquidacao'}>
        {pendente && !liquidacao && <Painel id="liquidacao" titulo="Liquidação">
          <Typography>Recebível: {pendente.id}. Alterações financeiras exigem nova prévia e confirmação.</Typography>
          {previaAtual && <Valores dados={previaAtual} locale={locale} fusoHorario={fusoHorario} />}
          {previaObsoleta && <Button disabled={ocupado || bloqueadoPorLote} onClick={renovarPrevia}>Atualizar prévia do recebível</Button>}
          <Box><Button variant="contained" disabled={ocupado || bloqueadoPorLote || previaObsoleta} onClick={() => setConfirmando(true)}>Confirmar liquidação</Button></Box>
        </Painel>}
        {liquidacao && <Painel id="resultado" titulo="Liquidação confirmada">
        <Typography>Código da liquidação: {liquidacao.liquidacaoId}</Typography>
        <Valores dados={liquidacao} locale={locale} fusoHorario={fusoHorario} />
        <Box><Button disabled={bloqueadoPorLote} onClick={() => {
          setPendente(null); setLiquidacao(null); setValor(''); setPrazo(''); setVencimento('')
          setPrevia(null); setEstadoPrevia('vazio'); setSucesso(''); setErro(''); setPreviaObsoleta(false)
          aoAtualizarOperacaoPendente(false)
          navegar('simulacao')
        }}>Nova operação</Button></Box>
        </Painel>}
        {!pendente && !liquidacao && <Painel id="liquidacao" titulo="Liquidação">
          <EstadoVazio titulo="Nenhuma operação aguardando liquidação" descricao="Conclua uma simulação e cadastre o recebível antes de confirmar a liquidação." />
          <Button onClick={() => navegar('simulacao')}>Ir para simulação</Button>
        </Painel>}
      </Box>
      <Dialog open={confirmando} onClose={() => setConfirmando(false)} aria-labelledby="confirmar-titulo">
        <DialogTitle id="confirmar-titulo">Confirmar liquidação?</DialogTitle>
        <DialogContent><DialogContentText>O servidor revalidará a prévia. Se o preço ou a fonte mudar, obtenha nova prévia e confirme novamente.</DialogContentText></DialogContent>
        <DialogActions><Button onClick={() => setConfirmando(false)}>Cancelar</Button><Button onClick={liquidar} disabled={ocupado || bloqueadoPorLote}>Liquidar</Button></DialogActions>
      </Dialog>
    </Stack>
  )
}

function Valores({ dados, locale, fusoHorario }: { dados: Simulacao | Liquidacao; locale: string; fusoHorario?: string }) {
  const liquidacao = 'liquidadaEm' in dados
  const instantePrincipal = 'liquidadaEm' in dados
    ? { isoUtc: dados.liquidadaEm, rotulo: 'Data/hora da liquidação' }
    : { isoUtc: dados.referencia, rotulo: 'Referência da simulação' }
  return <Stack spacing={0.5} aria-label="Valores retornados pelo servidor">
    <Typography>Valor presente: {formatarMonetario(dados.valorPresenteBrl, 'BRL', locale)}</Typography>
    <Typography>Deságio: {formatarMonetario(dados.desagioBrl, 'BRL', locale)}</Typography>
    <Typography>Pagamento: {formatarMonetario(dados.valorPagamento, dados.moeda, locale)}</Typography>
    {'taxaBaseMensal' in dados && <>
      <Typography>Taxa base mensal: {formatarPercentual(dados.taxaBaseMensal, locale)}</Typography>
      <Typography>Spread mensal: {formatarPercentual(dados.spreadMensal, locale)}</Typography>
      {dados.cotacaoBrlPorUsd && <Typography>Cotação: 1 USD = R$ {formatarCotacao(dados.cotacaoBrlPorUsd, locale)}</Typography>}
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={{ xs: 0, sm: 1 }}>
        <Typography>{instantePrincipal.rotulo}:</Typography>
        <InstanteLocal isoUtc={instantePrincipal.isoUtc} locale={locale} rotulo={instantePrincipal.rotulo} fusoHorario={fusoHorario} />
      </Stack>
      {dados.cotacaoValidaAte && <Stack direction={{ xs: 'column', sm: 'row' }} spacing={{ xs: 0, sm: 1 }}>
        <Typography>Cotação válida até:</Typography>
        <InstanteLocal isoUtc={dados.cotacaoValidaAte} locale={locale} rotulo="Cotação válida até" fusoHorario={fusoHorario} />
      </Stack>}
    </>}
    {liquidacao && !('taxaBaseMensal' in dados) && <Stack direction={{ xs: 'column', sm: 'row' }} spacing={{ xs: 0, sm: 1 }}>
      <Typography>Data/hora da liquidação:</Typography>
      <InstanteLocal isoUtc={dados.liquidadaEm} locale={locale} rotulo="Data/hora da liquidação" fusoHorario={fusoHorario} />
    </Stack>}
  </Stack>
}

function mapearCursorMonetario(formatado: string, editavel: string, cursor: number, locale: string): number {
  const separador = separadorDecimalMonetario(locale)
  const prefixo = formatado.slice(0, cursor)
  const digitosAntes = (prefixo.match(/\p{Decimal_Number}/gu) ?? []).length
  const passouDecimal = prefixo.includes(separador)
  let digitos = 0

  for (let indice = 0; indice < editavel.length; indice += 1) {
    const caractere = editavel[indice]
    if (caractere === separador && digitos === digitosAntes) return indice + (passouDecimal ? 1 : 0)
    if (/\p{Decimal_Number}/u.test(caractere ?? '')) {
      if (digitos === digitosAntes) return indice
      digitos += 1
    }
  }
  return editavel.length
}
