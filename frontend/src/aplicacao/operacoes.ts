import type { Cedente, EntradaCedente, EntradaRecebivel, EntradaSimulacao, IntencaoLiquidacao, Liquidacao, Moeda, Recebivel, Sessao, Simulacao } from '../dominio/operacoes'

export interface PortaOperacoes {
  autenticar(email: string, senha: string): Promise<Sessao>
  listarCedentes(token: string): Promise<Cedente[]>
  cadastrarCedente(token: string, entrada: EntradaCedente): Promise<Cedente>
  simular(token: string, entrada: EntradaSimulacao, sinal: AbortSignal): Promise<Simulacao>
  cadastrarRecebivel(token: string, entrada: EntradaRecebivel): Promise<Recebivel>
  liquidar(token: string, id: string, moeda: Moeda, chave: string): Promise<Liquidacao>
}

export class ErroOperacao extends Error {
  constructor(message: string, public readonly status?: number) {
    super(message)
    this.name = 'ErroOperacao'
  }
}

export function mensagemErro(erro: unknown): string {
  return erro instanceof ErroOperacao ? erro.message : 'Falha inesperada. Tente novamente.'
}

export async function cadastrarParaLiquidacao(
  porta: PortaOperacoes, token: string, entrada: EntradaRecebivel, moeda: Moeda, chave: string,
): Promise<IntencaoLiquidacao> {
  const recebivel = await porta.cadastrarRecebivel(token, entrada)
  return { id: recebivel.id, moeda, chave }
}

export function confirmarLiquidacao(porta: PortaOperacoes, token: string, intencao: IntencaoLiquidacao): Promise<Liquidacao> {
  return porta.liquidar(token, intencao.id, intencao.moeda, intencao.chave)
}
