import { ErroLote, type PortaLotes } from '../aplicacao/lotes'
import type {
  CadastroLote, ComandoImportacao, LinhaLote, PreviaCadastro, PreviaFinanceiraLote,
  PreviaItemLote, ResultadoLiquidacaoLote,
} from '../dominio/lotes'
import type { Simulacao } from '../dominio/operacoes'

const objeto = (valor: unknown): valor is Record<string, unknown> =>
  typeof valor === 'object' && valor !== null && !Array.isArray(valor)
const texto = (valor: unknown): valor is string => typeof valor === 'string' && valor.length > 0
const decimal = (valor: unknown): valor is string => typeof valor === 'string' && /^\d+(\.\d+)?$/.test(valor)
const moeda = (valor: unknown) => valor === 'BRL' || valor === 'USD'
const instante = (valor: unknown) => texto(valor) && Number.isFinite(Date.parse(valor))
const nuloOu = (valor: unknown, validar: (valor: unknown) => boolean) => valor === null || validar(valor)

function comando(valor: unknown): valor is ComandoImportacao {
  return objeto(valor) && texto(valor.referenciaExterna) && texto(valor.cedenteId)
    && (valor.modalidade === 'DUPLICATA_MERCANTIL' || valor.modalidade === 'CHEQUE_PRE_DATADO')
    && decimal(valor.valorFaceBrl) && Number.isSafeInteger(valor.prazoMeses)
    && typeof valor.prazoMeses === 'number' && valor.prazoMeses > 0
    && nuloOu(valor.vencimento, texto) && moeda(valor.moedaPagamento)
}
function simulacao(valor: unknown): valor is Simulacao {
  return objeto(valor) && moeda(valor.moeda)
    && ['valorPresenteBrl', 'desagioBrl', 'valorPagamento', 'taxaBaseMensal', 'spreadMensal'].every((campo) => decimal(valor[campo]))
    && nuloOu(valor.cotacaoBrlPorUsd, decimal) && instante(valor.referencia) && nuloOu(valor.cotacaoValidaAte, instante)
}
function linha(valor: unknown): valor is LinhaLote {
  return objeto(valor) && Number.isSafeInteger(valor.linha) && typeof valor.linha === 'number' && valor.linha >= 2
    && ['NOVO', 'JA_CADASTRADO', 'JA_LIQUIDADO', 'INVALIDO'].includes(String(valor.status))
    && nuloOu(valor.comando, comando) && Array.isArray(valor.erros)
    && valor.erros.every((erro) => objeto(erro) && texto(erro.campo) && texto(erro.mensagem))
    && nuloOu(valor.simulacao, simulacao) && nuloOu(valor.avisoSimulacao, texto) && nuloOu(valor.recebivelId, texto)
}
function previaCadastro(valor: unknown): valor is PreviaCadastro {
  return objeto(valor) && typeof valor.confirmavel === 'boolean' && Array.isArray(valor.itens)
    && valor.itens.length > 0 && valor.itens.every(linha)
}
function cadastro(valor: unknown): valor is CadastroLote {
  return objeto(valor) && texto(valor.loteId) && Number.isSafeInteger(valor.cadastrados)
    && Array.isArray(valor.itens) && valor.itens.length > 0
    && valor.itens.every((item) => objeto(item) && texto(item.referenciaExterna) && texto(item.recebivelId))
}
function previaItem(valor: unknown): valor is PreviaItemLote {
  return objeto(valor) && texto(valor.recebivelId) && Number.isSafeInteger(valor.versao) && texto(valor.estado)
    && moeda(valor.moedaPagamento) && decimal(valor.valorFaceBrl)
    && ['valorPresenteBrl', 'desagioBrl', 'valorPagamento', 'taxaBaseMensal', 'spreadMensal', 'taxaBrlPorUsd']
      .every((campo) => nuloOu(valor[campo], decimal))
    && ['taxaBaseId', 'spreadId', 'cotacaoId', 'fonteCambio', 'impedimento'].every((campo) => nuloOu(valor[campo], texto))
    && ['taxaBaseVigenteEm', 'spreadVigenteEm', 'cotacaoVigenteEm', 'cotacaoValidaAte']
      .every((campo) => nuloOu(valor[campo], instante))
}
function previaFinanceira(valor: unknown): valor is PreviaFinanceiraLote {
  return objeto(valor) && texto(valor.loteId) && instante(valor.referencia)
    && typeof valor.aptoParaLiquidar === 'boolean' && nuloOu(valor.hashPrevia, texto)
    && Array.isArray(valor.itens) && valor.itens.every(previaItem) && objeto(valor.totaisPorMoeda)
    && Object.entries(valor.totaisPorMoeda).every(([chave, total]) => moeda(chave) && decimal(total))
}
function resultado(valor: unknown): valor is ResultadoLiquidacaoLote {
  return objeto(valor) && texto(valor.loteId) && instante(valor.liquidadaEm) && Array.isArray(valor.itens)
    && valor.itens.length > 0 && valor.itens.every((item) => objeto(item) && texto(item.liquidacaoId)
      && texto(item.recebivelId) && moeda(item.moedaPagamento)
      && ['valorPresenteBrl', 'desagioBrl', 'valorPagamento'].every((campo) => decimal(item[campo])))
}

