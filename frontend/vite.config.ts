import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'
import { readFileSync } from 'fs'

const { version } = JSON.parse(readFileSync(path.resolve(__dirname, 'package.json'), 'utf-8')) as { version: string }

export default defineConfig({
  plugins: [react()],
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
      // Статистика оплат по ссылкам (Р-91). Не /api/v1/transactions/summary: рядом живёт
      // GET /api/v1/transactions/{id}, и «summary» уехало бы в разбор UUID.
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
      // Кнопка «Тест» терминала — в pbl, а не в directory: к провайдеру ходит только pbl.
      '/api/v1/acquiring': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/api/v1/ecom': {
        target: 'http://localhost:8083',
        changeOrigin: true,
      },
    },
  },
})
