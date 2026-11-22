import { ErroCambio, type PortaCambio } from '../aplicacao/cambio'
import type { CotacaoCambial, FonteCambial, PaginaCambio, SelecaoCambial } from '../dominio/cambio'

const raiz = '/bff/v1/cotacoes-cambiais'
const objeto = (valor: unknown): valor is Record<string, unknown> => typeof valor === 'object' && valor !== null && !Array.isArray(valor)
const textoNulo = (valor: unknown): valor is string | null => valor === null || typeof valor === 'string'
const fonte = (valor: unknown): valor is FonteCambial => ['BCB_PTAX', 'MANUAL', 'DEMO', 'LEGADO'].includes(String(valor))
const instante = (valor: unknown): valor is string => typeof valor === 'string' && valor.endsWith('Z') && Number.isFinite(Date.parse(valor))
const instanteNulo = (valor: unknown) => valor === null || instante(valor)
const inteiro = (valor: unknown): valor is number => typeof valor === 'number' && Number.isSafeInteger(valor) && valor >= 0

function cotacao(valor: unknown): valor is CotacaoCambial {
  return objeto(valor) && typeof valor.id === 'string' && valor.id.length > 0
    && typeof valor.taxaBrlPorUsd === 'string' && /^[0-9]+(?:\.[0-9]+)?$/.test(valor.taxaBrlPorUsd)
    && /[1-9]/.test(valor.taxaBrlPorUsd) && valor.moedaBase === 'BRL' && valor.moedaCotada === 'USD'
    && valor.lado === 'VENDA' && fonte(valor.fonte) && inteiro(valor.versao)
    && instante(valor.referencia) && instante(valor.observadaEm)
    && ['dataBoletim', 'horarioOriginal', 'boletim', 'fuso', 'autorId', 'justificativa'].every((chave) => textoNulo(valor[chave]))
}

function selecao(valor: unknown): valor is SelecaoCambial {
  return objeto(valor) && typeof valor.id === 'string' && valor.id.length > 0 && fonte(valor.fonte)
    && textoNulo(valor.cotacaoId) && instante(valor.autorizadoEm) && instanteNulo(valor.validaAte)
    && typeof valor.politicaId === 'string' && textoNulo(valor.autorId) && textoNulo(valor.justificativa)
    && (valor.fonte !== 'MANUAL' || (typeof valor.cotacaoId === 'string' && instante(valor.validaAte)))
}

function pagina<T>(validar: (valor: unknown) => valor is T) {
  return (valor: unknown): valor is PaginaCambio<T> => objeto(valor) && Array.isArray(valor.itens)
    && valor.itens.every(validar) && inteiro(valor.pagina) && inteiro(valor.tamanho)
    && valor.tamanho >= 1 && valor.tamanho <= 100 && inteiro(valor.total)
}

const mensagens: Record<number, string> = {
  400: 'Dados inválidos. Confira a taxa e a justificativa.',
  401: 'Sessão expirada. Entre novamente.',
  403: 'Seu perfil não tem permissão para esta ação.',
  409: 'Conflito: a cotação pode já ter sido autorizada, ser de outro dia ou não estar disponível. Atualize os históricos.',
  503: 'Serviço de câmbio indisponível. Nenhuma troca de fonte é automática.',
}

async function requisitar<T>(caminho: string, validar: (valor: unknown) => valor is T, token: string, opcoes: RequestInit = {}): Promise<T> {
  const escrita = opcoes.method === 'POST'
  let resposta: Response
  try {
    resposta = await fetch(caminho, {
      ...opcoes,
      headers: { Authorization: `Bearer ${token}`, ...(opcoes.body ? { 'Content-Type': 'application/json' } : {}) },
    })
  } catch (falha) {
    if (falha instanceof DOMException && falha.name === 'AbortError') throw falha
    throw new ErroCambio('Não foi possível conectar ao servidor. Verifique a conexão.', undefined, escrita)
  }
  if (!resposta.ok) throw new ErroCambio(mensagens[resposta.status] ?? `Falha no câmbio (HTTP ${resposta.status}).`, resposta.status,
    escrita && resposta.status >= 500)
  let corpo: unknown
  try {
    corpo = await resposta.json()
  } catch {
    throw new ErroCambio('Resposta inválida do servidor.', undefined, escrita)
  }
  if (!validar(corpo)) throw new ErroCambio('Resposta inválida do servidor.', undefined, escrita)
  return corpo
}

export const bffCambio: PortaCambio = {
  historico: (token, consulta, signal) => {
    const parametros = new URLSearchParams({ pagina: String(consulta.pagina), tamanho: String(consulta.tamanho) })
    if (consulta.fonte) parametros.set('fonte', consulta.fonte)
    return requisitar(`${raiz}?${parametros}`, pagina(cotacao), token, { signal })
  },
  selecoes: (token, consulta, signal) => requisitar(`${raiz}/selecoes?${new URLSearchParams({
    pagina: String(consulta.pagina), tamanho: String(consulta.tamanho),
  })}`, pagina(selecao), token, { signal }),
  cadastrar: (token, entrada) => requisitar(`${raiz}/manuais`, cotacao, token, {
    method: 'POST', body: JSON.stringify({ taxaBrlPorUsd: entrada.taxaBrlPorUsd, justificativa: entrada.justificativa }),
  }),
  selecionar: (token, entrada) => requisitar(`${raiz}/selecoes`, selecao, token, {
    method: 'POST', body: JSON.stringify(entrada.fonte === 'MANUAL'
      ? { fonte: 'MANUAL', cotacaoId: entrada.cotacaoId, justificativa: entrada.justificativa }
      : { fonte: 'BCB_PTAX', justificativa: entrada.justificativa }),
  }),
  sincronizar: (token) => requisitar(`${raiz}/ptax/sincronizacoes`, (valor): valor is { versoesIncluidas: number } =>
    objeto(valor) && inteiro(valor.versoesIncluidas), token, { method: 'POST' }),
}
