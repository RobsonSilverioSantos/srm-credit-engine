import { ErroOperacao, type PortaOperacoes } from '../aplicacao/operacoes'
import type { Cedente, EntradaRecebivel, EntradaSimulacao, Liquidacao, Moeda, Recebivel, Sessao, Simulacao } from '../dominio/operacoes'

function objeto(valor: unknown): valor is Record<string, unknown> {
  return typeof valor === 'object' && valor !== null && !Array.isArray(valor)
}

function cedente(valor: unknown): valor is Cedente {
  return objeto(valor) && typeof valor.id === 'string' && valor.id.length > 0
    && typeof valor.nome === 'string' && typeof valor.documento === 'string' && typeof valor.ativo === 'boolean'
}

function valores(valor: Record<string, unknown>): boolean {
  return (valor.moeda === 'BRL' || valor.moeda === 'USD') && typeof valor.valorPresenteBrl === 'string'
    && typeof valor.desagioBrl === 'string' && typeof valor.valorPagamento === 'string'
}

function simulacao(valor: unknown): valor is Simulacao {
  return objeto(valor) && valores(valor) && typeof valor.taxaBaseMensal === 'string'
    && typeof valor.spreadMensal === 'string' && (typeof valor.cotacaoBrlPorUsd === 'string' || valor.cotacaoBrlPorUsd === null)
    && typeof valor.referencia === 'string' && (typeof valor.cotacaoValidaAte === 'string' || valor.cotacaoValidaAte === null)
}

function liquidacao(valor: unknown): valor is Liquidacao {
  return objeto(valor) && valores(valor) && typeof valor.liquidacaoId === 'string' && valor.liquidacaoId.length > 0
    && typeof valor.recebivelId === 'string' && valor.recebivelId.length > 0 && typeof valor.liquidadaEm === 'string'
}

async function requisitar<T>(
  caminho: string, validar: (valor: unknown) => valor is T, opcoes: RequestInit = {}, token?: string,
): Promise<T> {
  let resposta: Response
  try {
    resposta = await fetch(caminho, {
      ...opcoes,
      headers: {
        ...(opcoes.body ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...opcoes.headers,
      },
    })
  } catch (erro) {
    if (erro instanceof DOMException && erro.name === 'AbortError') throw erro
    throw new ErroOperacao('Não foi possível conectar ao servidor. Verifique a conexão.')
  }
  if (!resposta.ok) {
    const mensagens: Record<number, string> = {
      400: 'Dados inválidos. Confira os campos informados.',
      401: token ? 'Sessão expirada. Entre novamente.' : 'Credenciais inválidas.',
      403: 'Acesso não autorizado.',
      404: 'Registro não encontrado.',
      409: 'A operação conflita com o estado atual. Confira os dados antes de continuar.',
      503: 'Serviço ou dados financeiros indisponíveis. Tente novamente.',
    }
    throw new ErroOperacao(mensagens[resposta.status] ?? `Erro do servidor (HTTP ${resposta.status}).`, resposta.status)
  }
  let corpo: unknown
  try { corpo = await resposta.json() } catch {
    throw new ErroOperacao('Resposta inválida do servidor.')
  }
  if (!validar(corpo)) throw new ErroOperacao('Resposta inválida do servidor.')
  return corpo
}

export const bff: PortaOperacoes = {
  async autenticar(email, senha): Promise<Sessao> {
    const inicio = Date.now()
    const resposta = await requisitar('/api/v1/auth/token', (valor): valor is { accessToken: string; expiresIn: number } =>
      objeto(valor) && typeof valor.accessToken === 'string' && typeof valor.expiresIn === 'number', {
      method: 'POST', body: JSON.stringify({ email, password: senha }),
    })
    if (!resposta.accessToken || !Number.isFinite(resposta.expiresIn) || resposta.expiresIn <= 0) {
      throw new ErroOperacao('Resposta de autenticação inválida.')
    }
    return { token: resposta.accessToken, expiraEm: inicio + resposta.expiresIn * 1000 }
  },
  listarCedentes: (token) => requisitar('/bff/v1/cedentes', (valor): valor is Cedente[] => Array.isArray(valor) && valor.every(cedente), {}, token),
  cadastrarCedente: (token, nome, documento) => requisitar('/bff/v1/cedentes', cedente, {
    method: 'POST', body: JSON.stringify({ nome, documento }),
  }, token),
  async simular(token, entrada: EntradaSimulacao, signal) {
    const resultado = await requisitar('/bff/v1/simulacoes', simulacao, {
      method: 'POST', body: JSON.stringify(entrada), signal,
    }, token)
    if (resultado.moeda !== entrada.moeda) throw new ErroOperacao('Resposta de simulação incompatível com a solicitação.')
    return resultado
  },
  cadastrarRecebivel: (token, entrada: EntradaRecebivel) => requisitar('/bff/v1/recebiveis',
    (valor): valor is Recebivel => objeto(valor) && typeof valor.id === 'string' && valor.id.length > 0, {
    method: 'POST', body: JSON.stringify(entrada),
  }, token),
  async liquidar(token, id, moeda: Moeda, chave) {
    const resultado = await requisitar(`/bff/v1/recebiveis/${encodeURIComponent(id)}/liquidacoes`, liquidacao, {
      method: 'POST', headers: { 'Idempotency-Key': chave }, body: JSON.stringify({ moeda }),
    }, token)
    if (resultado.recebivelId !== id || resultado.moeda !== moeda) {
      throw new ErroOperacao('Resposta de liquidação incompatível com a solicitação.')
    }
    return resultado
  },
}
