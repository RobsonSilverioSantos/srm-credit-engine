import { Typography } from '@mui/material'
import { formatarInstante } from '../../dominio/formatacaoTemporal'

type Props = {
  isoUtc: string
  locale: string
  rotulo: string
  fusoHorario?: string
}

export function InstanteLocal({ isoUtc, locale, rotulo, fusoHorario }: Props) {
  const zona = fusoHorario ?? Intl.DateTimeFormat().resolvedOptions().timeZone ?? 'UTC'
  const valorLocal = formatarInstante(isoUtc, locale, zona)

  return (
    <Typography
      component="time"
      dateTime={isoUtc}
      title={`ISO 8601 UTC: ${isoUtc}`}
      aria-label={`${rotulo}: ${valorLocal}; fuso horário ${zona}; ISO 8601 UTC ${isoUtc}`}
    >
      {valorLocal} ({zona})
    </Typography>
  )
}
