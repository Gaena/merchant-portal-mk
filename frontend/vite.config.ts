import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'

function figmaAssetResolver() {
  return {
    name: 'figma-asset-resolver',
    resolveId(id: string) {
      if (id.startsWith('figma:asset/')) {
        const filename = id.replace('figma:asset/', '')
        return path.resolve(__dirname, 'src/assets', filename)
      }
    },
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [figmaAssetResolver(), react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  assetsInclude: ['**/*.svg', '**/*.csv'],
  server: {
    port: 3000,
    proxy: {
      '/api/v1/auth': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/api/v1/users': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/api/v1/companies': {
        target: 'http://localhost:8082',
        changeOrigin: true,
      },
      '/api/v1/terminals': {
        target: 'http://localhost:8082',
        changeOrigin: true,
      },
      '/api/v1/audit-logs': {
        target: 'http://localhost:8082',
        changeOrigin: true,
      },
      // Сводка главной (P3-7). Отдельный префикс, а не /api/v1/transactions/summary: рядом
      // живёт GET /api/v1/transactions/{id}, и «summary» уехало бы в разбор UUID.
      '/api/v1/dashboard': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/api/v1/payment-links': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/api/v1/transactions': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})


