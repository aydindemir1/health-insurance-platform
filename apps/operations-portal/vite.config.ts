import react from '@vitejs/plugin-react'
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return undefined
          if (id.includes('react-hook-form') || id.includes('@hookform') || id.includes('zod')) return 'forms-vendor'
          if (id.includes('@tanstack/react-query')) return 'query-vendor'
          if (id.includes('keycloak-js')) return 'auth-vendor'
          if (id.includes('react')) return 'react-vendor'
          return 'vendor'
        },
      },
    },
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/shared/test/setup.ts',
    include: ['src/**/*.test.{ts,tsx}'],
    coverage: {
      provider: 'v8',
      reporter: ['text-summary', 'lcov'],
    },
  },
})
