import assert from 'node:assert/strict'
import { test } from 'node:test'
import { ApiError } from '../src/services/api.ts'
import { csvParameters, getCsv } from '../src/services/exports.ts'

const selection = { countries: ['bra', 'USA'], indicator: 'sp.pop.totl',
  seriesId: '12345678-1234-1234-1234-123456789abc', from: 2000, to: 2025 }

test('CSV selection preserves an explicit series, countries and exact interval', () => {
  assert.deepEqual(Object.fromEntries(csvParameters(selection)), {
    countries: 'BRA,USA', indicator: 'SP.POP.TOTL', seriesId: selection.seriesId, from: '2000', to: '2025',
  })
  for (const invalid of [
    { ...selection, countries: [] }, { ...selection, countries: ['BRA', 'bra'] },
    { ...selection, countries: ['BRA', 'USA', 'CHN', 'JPN', 'FRA', 'CAN'] },
    { ...selection, from: 2026 }, { ...selection, seriesId: 'not-a-uuid' },
  ]) assert.throws(() => csvParameters(invalid), error => error instanceof ApiError && error.status === 400)
})

test('CSV downloads preserve bytes and use a public request with no credentials', async () => {
  const originalFetch = globalThis.fetch
  try {
    globalThis.fetch = async (url, options) => {
      assert.match(String(url), /exports\/indicators\?countries=BRA%2CUSA/)
      assert.equal(options?.credentials, 'omit')
      assert.ok(new Headers(options?.headers).get('Accept')?.includes('text/csv'))
      return new Response('\uFEFFcountry_code,value\r\nBRA,0\r\n', { headers: { 'Content-Type': 'text/csv;charset=UTF-8' } })
    }
    const blob = await getCsv(selection)
    assert.deepEqual([...new Uint8Array(await blob.arrayBuffer()).slice(0, 3)], [239, 187, 191])
    assert.ok((await blob.text()).includes('BRA,0'))
  } finally { globalThis.fetch = originalFetch }
})

test('CSV HTTP failures remain safe errors and HTML is rejected instead of being downloaded', async () => {
  const originalFetch = globalThis.fetch
  try {
    globalThis.fetch = async () => new Response('private error detail', { status: 409 })
    await assert.rejects(getCsv(selection), error => error instanceof ApiError && error.status === 409 && !error.message.includes('private'))
    globalThis.fetch = async () => new Response('<html>fallback</html>', { headers: { 'Content-Type': 'text/html' } })
    await assert.rejects(getCsv(selection), error => error instanceof ApiError && error.status === 502)
  } finally { globalThis.fetch = originalFetch }
})

test('an obsolete CSV request respects caller cancellation', async () => {
  const originalFetch = globalThis.fetch
  const controller = new AbortController()
  try {
    globalThis.fetch = async (_url, options) => {
      controller.abort(new DOMException('Selection changed', 'AbortError'))
      options?.signal?.throwIfAborted()
      throw new Error('Unreachable')
    }
    await assert.rejects(getCsv(selection, controller.signal), error => error === controller.signal.reason)
  } finally { globalThis.fetch = originalFetch }
})
