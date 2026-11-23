export type RecebivelDemonstrativo = Readonly<{
  id: string
  cedente: string
  modalidade: 'Duplicata' | 'Cheque'
  valorExibicao: string
  situacao: 'Disponível' | 'Liquidado'
}>
