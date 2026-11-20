import js from '@eslint/js'
import { defineConfig, globalIgnores } from 'eslint/config'
import hooks from 'eslint-plugin-react-hooks'
import tseslint from 'typescript-eslint'

const interfaceVisual = ['react', 'react/*', 'react-dom', 'react-dom/*', '@mui/*', '@emotion/*',
  '**/apresentacao/**', '**/tema/**', '**/apresentacao', '**/tema']
const adaptadores = ['**/infraestrutura/**', '**/infraestrutura']

export default defineConfig([
  globalIgnores(['dist', 'coverage']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [js.configs.recommended, tseslint.configs.recommended, hooks.configs.flat.recommended],
  },
  {
    files: ['src/infraestrutura/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-imports': ['error', { patterns: [
        { group: interfaceVisual, message: 'Adaptadores de infraestrutura não dependem da interface.' },
      ] }],
    },
  },
  {
    files: ['src/{dominio,aplicacao}/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-imports': ['error', { patterns: [
        { group: [...interfaceVisual, ...adaptadores], message: 'Domínio e aplicação não dependem de adaptadores ou da interface.' },
      ] }],
      'no-restricted-globals': ['error', 'fetch', 'window', 'document', 'localStorage'],
    },
  },
  {
    files: ['src/dominio/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-imports': ['error', { patterns: [...interfaceVisual, ...adaptadores, '**/aplicacao/**', '**/aplicacao'] }],
    },
  },
  {
    files: ['src/apresentacao/**/*.{ts,tsx}'],
    rules: {
      'no-restricted-imports': ['error', { patterns: adaptadores }],
      'no-restricted-globals': ['error', 'fetch'],
    },
  },
])
