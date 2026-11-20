import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { CssBaseline } from '@mui/material'
import { ThemeProvider } from '@mui/material/styles'
import { consultarCatalogoVisual } from './aplicacao/catalogoVisual'
import { catalogoEmMemoria } from './infraestrutura/catalogoEmMemoria'
import { PaginaCatalogo } from './apresentacao/PaginaCatalogo'
import { tema } from './tema/tema'

const raiz = document.getElementById('root')
if (!raiz) throw new Error('Elemento raiz da aplicação não encontrado.')

createRoot(raiz).render(
  <StrictMode>
    <ThemeProvider theme={tema}>
      <CssBaseline />
      <PaginaCatalogo catalogo={consultarCatalogoVisual(catalogoEmMemoria)} />
    </ThemeProvider>
  </StrictMode>,
)
