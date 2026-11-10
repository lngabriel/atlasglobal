import assert from 'node:assert/strict'
import { test } from 'node:test'
import { apiOrigin, apiTimeout, pagesHeaders } from '../config/deployment.ts'

test('deployment accepts an exact public HTTPS API origin and preserves local defaults', () => {
  assert.equal(apiOrigin(undefined), '')
  assert.equal(apiOrigin(''), '')
  assert.equal(apiOrigin('https://atlas-api.onrender.com/'), 'https://atlas-api.onrender.com')
  assert.equal(apiOrigin('https://ATLAS-API.onrender.com'), 'https://atlas-api.onrender.com')
  assert.equal(apiTimeout(undefined, false), 15_000)
  assert.equal(apiTimeout(undefined, true), 90_000)
  assert.equal(apiTimeout('120000', true), 120_000)
})

test('deployment rejects mixed content, secrets, paths and header injection before producing a bundle', () => {
  for (const value of ['http://atlas-api.onrender.com', '//atlas-api.onrender.com', 'https:atlas-api.onrender.com', 'not-a-url',
    'https://user:password@atlas-api.onrender.com', 'https://atlas-api.onrender.com/api',
    'https://atlas-api.onrender.com?password=secret', 'https://atlas-api.onrender.com#fragment',
    'https://atlas-api.onrender.com\\other', 'https://atlas-api.onrender.com\r\nX-Header: injected']) {
    assert.throws(() => apiOrigin(value), /VITE_API_BASE_URL/)
  }
  for (const value of ['0', '14999', '120001', '90000ms', 'NaN', 'Infinity', '1e5']) {
    assert.throws(() => apiTimeout(value, true), /VITE_API_TIMEOUT_MS/)
  }
})

test('Pages headers restrict connections to the configured backend and keep immutable caching scoped to hashed assets', () => {
  const headers = pagesHeaders('https://atlas-api.onrender.com')
  assert.match(headers, /connect-src 'self' https:\/\/atlas-api\.onrender\.com;/)
  assert.match(headers, /frame-ancestors 'none'/)
  assert.match(headers, /X-Content-Type-Options: nosniff/)
  assert.match(headers, /\/assets\/\*\n  Cache-Control: public, max-age=31536000, immutable/)
  assert.match(headers, /\/geodata\/\*\.geojson\n  Content-Type: application\/geo\+json/)
  assert.doesNotMatch(headers.split('/assets/*')[0], /Cache-Control|https:\/\/\*/)
  assert.throws(() => pagesHeaders(''), /requires VITE_API_BASE_URL/)
})
