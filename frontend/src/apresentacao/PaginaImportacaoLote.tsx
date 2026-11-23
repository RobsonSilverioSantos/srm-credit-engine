import { useEffect, useRef, useState, useSyncExternalStore, type ReactNode } from 'react'
import {
  Box, Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, Link,
  Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Typography,
} from '@mui/material'
import { FluxoLote, type PortaLotes } from '../aplicacao/lotes'
import { cadastroCompleto, previaCompleta, simulacaoCompleta, type LinhaLote, type PreviaItemLote } from '../dominio/lotes'
import { formatarCotacao, formatarMonetario, formatarPercentual } from '../dominio/formatacaoMonetaria'
import { Carregando, Mensagem } from '../tema/componentes/Feedback'
import { InstanteLocal } from '../tema/componentes/InstanteLocal'
import { Painel } from '../tema/componentes/Painel'

type Props = {
  porta: PortaLotes
  token: string | null
  locale: string
  fusoHorario?: string
  tratarErro: (erro: unknown) => string
  aoAtualizarOperacaoEmCurso: (ocupado: boolean) => void
  aoAtualizarOperacaoPendente: (pendente: boolean) => void
  bloqueadoExternamente?: boolean
}

function Grade({ titulo, cabecalhos, children }: { titulo: string; cabecalhos: string[]; children: ReactNode }) {
  return (
    <TableContainer component={Box} tabIndex={0} role="region" aria-label={titulo} sx={{ maxWidth: '100%', overflowX: 'auto' }}>
      <Table size="small" aria-label={titulo}>
        <TableHead><TableRow>{cabecalhos.map((nome) => <TableCell key={nome} scope="col">{nome}</TableCell>)}</TableRow></TableHead>
        <TableBody>{children}</TableBody>
      </Table>
    </TableContainer>
  )
}

const modalidade = (valor: string) => valor === 'DUPLICATA_MERCANTIL' ? 'Duplicata mercantil' : 'Cheque pré-datado'

function relatorioErros(itens: LinhaLote[]): string {
  return JSON.stringify(itens.filter((item) => item.status !== 'NOVO' || item.erros.length || item.avisoSimulacao)
    .map(({ linha, status, comando, erros, avisoSimulacao }) =>
      ({ linha, status, referenciaExterna: comando?.referenciaExterna, erros, avisoSimulacao })), null, 2)
}

