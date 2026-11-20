import { Table, TableBody, TableCell, TableContainer, TableHead, TableRow } from '@mui/material'
import type { ReactNode } from 'react'
import { Carregando, EstadoVazio, Mensagem } from './Feedback'

type Coluna<Linha> = {
  id: string
  rotulo: string
  alinhar?: 'left' | 'right'
  cabecalhoLinha?: boolean
  renderizar: (linha: Linha) => ReactNode
}

export type EstadoTabela<Linha> =
  | { tipo: 'carregando' }
  | { tipo: 'erro'; mensagem: string; acao?: ReactNode }
  | { tipo: 'pronto'; linhas: readonly Linha[] }

type Props<Linha> = {
  legenda: string
  colunas: readonly Coluna<Linha>[]
  chaveLinha: (linha: Linha) => string
  estado: EstadoTabela<Linha>
  vazio?: { titulo: string; descricao: string }
}

export function TabelaDados<Linha>({ legenda, colunas, chaveLinha, estado, vazio }: Props<Linha>) {
  if (estado.tipo === 'carregando') return <Carregando rotulo="Carregando registros…" />
  if (estado.tipo === 'erro') return <Mensagem severity="error" action={estado.acao}>{estado.mensagem}</Mensagem>
  if (estado.linhas.length === 0) {
    return <EstadoVazio {...(vazio ?? { titulo: 'Nenhum registro', descricao: 'Não há dados para exibir.' })} />
  }

  return (
    <TableContainer role="region" aria-label={legenda} tabIndex={0} sx={{ border: 1, borderColor: 'divider', borderRadius: 1 }}>
      <Table size="small" sx={{ minWidth: 600 }}>
        <caption>{legenda}</caption>
        <TableHead>
          <TableRow>
            {colunas.map(({ id, rotulo, alinhar }) => <TableCell key={id} scope="col" align={alinhar}>{rotulo}</TableCell>)}
          </TableRow>
        </TableHead>
        <TableBody>
          {estado.linhas.map((linha) => (
            <TableRow key={chaveLinha(linha)} hover>
              {colunas.map(({ id, alinhar, cabecalhoLinha, renderizar }) => (
                <TableCell key={id} align={alinhar} component={cabecalhoLinha ? 'th' : 'td'} scope={cabecalhoLinha ? 'row' : undefined}>
                  {renderizar(linha)}
                </TableCell>
              ))}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  )
}
