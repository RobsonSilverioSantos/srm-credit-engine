import { useState, type MouseEvent, type ReactNode } from 'react'
import {
  AppBar, Box, Breadcrumbs, Button, Container, Drawer, Link, List, ListItemButton,
  Stack, Toolbar, Typography, useMediaQuery,
} from '@mui/material'
import { useTheme } from '@mui/material/styles'
import { hashDaPagina, type PaginaOperacional } from './navegacaoOperacional'

type Props = {
  children: ReactNode
  pagina: PaginaOperacional
  usuariosVisiveis: boolean
  sairDesabilitado: boolean
  aoNavegar: (pagina: PaginaOperacional) => void
  aoSair: () => void
}

const paginas: readonly { id: PaginaOperacional; rotulo: string; grupo: string }[] = [
  { id: 'cedentes', rotulo: 'Cedentes', grupo: 'Operações' },
  { id: 'simulacao', rotulo: 'Simulação', grupo: 'Operações' },
  { id: 'liquidacao', rotulo: 'Liquidação', grupo: 'Operações' },
  { id: 'extrato', rotulo: 'Extrato', grupo: 'Relatórios' },
  { id: 'usuarios', rotulo: 'Usuários', grupo: 'Administração' },
]

export function LayoutOperacional({ children, pagina, usuariosVisiveis, sairDesabilitado, aoNavegar, aoSair }: Props) {
  const tema = useTheme()
  const desktop = useMediaQuery(tema.breakpoints.up('md'))
  const [menuAberto, setMenuAberto] = useState(false)
  const atual = paginas.find(({ id }) => id === pagina) ?? paginas[0]!
  const itens = paginas.filter(({ id }) => id !== 'usuarios' || usuariosVisiveis)

  function selecionar(evento: MouseEvent, destino: PaginaOperacional) {
    if (evento.ctrlKey || evento.metaKey || evento.shiftKey || evento.altKey) return
    evento.preventDefault()
    aoNavegar(destino)
    setMenuAberto(false)
  }

  const navegacao = (
    <Box component="nav" id="menu-operacional" aria-label="Navegação operacional" sx={{ p: 2 }}>
      <List disablePadding>
        {itens.map(({ id, rotulo }) => (
          <ListItemButton
            component="a"
            href={hashDaPagina(id)}
            key={id}
            selected={pagina === id}
            aria-current={pagina === id ? 'page' : undefined}
            onClick={(evento) => selecionar(evento, id)}
            sx={{ borderRadius: 1, minHeight: 44 }}
          >
            {rotulo}
          </ListItemButton>
        ))}
      </List>
    </Box>
  )

  return (
    <>
      <Link href="#conteudo" sx={{
        position: 'fixed', top: 8, left: 8, zIndex: (tema) => tema.zIndex.tooltip,
        bgcolor: 'background.paper', p: 1, transform: 'translateY(-200%)',
        '&:focus': { transform: 'none' },
      }} onClick={(evento) => {
        evento.preventDefault()
        document.getElementById('conteudo')?.focus()
      }}>Pular para o conteúdo</Link>
      <AppBar position="static" elevation={0}>
        <Toolbar sx={{ gap: 1.5, flexWrap: 'wrap', py: 1 }}>
          {!desktop && (
            <Button
              color="inherit"
              onClick={() => setMenuAberto(true)}
              aria-expanded={menuAberto}
              aria-controls={menuAberto ? 'menu-operacional' : undefined}
              aria-label="Abrir menu de navegação"
            >Menu</Button>
          )}
          <Typography fontWeight={700} sx={{ flexGrow: 1 }}>SRM Credit Engine</Typography>
          <Button
            color="inherit"
            onClick={aoSair}
            disabled={sairDesabilitado}
            aria-label={sairDesabilitado ? 'Aguarde a conclusão da operação para sair' : undefined}
          >Sair</Button>
        </Toolbar>
      </AppBar>
      <Box sx={{ display: 'flex', minHeight: 'calc(100dvh - 64px)' }}>
        {desktop ? (
          <Box component="aside" sx={{
            width: 224, flexShrink: 0, borderRight: 1, borderColor: 'divider', bgcolor: 'background.paper',
            display: 'flex', flexDirection: 'column', justifyContent: 'space-between',
          }}>
            {navegacao}
            <Box sx={{ p: 2 }}>
              <Link href="/catalogo.html">Catálogo visual (demonstração)</Link>
            </Box>
          </Box>
        ) : (
          <Drawer
            open={menuAberto}
            onClose={() => setMenuAberto(false)}
            slotProps={{ paper: { sx: { width: 'min(17rem, 90vw)' } } }}
          >
            <Stack sx={{ height: '100%', justifyContent: 'space-between' }}>
              {navegacao}
              <Box sx={{ p: 2 }}>
                <Link href="/catalogo.html">Catálogo visual (demonstração)</Link>
              </Box>
            </Stack>
          </Drawer>
        )}
        <Box component="main" id="conteudo" tabIndex={-1} sx={{ flex: 1, minWidth: 0 }}>
          <Container maxWidth="lg">
            <Stack spacing={3} sx={{ py: { xs: 3, md: 5 } }}>
              <Box component="header">
                <Breadcrumbs aria-label="Caminho de navegação">
                  <Link href={hashDaPagina('cedentes')} onClick={(evento) => selecionar(evento, 'cedentes')}>Início</Link>
                  <Typography color="text.primary">{atual.grupo}</Typography>
                  <Typography color="text.primary" aria-current="page">{atual.rotulo}</Typography>
                </Breadcrumbs>
                <Typography component="h1" variant="h1" sx={{ mt: 2 }}>{atual.rotulo}</Typography>
              </Box>
              {children}
            </Stack>
          </Container>
        </Box>
      </Box>
    </>
  )
}
