export function idiomaNavegador(preferencias?: readonly string[]): string {
  const idiomas = preferencias ?? (typeof navigator === 'undefined' ? [] : navigator.languages)
  return idiomas.find((idioma) => idioma.trim() !== '')?.trim() ?? 'pt-BR'
}

export function fusoHorarioNavegador(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'
}