async function post<T>(
  caminho: string, token: string, corpo: FormData | object,
  validar: (valor: unknown) => valor is T, chave?: string,
): Promise<T> {
  const multipart = corpo instanceof FormData
  let resposta: Response
  try {
    resposta = await fetch(caminho, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        ...(!multipart ? { 'Content-Type': 'application/json' } : {}),
        ...(chave ? { 'Idempotency-Key': chave } : {}),
      },
      body: multipart ? corpo : JSON.stringify(corpo),
    })
  } catch {
    throw new ErroLote('Conexão interrompida. Se confirmou uma operação, o resultado é incerto: repita o mesmo comando com a mesma chave.')
  }
  let dados: unknown
  try { dados = await resposta.json() } catch {
    if (resposta.ok) throw new ErroLote('Resposta inválida. Resultado incerto: preserve a chave e repita o mesmo comando.')
  }
  if (!resposta.ok) {
    const mensagens: Record<number, string> = {
      400: 'Dados inválidos. Nenhum item desta tentativa foi confirmado. Revise o arquivo ou obtenha nova prévia.',
      401: 'Sessão expirada. Entre novamente com a mesma conta para repetir o comando pendente.',
      403: 'Acesso não autorizado. Nenhuma confirmação permitida.',
      404: 'Lote não encontrado no escopo da sessão.',
      409: 'Conflito ou prévia obsoleta: rejeição integral, com rollback de toda esta tentativa. Obtenha nova prévia e confirme novamente.',
      413: 'Arquivo acima do limite permitido pelo servidor.',
      422: 'Lote inválido: nenhum recebível foi cadastrado. Corrija todas as linhas indicadas.',
      503: 'Serviço ou dados financeiros indisponíveis. Se confirmou, preserve a chave e repita o mesmo comando para verificar o resultado.',
    }
    const itens = objeto(dados) && Array.isArray(dados.itens) && dados.itens.every(linha) ? dados.itens : undefined
    throw new ErroLote(mensagens[resposta.status] ?? `Erro HTTP ${resposta.status}. Preserve a chave da confirmação pendente.`, resposta.status, itens)
  }
  if (!validar(dados)) throw new ErroLote('Resposta incompleta do servidor. Resultado incerto: preserve a chave e repita o mesmo comando.')
  return dados
}

const caminhoLote = (id: string) => `/bff/v1/lotes/${encodeURIComponent(id)}`
export const bffLotes: PortaLotes = {
  prevalidar(token, arquivo) {
    const form = new FormData()
    form.append('arquivo', arquivo)
    return post('/bff/v1/lotes/recebiveis/validacoes', token, form, previaCadastro)
  },
  cadastrar: (token, entrada, chave) => post('/bff/v1/lotes/recebiveis', token, entrada, cadastro, chave),
  previsualizar: (token, id, itens) => post(`${caminhoLote(id)}/previas-liquidacao`, token, { itens }, previaFinanceira),
  liquidar: (token, id, entrada, chave) => post(`${caminhoLote(id)}/liquidacoes`, token, entrada, resultado, chave),
}
