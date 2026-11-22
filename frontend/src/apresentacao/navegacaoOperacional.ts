export type PaginaOperacional = 'cedentes' | 'simulacao' | 'liquidacao' | 'extrato' | 'usuarios'

export function paginaDaHash(hash: string): PaginaOperacional {
  const pagina = hash.replace(/^#\/?/, '').split(/[/?]/)[0]
  if (pagina === 'simulacao' || pagina === 'liquidacao' || pagina === 'extrato' || pagina === 'usuarios') return pagina
  return 'cedentes'
}

export function hashDaPagina(pagina: PaginaOperacional): string {
  return `#/${pagina}`
}
