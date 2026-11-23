export function formatarInstante(isoUtc: string, locale: string, fusoHorario: string): string {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z$/.test(isoUtc)) {
    throw new Error('Instante ISO 8601 UTC inválido.')
  }
  const instante = new Date(isoUtc)
  if (!Number.isFinite(instante.getTime()) || instante.toISOString().slice(0, 19) !== isoUtc.slice(0, 19)) {
    throw new Error('Instante ISO 8601 UTC inválido.')
  }

  const partes = new Intl.DateTimeFormat(locale, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
    timeZone: fusoHorario,
  }).formatToParts(instante)
  return partes.map((parte) => parte.type === 'literal' ? parte.value.replace(',', '') : parte.value).join('')
}
