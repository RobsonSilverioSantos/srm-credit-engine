import { useEffect, useState, type ReactNode } from 'react'
import { Button, Stack, TablePagination } from '@mui/material'
import type { PaginaCambio } from '../dominio/cambio'
import { TabelaDados } from '../tema/componentes/TabelaDados'
import type { ComponentProps } from 'react'

type Props<T extends { id: string }> = {
  titulo: string
  carregar: (pagina: number, tamanho: number, signal: AbortSignal) => Promise<PaginaCambio<T>>
  colunas: ComponentProps<typeof TabelaDados<T>>['colunas']
  tratarErro: (falha: unknown) => string
  complemento?: ReactNode
}

export function HistoricoCambio<T extends { id: string }>({ titulo, carregar, colunas, tratarErro, complemento }: Props<T>) {
  const [consulta, setConsulta] = useState({ pagina: 0, tamanho: 20 })
  const [dados, setDados] = useState<PaginaCambio<T> | null>(null)
  const [erro, setErro] = useState('')
  const [tentativa, setTentativa] = useState(0)
  const [chaveCarregada, setChaveCarregada] = useState<object | null>(null)
  const carregando = chaveCarregada !== consulta

  useEffect(() => {
    const controlador = new AbortController()
    let ativa = true
    void carregar(consulta.pagina, consulta.tamanho, controlador.signal).then((pagina) => {
      if (!ativa) return
      setDados(pagina)
      setErro('')
      setChaveCarregada(consulta)
    }).catch((falha: unknown) => {
      if (!ativa) return
      setDados(null)
      setErro(tratarErro(falha))
      setChaveCarregada(consulta)
    })
    return () => { ativa = false; controlador.abort() }
  }, [carregar, consulta, tentativa, tratarErro])

  return <Stack spacing={2}>
    {complemento}
    <TabelaDados legenda={titulo} colunas={colunas} chaveLinha={(item) => item.id}
      estado={carregando ? { tipo: 'carregando' } : erro ? {
        tipo: 'erro', mensagem: erro, acao: <Button onClick={() => {
          setChaveCarregada(null)
          setTentativa((valor) => valor + 1)
        }}>Tentar novamente</Button>,
      } : { tipo: 'pronto', linhas: dados?.itens ?? [] }} />
    <TablePagination component="div" count={dados?.total ?? 0} page={consulta.pagina} rowsPerPage={consulta.tamanho}
      rowsPerPageOptions={[10, 20, 50, 100]} labelRowsPerPage="Registros por página"
      labelDisplayedRows={({ from, to, count }) => `${from}–${to} de ${count}`}
      getItemAriaLabel={(tipo) => tipo === 'next' ? `Próxima página: ${titulo}` : `Página anterior: ${titulo}`}
      disabled={carregando}
      onPageChange={(_, pagina) => setConsulta((atual) => ({ ...atual, pagina }))}
      onRowsPerPageChange={(evento) => setConsulta({ pagina: 0, tamanho: parseInt(evento.target.value, 10) })} />
  </Stack>
}
