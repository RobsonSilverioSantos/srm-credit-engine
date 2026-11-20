import { createTheme } from '@mui/material/styles'

const tokens = {
  marinho: '#17365D',
  marinhoEscuro: '#102640',
  petroleo: '#00675D',
  texto: '#182639',
  textoSecundario: '#4B5B70',
  fundo: '#F3F6FA',
  superficie: '#FFFFFF',
  divisoria: '#D5DEE8',
  bordaCampo: '#78899D',
  foco: '#1560AB',
  erro: '#A61B29',
  aviso: '#8A4B08',
  sucesso: '#146344',
}

export const tema = createTheme({
  palette: {
    primary: { main: tokens.marinho, dark: tokens.marinhoEscuro, contrastText: tokens.superficie },
    secondary: { main: tokens.petroleo, contrastText: tokens.superficie },
    error: { main: tokens.erro },
    warning: { main: tokens.aviso },
    success: { main: tokens.sucesso },
    info: { main: tokens.foco },
    background: { default: tokens.fundo, paper: tokens.superficie },
    text: { primary: tokens.texto, secondary: tokens.textoSecundario },
    divider: tokens.divisoria,
  },
  typography: {
    fontFamily: 'system-ui, -apple-system, "Segoe UI", sans-serif',
    h1: { fontSize: 'clamp(2rem, 1.65rem + 1.5vw, 3rem)', fontWeight: 700, lineHeight: 1.15 },
    h2: { fontSize: '1.25rem', fontWeight: 700 },
    button: { textTransform: 'none', fontWeight: 650 },
  },
  spacing: 8,
  shape: { borderRadius: 8 },
  breakpoints: { values: { xs: 0, sm: 600, md: 900, lg: 1200, xl: 1536 } },
  components: {
    MuiCssBaseline: {
      styleOverrides: {
        ':focus-visible': { outline: `2px solid ${tokens.foco}`, outlineOffset: 3 },
        '@media (prefers-reduced-motion: reduce)': {
          '*, *::before, *::after': { animationDuration: '0.01ms !important', transitionDuration: '0.01ms !important' },
        },
      },
    },
    MuiButtonBase: {
      defaultProps: { disableRipple: true },
      styleOverrides: { root: { '&.Mui-focusVisible': { outline: `2px solid ${tokens.foco}`, outlineOffset: 3 } } },
    },
    MuiButton: { defaultProps: { disableElevation: true }, styleOverrides: { root: { minHeight: 44 } } },
    MuiTextField: { defaultProps: { fullWidth: true, variant: 'outlined' } },
    MuiOutlinedInput: { styleOverrides: { notchedOutline: { borderColor: tokens.bordaCampo } } },
    MuiCard: { defaultProps: { variant: 'outlined' } },
    MuiTableHead: { styleOverrides: { root: { backgroundColor: tokens.fundo } } },
    MuiTableCell: {
      styleOverrides: {
        head: { fontWeight: 700 },
        alignRight: { fontVariantNumeric: 'tabular-nums' },
      },
    },
  },
})
