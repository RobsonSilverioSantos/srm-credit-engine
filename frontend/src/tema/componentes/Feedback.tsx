import { Alert, CircularProgress, Stack, Typography, type AlertProps } from '@mui/material'

export function Mensagem({ severity = 'info', ...props }: Pick<AlertProps, 'severity' | 'children' | 'action'>) {
  return <Alert {...props} severity={severity} role={severity === 'error' ? 'alert' : 'status'} />
}

export function EstadoVazio({ titulo, descricao }: { titulo: string; descricao: string }) {
  return (
    <Stack role="status" spacing={0.5} sx={{ p: 2, border: 1, borderColor: 'divider', borderRadius: 1 }}>
      <Typography fontWeight={700}>{titulo}</Typography>
      <Typography color="text.secondary">{descricao}</Typography>
    </Stack>
  )
}

export function Carregando({ rotulo = 'Carregando…' }: { rotulo?: string }) {
  return (
    <Stack direction="row" spacing={1.5} alignItems="center" role="status" sx={{ py: 1 }}>
      <CircularProgress aria-hidden="true" size={20} />
      <Typography>{rotulo}</Typography>
    </Stack>
  )
}
