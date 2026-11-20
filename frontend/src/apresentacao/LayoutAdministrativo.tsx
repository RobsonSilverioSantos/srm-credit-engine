import { useState, type MouseEvent, type ReactNode } from 'react'
import {
  AppBar, Box, Button, Chip, Container, Drawer, Link, List, ListItemButton,
  Stack, Toolbar, Typography, useMediaQuery,
} from '@mui/material'
import { useTheme } from '@mui/material/styles'

type Props = {
  children: ReactNode
  secoes: readonly { id: string; rotulo: string }[]
}

export function LayoutAdministrativo({ children, secoes }: Props) {
  const tema = useTheme()
  const desktop = useMediaQuery(tema.breakpoints.up('md'))
  const [menuAberto, setMenuAberto] = useState(false)
  const [destino, setDestino] = useState<string>()

  function fecharMenu() {
    setDestino(undefined)
    setMenuAberto(false)
  }

  function selecionarSecao(evento: MouseEvent, id: string) {
    if (desktop || evento.ctrlKey || evento.metaKey || evento.shiftKey || evento.altKey) return
    evento.preventDefault()
    setDestino(id)
    setMenuAberto(false)
  }

  function focarDestino() {
    if (!destino) return
    window.location.hash = destino
    document.getElementById(destino)?.focus()
    setDestino(undefined)
  }

  const navegacao = (
    <Box component="nav" id="menu-catalogo" aria-label="Seções do catálogo" sx={{ p: 2 }}>
      <Typography variant="overline" color="text.secondary" sx={{ px: 2 }}>Design system</Typography>
      <List disablePadding>
        {secoes.map(({ id, rotulo }) => (
          <ListItemButton
            component="a" href={`#${id}`} key={id}
            onClick={(evento) => selecionarSecao(evento, id)}
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
      }}>Pular para o conteúdo</Link>
      <AppBar position="static" elevation={0}>
        <Toolbar sx={{ gap: 1.5, flexWrap: 'wrap', py: 1 }}>
          {!desktop && (
            <Button
              color="inherit"
              onClick={() => setMenuAberto(true)}
              aria-expanded={menuAberto}
              aria-controls={menuAberto ? 'menu-catalogo' : undefined}
              sx={{ '&.Mui-focusVisible': { outlineColor: 'currentColor' } }}
            >Menu</Button>
          )}
          <Typography fontWeight={700} sx={{ flexGrow: 1 }}>SRM Credit Engine</Typography>
          <Chip label="Acesso demonstrativo" size="small" sx={{ bgcolor: 'background.paper', color: 'primary.main' }} />
        </Toolbar>
      </AppBar>
      <Box sx={{ display: 'flex', minHeight: 'calc(100dvh - 64px)' }}>
        {desktop ? (
          <Box component="aside" sx={{ width: 224, flexShrink: 0, borderRight: 1, borderColor: 'divider', bgcolor: 'background.paper' }}>
            {navegacao}
          </Box>
        ) : (
          <Drawer
            open={menuAberto}
            onClose={fecharMenu}
            ModalProps={{ disableRestoreFocus: Boolean(destino) }}
            slotProps={{
              paper: { sx: { width: 'min(17rem, 90vw)' }, 'aria-label': 'Menu do catálogo' },
              transition: { onExited: focarDestino },
            }}
          >
            {navegacao}
          </Drawer>
        )}
        <Box component="main" id="conteudo" tabIndex={-1} sx={{ flex: 1, minWidth: 0 }}>
          <Container maxWidth="lg">
            <Stack spacing={3} sx={{ py: { xs: 3, md: 5 } }}>{children}</Stack>
          </Container>
        </Box>
      </Box>
    </>
  )
}
