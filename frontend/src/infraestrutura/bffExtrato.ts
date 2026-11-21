import { SessaoExpirada, type ConsultaExtrato, type PortaAutenticacao, type PortaExtrato } from '../aplicacao/extrato'
import type { ItemExtrato, PaginaExtrato } from '../dominio/extrato'

function objeto(valor: unknown): valor is Record<string, unknown> {
  return typeof valor === 'object' && valor !== null && !Array.isArray(valor)
}

function itemValido(valor: unknown): valor is ItemExtrato {
  if (!objeto(valor)) return false
  const textos = ['liquidacaoId', 'recebivelId', 'cedenteId', 'modalidade', 'valorFaceBrl', 'taxaBaseId',
    'taxaBaseMensal', 'taxaBaseVigenteEm', 'spreadId', 'spreadMensal', 'spreadVigenteEm',
    'valorPresenteBrl', 'desagioBrl', 'valorPagamento', 'liquidadaEm']
  const opcionais = ['cotacaoCambialId', 'taxaBrlPorUnidade', 'cotacaoVigenteEm', 'cotacaoValidaAte']
  return textos.every((campo) => typeof valor[campo] === 'string') &&
    opcionais.every((campo) => valor[campo] === null || typeof valor[campo] === 'string') &&
    Number.isSafeInteger(valor.prazoMeses) && Number.isSafeInteger(valor.versaoRecebivel) &&
    (valor.moeda === 'BRL' || valor.moeda === 'USD')
}

function paginaValida(valor: unknown, consulta: ConsultaExtrato): valor is PaginaExtrato {
  return objeto(valor) && Array.isArray(valor.itens) && valor.itens.every(itemValido) &&
    valor.pagina === consulta.pagina && valor.tamanho === consulta.tamanho &&
    Number.isSafeInteger(valor.total) && typeof valor.total === 'number' && valor.total >= 0 &&
    valor.itens.length <= consulta.tamanho && valor.itens.length <= valor.total
}

export const bffExtrato: PortaExtrato = {
  async consultar(consulta, token, signal) {
    const parametros = new URLSearchParams({ pagina: String(consulta.pagina), tamanho: String(consulta.tamanho) })
    for (const chave of ['inicio', 'fim', 'cedenteId', 'moeda'] as const) {
      if (consulta[chave]) parametros.set(chave, consulta[chave])
    }
    const resposta = await fetch(`/bff/v1/extrato?${parametros}`, {
      headers: { Authorization: `Bearer ${token}` }, signal,
    })
    if (resposta.status === 401) throw new SessaoExpirada('Sessão expirada. Entre novamente.')
    if (!resposta.ok) throw new Error(`Não foi possível consultar o extrato (HTTP ${resposta.status}).`)
    const dados: unknown = await resposta.json()
    if (!paginaValida(dados, consulta)) throw new Error('Resposta inválida do serviço de extrato.')
    return dados
  },
}

export const autenticacaoHttp: PortaAutenticacao = {
  async autenticar(email, senha, signal) {
    const resposta = await fetch('/api/v1/auth/token', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password: senha }), signal,
    })
    if (resposta.status === 401) throw new Error('Credenciais inválidas.')
    if (!resposta.ok) throw new Error(`Não foi possível autenticar (HTTP ${resposta.status}).`)
    const dados: unknown = await resposta.json()
    if (!objeto(dados) || typeof dados.accessToken !== 'string' || !dados.accessToken || dados.tokenType !== 'Bearer') {
      throw new Error('Resposta inválida do serviço de autenticação.')
    }
    return dados.accessToken
  },
}
