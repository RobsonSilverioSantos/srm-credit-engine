
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [react()],

  build: {
    rollupOptions: {
      input: {
        principal: 'index.html',
        extrato: 'extrato.html',
        catalogo: 'catalogo.html',
      },
    },
  },

  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/bff': 'http://localhost:8080',
    },
  },

  test: {
    environment: 'jsdom',
    setupFiles: ['./test/setup.ts'],
  },
})
