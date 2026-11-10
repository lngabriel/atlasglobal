import assert from 'node:assert/strict'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { test } from 'node:test'
import { build } from 'vite'

test('the compiled external deployment uses one origin and cold-start timeout for JSON and both CSV transports', async t => {
  const directory = await mkdtemp(join(tmpdir(), 'atlas-transport-'))
  t.after(() => rm(directory, { recursive: true, force: true }))
  await build({
    configFile: false, publicDir: false, logLevel: 'silent',
    define: { __ATLAS_API_ORIGIN__: JSON.stringify('https://atlas-api.onrender.com'), __ATLAS_API_TIMEOUT_MS__: 90_000 },
    build: {
      outDir: directory, minify: false,
      lib: {
        entry: {
          api: fileURLToPath(new URL('../src/services/api.ts', import.meta.url)),
          exports: fileURLToPath(new URL('../src/services/exports.ts', import.meta.url)),
        },
        formats: ['es'], fileName: (_format, name) => `${name}.mjs`,
      },
      rollupOptions: { output: { chunkFileNames: '[name]-[hash].mjs' } },
    },
  })
  const api = await import(pathToFileURL(join(directory, 'api.mjs')).href)
  const exports = await import(pathToFileURL(join(directory, 'exports.mjs')).href)
  const requests: string[] = []
  const timeouts: number[] = []
  const fetchMock = t.mock.method(globalThis, 'fetch', async (input: string, options: RequestInit) => {
    requests.push(input)
    assert.equal(options.credentials, 'omit')
    assert.ok(options.signal)
    return input.includes('/export')
      ? new Response('country_code,value\r\nBRA,0\r\n', { headers: { 'Content-Type': 'text/csv' } })
      : Response.json({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
  })
  const timeoutMock = t.mock.method(AbortSignal, 'timeout', (milliseconds: number) => {
    timeouts.push(milliseconds)
    return new AbortController().signal
  })
  t.after(() => { fetchMock.mock.restore(); timeoutMock.mock.restore() })
  await api.getCountries()
  await exports.getCsv({ countries: ['BRA'], indicator: 'SP.POP.TOTL',
    seriesId: '12345678-1234-1234-1234-123456789abc', from: 2000, to: 2025 })
  await exports.getRateCsv({ countries: ['BRA'], from: '2020-01-01', to: '2026-01-01' })
  assert.deepEqual(requests.map(value => new URL(value).origin), Array(3).fill('https://atlas-api.onrender.com'))
  assert.deepEqual(requests.map(value => new URL(value).pathname), [
    '/api/v1/countries', '/api/v1/exports/indicators', '/api/v1/policy-rates/export',
  ])
  assert.deepEqual(timeouts, [90_000, 90_000, 90_000])
})
