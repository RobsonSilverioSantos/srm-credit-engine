export type PaginaOperacional = 'cedentes' | 'simulacao' | 'liquidacao' | 'importacao' | 'extrato' | 'usuarios' | 'cambio'

export function paginaDaHash(hash: string): PaginaOperacional {
  const pagina = hash.replace(/^#\/?/, '').split(/[/?]/)[0]
  if (pagina === 'simulacao' || pagina === 'liquidacao' || pagina === 'importacao' || pagina === 'extrato' || pagina === 'usuarios' || pagina === 'cambio') return pagina
  return 'cedentes'
}

export function hashDaPagina(pagina: PaginaOperacional): string {
  return `#/${pagina}`
}
