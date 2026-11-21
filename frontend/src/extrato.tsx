import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { CssBaseline } from '@mui/material'
import { ThemeProvider } from '@mui/material/styles'
import { autenticacaoHttp, bffExtrato } from './infraestrutura/bffExtrato'
import { idiomaNavegador } from './infraestrutura/idiomaNavegador'
import { AcessoExtrato } from './apresentacao/PaginaExtrato'
import { tema } from './tema/tema'

const raiz = document.getElementById('root')
if (!raiz) throw new Error('Elemento raiz da aplicação não encontrado.')

createRoot(raiz).render(
  <StrictMode><ThemeProvider theme={tema}><CssBaseline />
    <AcessoExtrato autenticacao={autenticacaoHttp} porta={bffExtrato} locale={idiomaNavegador()} />
  </ThemeProvider></StrictMode>,
)
