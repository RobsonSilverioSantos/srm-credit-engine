import type {
  CadastroLote, ComandoImportacao, ComandoLiquidacaoLote, ItemLiquidacaoLote, LinhaLote,
  PreviaCadastro, PreviaFinanceiraLote, ResultadoLiquidacaoLote,
} from '../dominio/lotes'
import { cadastroCompleto, previaCompleta } from '../dominio/lotes'
import { ErroOperacao } from './operacoes'

export interface PortaLotes {
  prevalidar(token: string, arquivo: File): Promise<PreviaCadastro>
  cadastrar(token: string, comando: { itens: ComandoImportacao[] }, chave: string): Promise<CadastroLote>
  previsualizar(token: string, loteId: string, itens: ItemLiquidacaoLote[]): Promise<PreviaFinanceiraLote>
  liquidar(token: string, loteId: string, comando: ComandoLiquidacaoLote, chave: string): Promise<ResultadoLiquidacaoLote>
}

export class ErroLote extends ErroOperacao {
  constructor(mensagem: string, status?: number, public readonly itens?: LinhaLote[]) {
    super(mensagem, status)
  }
}

type Intencao<T> = { chave: string; comando: T }
export type EstadoLote = {
  ocupado: boolean
  previaCadastro: PreviaCadastro | null
  cadastro: CadastroLote | null
  previaFinanceira: PreviaFinanceiraLote | null
  resultado: ResultadoLiquidacaoLote | null
  cadastroPendente: Intencao<{ itens: ComandoImportacao[] }> | null
  liquidacaoPendente: Intencao<ComandoLiquidacaoLote> | null
  itensLiquidacao: ItemLiquidacaoLote[]
}

const inicial = (): EstadoLote => ({
  ocupado: false, previaCadastro: null, cadastro: null, previaFinanceira: null, resultado: null,
  cadastroPendente: null, liquidacaoPendente: null, itensLiquidacao: [],
})

function rejeicaoDefinitiva(erro: unknown): boolean {
  return erro instanceof ErroOperacao && [400, 403, 404, 409, 413, 422].includes(erro.status ?? 0)
}

