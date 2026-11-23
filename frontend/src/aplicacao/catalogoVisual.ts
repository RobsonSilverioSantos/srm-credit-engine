import type { RecebivelDemonstrativo } from '../dominio/recebivelDemonstrativo'

export interface FonteCatalogoVisual {
  listarRecebiveisFicticios(): readonly RecebivelDemonstrativo[]
}

export function consultarCatalogoVisual(fonte: FonteCatalogoVisual) {
  return { recebiveis: fonte.listarRecebiveisFicticios() }
}

export type CatalogoVisual = ReturnType<typeof consultarCatalogoVisual>
