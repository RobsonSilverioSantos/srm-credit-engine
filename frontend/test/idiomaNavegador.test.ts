import { expect, test, vi } from 'vitest'
import { idiomaNavegador } from '../src/infraestrutura/idiomaNavegador'

test('usa o primeiro idioma do navegador com fallback pt-BR e permite injeção', () => {
  vi.stubGlobal('navigator', { languages: ['en-US', 'pt-BR'] })
  try {
    expect(idiomaNavegador()).toBe('en-US')
    expect(idiomaNavegador([])).toBe('pt-BR')
    expect(idiomaNavegador(['pt-BR', 'en-US'])).toBe('pt-BR')
  } finally {
    vi.unstubAllGlobals()
  }
})