export class FluxoLote {
  private estado = inicial()
  private ouvintes = new Set<() => void>()
  constructor(private readonly porta: PortaLotes, private readonly chave: () => string = () => crypto.randomUUID()) {}
  snapshot = () => this.estado
  assinar = (ouvinte: () => void) => {
    this.ouvintes.add(ouvinte)
    return () => { this.ouvintes.delete(ouvinte) }
  }
  private atualizar(mudancas: Partial<EstadoLote>) {
    this.estado = { ...this.estado, ...mudancas }
    this.ouvintes.forEach((ouvinte) => ouvinte())
  }
  private async executar(acao: () => Promise<void>) {
    if (this.estado.ocupado) return
    this.atualizar({ ocupado: true })
    try { await acao() } finally { this.atualizar({ ocupado: false }) }
  }
  novo() {
    if (this.estado.ocupado || this.estado.cadastroPendente || this.estado.liquidacaoPendente) {
      throw new ErroLote('Resolva a confirmação pendente repetindo a mesma operação antes de iniciar outro lote.')
    }
    this.atualizar(inicial())
  }
  async prevalidar(token: string, arquivo: File) {
    if (this.estado.cadastro || this.estado.cadastroPendente) throw new ErroLote('Inicie um novo lote somente após resolver o cadastro atual.')
    return this.executar(async () => {
      this.atualizar({ previaCadastro: null })
      if (!/\.(csv|xlsx)$/i.test(arquivo.name) || arquivo.size === 0 || arquivo.size > 2097152) {
        throw new ErroLote('Selecione CSV ou XLSX não vazio, de até 2 MiB. O servidor valida o conteúdo e o limite de linhas.')
      }
      this.atualizar({ previaCadastro: await this.porta.prevalidar(token, arquivo) })
    })
  }
  async cadastrar(token: string) {
    return this.executar(async () => {
      const { previaCadastro, cadastro } = this.estado
      if (cadastro || !cadastroCompleto(previaCadastro)) throw new ErroLote('Confira todas as linhas válidas antes de confirmar o cadastro.')
      const intencao = this.estado.cadastroPendente ?? {
        chave: this.chave(),
        comando: { itens: previaCadastro!.itens.map((item) => ({ ...item.comando! })) },
      }
      this.atualizar({ cadastroPendente: intencao })
      try {
        const resultado = await this.porta.cadastrar(token, intencao.comando, intencao.chave)
        const referencias = new Set(resultado.itens.map((item) => item.referenciaExterna))
        if (resultado.cadastrados !== intencao.comando.itens.length || resultado.itens.length !== resultado.cadastrados
          || referencias.size !== resultado.cadastrados
          || new Set(resultado.itens.map((item) => item.recebivelId)).size !== resultado.cadastrados
          || !intencao.comando.itens.every((item) => referencias.has(item.referenciaExterna))) {
          throw new ErroLote('Resposta de cadastro incompleta. Resultado incerto: repita com a mesma chave.')
        }
        const itensLiquidacao = resultado.itens.map((item) => ({
          recebivelId: item.recebivelId,
          moedaPagamento: intencao.comando.itens.find((comando) => comando.referenciaExterna === item.referenciaExterna)!.moedaPagamento,
        }))
        this.atualizar({ cadastro: resultado, cadastroPendente: null, itensLiquidacao })
      } catch (erro) {
        if (rejeicaoDefinitiva(erro)) this.atualizar({
          cadastroPendente: null,
          previaCadastro: {
            confirmavel: false,
            itens: previaCadastro!.itens.map((original) => {
              const atualizado = erro instanceof ErroLote ? erro.itens?.find((item) => item.linha === original.linha) : undefined
              return atualizado ? { ...atualizado, comando: atualizado.comando ?? original.comando } : original
            }),
          },
        })
        throw erro
      }
    })
  }
  async previsualizar(token: string) {
    const { cadastro, liquidacaoPendente, resultado } = this.estado
    if (!cadastro || liquidacaoPendente || resultado) throw new ErroLote('Resolva a operação pendente antes de solicitar nova prévia.')
    return this.executar(async () => {
      this.atualizar({ previaFinanceira: null })
      const previa = await this.porta.previsualizar(token, cadastro.loteId, this.estado.itensLiquidacao)
      if (previa.loteId !== cadastro.loteId) throw new ErroLote('Prévia financeira de outro lote rejeitada.')
      this.atualizar({ previaFinanceira: previa })
    })
  }
  async liquidar(token: string) {
    return this.executar(async () => {
      const { cadastro, previaFinanceira, itensLiquidacao, resultado } = this.estado
      if (!cadastro || resultado || (!this.estado.liquidacaoPendente && !previaCompleta(previaFinanceira, itensLiquidacao))) {
        throw new ErroLote('A liquidação exige prévia válida de 100% dos títulos.')
      }
      const intencao = this.estado.liquidacaoPendente ?? {
        chave: this.chave(),
        comando: { itens: itensLiquidacao.map((item) => ({ ...item })), hashPrevia: previaFinanceira!.hashPrevia!, confirmacaoFinanceira: true as const },
      }
      this.atualizar({ liquidacaoPendente: intencao })
      try {
        const liquidado = await this.porta.liquidar(token, cadastro.loteId, intencao.comando, intencao.chave)
        if (liquidado.loteId !== cadastro.loteId || liquidado.itens.length !== itensLiquidacao.length
          || new Set(liquidado.itens.map((item) => item.recebivelId)).size !== itensLiquidacao.length
          || new Set(liquidado.itens.map((item) => item.liquidacaoId)).size !== itensLiquidacao.length
          || !liquidado.itens.every((item) => itensLiquidacao.some((esperado) =>
            esperado.recebivelId === item.recebivelId && esperado.moedaPagamento === item.moedaPagamento))) {
          throw new ErroLote('Resposta financeira incompleta. Resultado incerto: repita com a mesma chave, sem assumir sucesso parcial.')
        }
        this.atualizar({ resultado: liquidado, liquidacaoPendente: null })
      } catch (erro) {
        if (rejeicaoDefinitiva(erro)) this.atualizar({ liquidacaoPendente: null, previaFinanceira: null })
        throw erro
      }
    })
  }
}
