import { Card, Stack, Typography } from '@mui/material'
import type { ReactNode } from 'react'

type Props = { id: string; titulo: string; descricao?: string; children: ReactNode }

export function Painel({ id, titulo, descricao, children }: Props) {
  return (
    <Card component="section" aria-labelledby={id} sx={{ minWidth: 0 }}>
      <Stack spacing={2.5} sx={{ p: { xs: 2, sm: 3 } }}>
        <div>
          <Typography id={id} tabIndex={-1} component="h2" variant="h2">{titulo}</Typography>
          {descricao && <Typography color="text.secondary" sx={{ mt: 0.75 }}>{descricao}</Typography>}
        </div>
        {children}
      </Stack>
    </Card>
  )
}
