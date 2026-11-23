import type { FonteCatalogoVisual } from '../aplicacao/catalogoVisual'

export const catalogoEmMemoria: FonteCatalogoVisual = {
  listarRecebiveisFicticios: () => [
    { id: 'DEMO-001', cedente: 'Cedente Exemplo Alfa', modalidade: 'Duplicata', valorExibicao: 'R$ 18.500,00', situacao: 'Disponível' },
    { id: 'DEMO-002', cedente: 'Cedente Exemplo Beta', modalidade: 'Cheque', valorExibicao: 'R$ 7.250,00', situacao: 'Liquidado' },
    { id: 'DEMO-003', cedente: 'Cedente Exemplo Gama', modalidade: 'Duplicata', valorExibicao: 'R$ 12.800,00', situacao: 'Disponível' },
  ],
}
