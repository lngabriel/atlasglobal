import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import type { Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { apiOrigin, apiTimeout, pagesHeaders } from './config/deployment'

export default defineConfig(({ mode }) => {
  const environment = loadEnv(mode, fileURLToPath(new URL('.', import.meta.url)), 'VITE_')
  const origin = apiOrigin(environment.VITE_API_BASE_URL)
  if (mode === 'pages' && !origin) throw new Error('Cloudflare Pages requires VITE_API_BASE_URL.')
  const pages: Plugin = {
    name: 'atlas-pages-headers',
    generateBundle() { this.emitFile({ type: 'asset', fileName: '_headers', source: pagesHeaders(origin) }) },
  }
  return {
    plugins: [react(), tailwindcss(), ...(mode === 'pages' ? [pages] : [])],
    define: {
      __ATLAS_API_ORIGIN__: JSON.stringify(origin),
      __ATLAS_API_TIMEOUT_MS__: apiTimeout(environment.VITE_API_TIMEOUT_MS, Boolean(origin)),
    },
    resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
    server: {
      host: '127.0.0.1',
      port: 5173,
      strictPort: true,
      proxy: { '/api': { target: 'http://127.0.0.1:8080', changeOrigin: true } },
    },
  }
})