export function PaginaImportacaoLote({
  porta, token, locale, fusoHorario, tratarErro, aoAtualizarOperacaoEmCurso, aoAtualizarOperacaoPendente, bloqueadoExternamente = false,
}: Props) {
  const [fluxo] = useState(() => new FluxoLote(porta))
  const estado = useSyncExternalStore(fluxo.assinar, fluxo.snapshot)
  const [arquivo, setArquivo] = useState<File | null>(null)
  const [versaoArquivo, setVersaoArquivo] = useState(0)
  const [agora, setAgora] = useState(() => Date.now())
  const [erro, setErro] = useState('')
  const focoAposConfirmacao = useRef<string | null>(null)
  const focoAbertura = useRef<HTMLElement | null>(null)
  const [confirmacao, setConfirmacao] = useState<{ tipo: 'cadastro' | 'liquidacao'; token: string } | null>(null)
  const modal = confirmacao?.token === token ? confirmacao?.tipo ?? null : null
  const setModal = (tipo: 'cadastro' | 'liquidacao' | null) => {
    if (tipo) focoAbertura.current = document.activeElement instanceof HTMLElement ? document.activeElement : null
    setConfirmacao(tipo && token ? { tipo, token } : null)
  }
  const { ocupado, previaCadastro, cadastro, previaFinanceira, resultado, cadastroPendente, liquidacaoPendente, itensLiquidacao } = estado
  const pendente = Boolean(cadastroPendente || liquidacaoPendente)
  const bloqueado = ocupado || !token || bloqueadoExternamente
  const apto = previaCompleta(previaFinanceira, itensLiquidacao, agora)
  const podeConfirmarLiquidacao = apto || Boolean(liquidacaoPendente)
  const validos = previaFinanceira?.itens.filter((item) => simulacaoCompleta(item, agora)).length ?? 0
  const moeda = (valor: string | null | undefined, unidade: 'BRL' | 'USD' = 'BRL') =>
    valor == null ? 'Indisponível' : formatarMonetario(valor, unidade, locale)

  useEffect(() => { aoAtualizarOperacaoEmCurso(ocupado) }, [ocupado, aoAtualizarOperacaoEmCurso])
  useEffect(() => { aoAtualizarOperacaoPendente(pendente) }, [pendente, aoAtualizarOperacaoPendente])
  useEffect(() => {
    if (!previaFinanceira) return
    const vencimentos = previaFinanceira.itens
      .filter((item) => item.moedaPagamento === 'USD' && item.cotacaoValidaAte)
      .map((item) => Date.parse(item.cotacaoValidaAte!)).filter((instante) => instante > agora)
    if (!vencimentos.length) return
    const espera = Math.min(2147483647, Math.max(0, Math.min(...vencimentos) - Date.now() + 1))
    const timer = window.setTimeout(() => setAgora(Date.now()), espera)
    return () => window.clearTimeout(timer)
  }, [previaFinanceira, agora])

  async function executar(acao: () => Promise<void>) {
    if (!token || ocupado || bloqueadoExternamente) return
    setAgora(Date.now())
    setErro('')
    const anterior = fluxo.snapshot()
    try {
      await acao()
      const atual = fluxo.snapshot()
      if (!anterior.resultado && atual.resultado) focoAposConfirmacao.current = 'resultado-financeiro'
      else if (!anterior.cadastro && atual.cadastro) focoAposConfirmacao.current = 'lote-cadastrado'
    } catch (falha) { setErro(tratarErro(falha)) }
    finally { setModal(null) }
  }

  function novoLote() {
    if (bloqueadoExternamente) return
    try {
      fluxo.novo()
      setArquivo(null)
      setVersaoArquivo((valor) => valor + 1)
      setErro('')
    } catch (falha) { setErro(tratarErro(falha)) }
  }

  const totais = previaFinanceira && (
    <Stack spacing={1}>
      <Typography fontWeight={700}>Totais indicativos por moeda (servidor)</Typography>
      {Object.entries(previaFinanceira.totaisPorMoeda).map(([unidade, total]) => (
        <Typography key={unidade}>{unidade}: {moeda(total, unidade as 'BRL' | 'USD')}</Typography>
      ))}
    </Stack>
  )
  function detalhesTaxas(item: PreviaItemLote) {
    return (
      <Stack spacing={0.5}>
        <Typography variant="body2">Base: {item.taxaBaseMensal ? formatarPercentual(item.taxaBaseMensal, locale) : 'Indisponível'}; spread: {item.spreadMensal ? formatarPercentual(item.spreadMensal, locale) : 'Indisponível'}</Typography>
        {item.moedaPagamento === 'USD' && <>
          <Typography variant="body2">Câmbio BRL/USD: {item.taxaBrlPorUsd ? formatarCotacao(item.taxaBrlPorUsd, locale) : 'Indisponível'}; fonte: {item.fonteCambio ?? 'Indisponível'}</Typography>
          {item.cotacaoValidaAte && <InstanteLocal isoUtc={item.cotacaoValidaAte} rotulo="Câmbio válido até" locale={locale} fusoHorario={fusoHorario} />}
        </>}
        <Typography variant="body2">Versão: {item.versao}</Typography>
      </Stack>
    )
  }

  return (
    <Stack spacing={3}>
      <Mensagem>Importação cadastra recebíveis, mas não liquida. Pré-validação e prévia financeira não gravam dados nem reservam preço. Cadastro e liquidação exigem confirmações distintas.</Mensagem>
      {bloqueadoExternamente && <Mensagem severity="warning">Resolva a operação individual pendente em Simulação / Liquidação antes de iniciar ou confirmar outro lote.</Mensagem>}
      {erro && <Mensagem severity="error">{erro}</Mensagem>}
      {pendente && !ocupado && <Mensagem severity="warning">
        Resultado da confirmação pendente ou incerto. Não envie outro lote nem atualize a prévia: repita a mesma confirmação com a mesma chave.
        A chave e o comando ficam apenas em memória; não feche ou recarregue esta página antes de verificar o resultado.
      </Mensagem>}
      {ocupado && <Carregando rotulo="Processando lote… Aguarde sem repetir a operação." />}
      <Painel id="arquivo-lote" titulo="1. Selecionar e pré-validar arquivo">
        <Stack spacing={2}>
          <Typography>CSV UTF-8 separado por ponto e vírgula (;) ou XLSX padronizado, sem fórmulas, macros ou vínculos. Limites iniciais: 200 linhas e 2 MiB, sujeitos à configuração do servidor.</Typography>
          <Typography>Modelo por CNPJ brasileiro: referenciaExterna, cedenteDocumento, modalidade, valorFaceBrl, prazoMeses, vencimento, moedaPagamento. Use alternativamente o modelo UUID (cedenteId), inclusive para cedentes estrangeiros. Não misture os dois cabeçalhos nem altere a ordem das colunas.</Typography>
          <Typography>referenciaExterna identifica o título/recebível de origem, com referência única entre lotes na organização; não identifica a organização. A organização vem exclusivamente da sessão JWT validada pelo servidor, nunca do arquivo.</Typography>
          <Typography>cedenteDocumento é o CNPJ brasileiro do cedente, numérico ou alfanumérico, com ou sem máscara completa. Mantenha a coluna como texto, inclusive ao abrir CSV em planilhas, para preservar zeros à esquerda. O servidor valida o documento e resolve o UUID de um cedente ativo da sua organização; a conferência abaixo mostra esse UUID. Validação de formato e dígitos não comprova existência ou regularidade fiscal na Receita Federal.</Typography>
          <Typography>Modalidade DUPLICATA_MERCANTIL ou CHEQUE_PRE_DATADO; valor decimal canônico 25000.00; meses inteiros; vencimento opcional como texto ISO 2027-02-04; moeda BRL ou USD.</Typography>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <Link href="/modelos/modelo-lote-cnpj.csv" download>Baixar modelo CSV por CNPJ</Link>
            <Link href="/modelos/modelo-lote-cnpj.xlsx" download>Baixar modelo XLSX por CNPJ</Link>
            <Link href="/modelos/modelo-lote.csv" download>Baixar modelo CSV por UUID (legado)</Link>
            <Link href="/modelos/modelo-lote.xlsx" download>Baixar modelo XLSX por UUID (legado)</Link>
          </Stack>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <Link href="/modelos/lote-a-200.csv" download>Baixar massa demonstrativa A (200)</Link>
            <Link href="/modelos/lote-b-200.csv" download>Baixar massa demonstrativa B (200, 4 repetidos)</Link>
          </Stack>
          <Typography variant="body2">Substitua CEDENTE_CNPJ_AQUI ou CEDENTE_ID_AQUI pelo identificador de um cedente cadastrado e ativo. Não preencha ambos. Modelos e massas A/B são exclusivamente demonstrativos; as massas mantêm o modelo UUID. Para usá-las por CNPJ, troque somente o cabeçalho cedenteId por cedenteDocumento e os identificadores por CNPJ como texto; preserve as quatro referências repetidas para aferir os conflitos.</Typography>
          <Box component="input" type="file" aria-label="Arquivo CSV ou XLSX" accept=".csv,.xlsx"
            disabled={bloqueado || Boolean(cadastro) || pendente}
            key={versaoArquivo}
            onChange={(evento) => {
              if (bloqueado) return
              fluxo.novo()
              setErro('')
              setArquivo(evento.target.files?.[0] ?? null)
            }} sx={{ maxWidth: '100%' }} />
          {arquivo && <Typography>Arquivo selecionado: {arquivo.name}</Typography>}
          <Box><Button variant="contained" disabled={bloqueado || !arquivo || Boolean(cadastro) || pendente}
            onClick={() => executar(() => fluxo.prevalidar(token!, arquivo!))}>Pré-validar arquivo</Button></Box>
        </Stack>
      </Painel>
      {previaCadastro && <Painel id="conferencia-cadastro" titulo="2. Conferir e confirmar cadastro">
        <Stack spacing={2}>
          <Typography role="status">{previaCadastro.itens.filter((item) => item.status === 'NOVO' && !item.erros.length).length}/{previaCadastro.itens.length} linhas válidas para cadastro</Typography>
          {!cadastroCompleto(previaCadastro) && <Mensagem severity="error">Cadastro bloqueado para o lote inteiro. Corrija todas as linhas inválidas ou repetidas e reenvie o arquivo; nenhuma linha é descartada automaticamente.</Mensagem>}
          <Link download="erros-lote.json" href={`data:application/json;charset=utf-8,${encodeURIComponent(relatorioErros(previaCadastro.itens))}`}>Baixar relatório de erros e avisos</Link>
          {previaCadastro.itens.some((item) => item.status !== 'NOVO') && <Stack component="nav" aria-label="Linhas com impedimentos">
            {previaCadastro.itens.filter((item) => item.status !== 'NOVO').map((item) =>
              <Link key={item.linha} href={`#linha-lote-${item.linha}`} onClick={(evento) => {
                evento.preventDefault()
                document.getElementById(`linha-lote-${item.linha}`)?.focus()
              }}>Ir à linha {item.linha}: {item.status}</Link>)}
          </Stack>}
          <Grade titulo="Pré-validação de todas as linhas" cabecalhos={['Linha / referência', 'Cedente / recebível', 'Título', 'Prévia indicativa', 'Status / mensagens']}>
            {previaCadastro.itens.map((item) => <TableRow key={item.linha} id={`linha-lote-${item.linha}`} tabIndex={-1}>
              <TableCell component="th" scope="row">{item.linha} / {item.comando?.referenciaExterna ?? 'Inválida'}</TableCell>
              <TableCell sx={{ overflowWrap: 'anywhere' }}>{item.comando?.cedenteId ?? 'Indisponível'}{item.recebivelId && <Typography variant="body2">Recebível: {item.recebivelId}</Typography>}</TableCell>
              <TableCell>{item.comando && <>{modalidade(item.comando.modalidade)}; {moeda(item.comando.valorFaceBrl)}; {item.comando.prazoMeses} meses; {item.comando.vencimento ?? 'Sem vencimento'}; {item.comando.moedaPagamento}</>}</TableCell>
              <TableCell>{item.simulacao ? <>VP: {moeda(item.simulacao.valorPresenteBrl)}; deságio: {moeda(item.simulacao.desagioBrl)}; pagamento: {moeda(item.simulacao.valorPagamento, item.simulacao.moeda)}</> : 'Simulação indisponível'}</TableCell>
              <TableCell>{item.status}{item.erros.map((falha, indice) => <Typography key={indice} variant="body2">{falha.campo}: {falha.mensagem}</Typography>)}{item.avisoSimulacao && <Typography variant="body2">Aviso financeiro (não impede cadastro): {item.avisoSimulacao}</Typography>}</TableCell>
            </TableRow>)}
          </Grade>
          {!cadastro && <Box><Button variant="contained" disabled={bloqueado || !cadastroCompleto(previaCadastro)}
            onClick={() => setModal('cadastro')}>{cadastroPendente ? 'Repetir confirmação de cadastro' : 'Conferir cadastro'}</Button></Box>}
        </Stack>
      </Painel>}
      {cadastro && <Painel id="lote-cadastrado" titulo="3. Cadastro confirmado — não liquidado automaticamente">
        <Stack spacing={2}>
          <Mensagem severity="success">{cadastro.cadastrados} recebíveis cadastrados. Código do lote: {cadastro.loteId}. {resultado ? 'Liquidação confirmada na etapa financeira abaixo.' : 'Nenhuma liquidação realizada por este cadastro; títulos cadastrados como DISPONIVEL.'}</Mensagem>
          <Grade titulo="Recebíveis cadastrados para auditoria" cabecalhos={['Referência', 'Código do recebível', 'Estado confirmado nesta sessão']}>
            {cadastro.itens.map((item) => <TableRow key={item.recebivelId}>
              <TableCell component="th" scope="row">{item.referenciaExterna}</TableCell>
              <TableCell sx={{ overflowWrap: 'anywhere' }}>{item.recebivelId}</TableCell>
              <TableCell>{resultado ? 'LIQUIDADO' : previaFinanceira?.itens.find((previa) => previa.recebivelId === item.recebivelId)?.estado ?? 'DISPONIVEL no cadastro'}</TableCell>
            </TableRow>)}
          </Grade>
          {!resultado && <Box><Button variant="outlined" disabled={bloqueado || Boolean(liquidacaoPendente)}
            onClick={() => executar(() => fluxo.previsualizar(token!))}>{previaFinanceira ? 'Atualizar prévia financeira' : 'Obter prévia financeira de todos os itens'}</Button></Box>}
        </Stack>
      </Painel>}
      {previaFinanceira && !resultado && <Painel id="conferencia-financeira" titulo="4. Conferir liquidação integral">
        <Stack spacing={2}>
          <Typography role="status">{validos}/{itensLiquidacao.length} simulações válidas</Typography>
          <InstanteLocal isoUtc={previaFinanceira.referencia} rotulo="Referência da prévia financeira" locale={locale} fusoHorario={fusoHorario} />
          <Mensagem severity={apto ? 'info' : 'warning'}>{apto ? '100% dos títulos possuem simulação válida. Confira todos os itens e os totais antes da confirmação separada.'
            : liquidacaoPendente ? 'Prévia expirada ou inválida para nova operação. Somente a repetição do comando pendente com a mesma chave está permitida para verificar seu resultado.'
              : 'Liquidação bloqueada: prévia inválida, incompleta ou câmbio vencido. Atualize a prévia; todos e somente os títulos devem estar disponíveis, com simulação válida e hash de revisão.'}</Mensagem>
          {totais}
          <Grade titulo="Simulações financeiras de todos os títulos" cabecalhos={['Referência / recebível', 'Estado / impedimento', 'Face / VP / deságio em BRL', 'Pagamento indicativo', 'Taxas / câmbio']}>
            {previaFinanceira.itens.map((item, indice) => <TableRow key={`${item.recebivelId}-${indice}`}>
              <TableCell component="th" scope="row" sx={{ overflowWrap: 'anywhere' }}>{cadastro?.itens.find((cadastrado) => cadastrado.recebivelId === item.recebivelId)?.referenciaExterna} / {item.recebivelId}</TableCell>
              <TableCell>{item.estado}; {item.impedimento ?? (simulacaoCompleta(item, agora) ? 'Simulação válida' : 'Simulação incompleta ou câmbio vencido')}</TableCell>
              <TableCell>{moeda(item.valorFaceBrl)} / {moeda(item.valorPresenteBrl)} / {moeda(item.desagioBrl)}</TableCell>
              <TableCell>{moeda(item.valorPagamento, item.moedaPagamento)}</TableCell>
              <TableCell>{detalhesTaxas(item)}</TableCell>
            </TableRow>)}
          </Grade>
          <Box><Button variant="contained" disabled={bloqueado || !podeConfirmarLiquidacao} onClick={() => setModal('liquidacao')}>
            {liquidacaoPendente ? 'Repetir confirmação de liquidação' : 'Liquidar lote'}
          </Button></Box>
        </Stack>
      </Painel>}
      {resultado && <Painel id="resultado-financeiro" titulo="Liquidação integral confirmada">
        <Stack spacing={2}>
          <Mensagem severity="success">Todos os {resultado.itens.length} títulos foram liquidados. Não há sucesso parcial. Código do lote: {resultado.loteId}.</Mensagem>
          <InstanteLocal isoUtc={resultado.liquidadaEm} rotulo="Liquidação efetiva" locale={locale} fusoHorario={fusoHorario} />
          <Typography>Valores efetivos confirmados pelo servidor, não uma reserva de preço da prévia. Consulte o extrato para os snapshots auditáveis.</Typography>
          <Grade titulo="Resultados financeiros efetivos" cabecalhos={['Recebível / código da liquidação', 'Estado', 'VP / deságio em BRL', 'Pagamento efetivo']}>
            {resultado.itens.map((item) => <TableRow key={item.liquidacaoId}>
              <TableCell component="th" scope="row" sx={{ overflowWrap: 'anywhere' }}>{item.recebivelId} / {item.liquidacaoId}</TableCell>
              <TableCell>LIQUIDADO</TableCell><TableCell>{moeda(item.valorPresenteBrl)} / {moeda(item.desagioBrl)}</TableCell>
              <TableCell>{moeda(item.valorPagamento, item.moedaPagamento)}</TableCell>
            </TableRow>)}
          </Grade>
        </Stack>
      </Painel>}
      {cadastro && <Box><Button disabled={bloqueado || pendente} onClick={novoLote}>Iniciar outro lote</Button></Box>}
      <Dialog open={modal !== null} onClose={ocupado ? undefined : () => setModal(null)} fullWidth maxWidth="sm" aria-labelledby="confirmacao-lote-titulo"
        slotProps={{ transition: { onExited: () => {
          if (focoAposConfirmacao.current) document.getElementById(focoAposConfirmacao.current)?.focus()
          else focoAbertura.current?.focus()
          focoAposConfirmacao.current = null
          focoAbertura.current = null
        } } }}>
        <DialogTitle id="confirmacao-lote-titulo">{modal === 'cadastro' ? 'Confirmar cadastro' : 'Confirmar liquidação'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2}>
            <DialogContentText>{modal === 'cadastro'
              ? `Cadastrar ${previaCadastro?.itens.length ?? 0} recebíveis em uma única transação, sem liquidar? Qualquer erro rejeita o lote inteiro.`
              : `Liquidar todos os ${itensLiquidacao.length} títulos do lote ${cadastro?.loteId}? Preços e câmbio são indicativos e serão revalidados. Divergência exige nova prévia e reconfirmação. Qualquer falha causa rollback integral, sem sucesso parcial.`}</DialogContentText>
            {modal === 'liquidacao' && totais}
            {pendente && <Mensagem severity="warning">Repetição do comando original com a mesma chave, sem nova operação.</Mensagem>}
          </Stack>
        </DialogContent>
        <DialogActions sx={{ flexWrap: 'wrap' }}>
          <Button disabled={ocupado} onClick={() => setModal(null)}>Cancelar</Button>
          <Button variant="contained" disabled={bloqueado || (modal === 'cadastro' ? !cadastroCompleto(previaCadastro) : !podeConfirmarLiquidacao)}
            onClick={() => executar(() => modal === 'cadastro' ? fluxo.cadastrar(token!) : fluxo.liquidar(token!))}>
            {modal === 'cadastro' ? 'Confirmar cadastro' : 'Confirmar liquidação'}
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  )
}
