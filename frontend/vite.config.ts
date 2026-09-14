import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'
import { readFileSync } from 'fs'

const { version } = JSON.parse(readFileSync(path.resolve(__dirname, 'package.json'), 'utf-8')) as { version: string }

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // Версия для подписи в сайдбаре — из package.json при сборке.
  define: {
    __APP_VERSION__: JSON.stringify(version),
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
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
      // Проверка учётных данных терминала пробным заказом. В pbl, а не в directory, куда уходит
      // весь /api/v1/terminals: к провайдеру умеет ходить только pbl.
      '/api/v1/acquiring': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      // Сервис выписки и слепок терминалов провайдера.
      '/api/v1/ecom': {
        target: 'http://localhost:8083',
        changeOrigin: true,
      },
    },
  },
})
