import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { CssBaseline } from '@mui/material'
import { ThemeProvider } from '@mui/material/styles'
import { bff } from './infraestrutura/bff'
import { bffUsuarios } from './infraestrutura/bffUsuarios'
import { bffLotes } from './infraestrutura/bffLotes'
import { fusoHorarioNavegador, idiomaNavegador } from './infraestrutura/idiomaNavegador'
import { Aplicacao } from './apresentacao/Aplicacao'
import { bffExtrato } from './infraestrutura/bffExtrato'
import { tema } from './tema/tema'

const raiz = document.getElementById('root')
if (!raiz) throw new Error('Elemento raiz da aplicação não encontrado.')

createRoot(raiz).render(
  <StrictMode>
    <ThemeProvider theme={tema}>
      <CssBaseline />
      <Aplicacao operacoes={bff} gestaoUsuarios={bffUsuarios} extrato={bffExtrato} lotes={bffLotes} locale={idiomaNavegador()} fusoHorario={fusoHorarioNavegador()} />
    </ThemeProvider>
  </StrictMode>,
)
