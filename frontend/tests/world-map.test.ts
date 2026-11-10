import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFileSync } from 'node:fs'
import { countryForGeometry, parseWorldGeometry, type CountryGeometry } from '../src/lib/world-map.ts'
import { getWorldGeometry } from '../src/services/world-map.ts'
import { ApiError, type Country } from '../src/services/api.ts'

const geometry = JSON.parse(readFileSync(new URL('../public/geodata/countries-110m.geojson', import.meta.url), 'utf8'))
const country = (code: string) => ({ iso3Code: code, name: code }) as Country
const catalog = new Map(['BRA', 'FRA', 'NOR', 'COD', 'TLS', 'ROU', 'PSE', 'TWN', 'XKX'].map(code => [code, country(code)]))
const byName = (name: string): CountryGeometry => geometry.features.find((feature: CountryGeometry) => feature.properties.NAME === name)

test('the committed world layer is valid geometry and excludes stale statistical attributes', () => {
  const parsed = parseWorldGeometry(geometry)
  assert.equal(parsed.features.length, 177)
  for (const feature of parsed.features) {
    assert.deepEqual(Object.keys(feature.properties).sort(), ['ADM0_A3', 'ISO_A3', 'ISO_A3_EH', 'NAME', 'NAME_PT', 'WB_A3'].sort())
    assert.equal('POP_EST' in feature.properties, false)
    assert.equal('GDP_MD' in feature.properties, false)
  }
})

test('exact ISO identities take precedence over obsolete Natural Earth World Bank identifiers', () => {
  for (const [name, code] of [['Brazil', 'BRA'], ['France', 'FRA'], ['Norway', 'NOR'],
    ['Dem. Rep. Congo', 'COD'], ['Timor-Leste', 'TLS'], ['Romania', 'ROU'], ['Palestine', 'PSE'], ['Taiwan', 'TWN']]) {
    assert.equal(countryForGeometry(byName(name), catalog)?.iso3Code, code)
  }
  assert.equal(countryForGeometry(byName('Kosovo'), catalog)?.iso3Code, 'XKX')
})

test('unmatched geographic areas do not invent countries or use approximate name matches', () => {
  assert.equal(countryForGeometry(byName('Antarctica'), catalog), undefined)
  assert.equal(countryForGeometry(byName('N. Cyprus'), catalog), undefined)
  assert.equal(countryForGeometry(byName('Somaliland'), catalog), undefined)
  assert.equal(countryForGeometry(byName('Brazil'), new Map([['BRZ', { ...country('BRZ'), name: 'Brazil' }]])), undefined)
})

test('invalid collections, geometry, bounds and open rings are rejected', () => {
  for (const value of [null, {}, { type: 'FeatureCollection', features: [] }]) assert.throws(() => parseWorldGeometry(value))
  const invalid = structuredClone(geometry)
  invalid.features[0].geometry.type = 'Point'
  assert.throws(() => parseWorldGeometry(invalid), /geometry/)
  const invalidCoordinate = structuredClone(geometry)
  invalidCoordinate.features[0].geometry.coordinates[0][0][0] = [181, 0]
  assert.throws(() => parseWorldGeometry(invalidCoordinate), /coordinate/)
  const openRing = structuredClone(geometry)
  openRing.features[0].geometry.coordinates[0][0][0] = [179, -16]
  assert.throws(() => parseWorldGeometry(openRing), /ring/)
})

test('map fetch uses only the local geometry asset and preserves caller cancellation', async () => {
  const previous = globalThis.fetch
  try {
    globalThis.fetch = async (input, init) => {
      assert.equal(input, '/geodata/countries-110m.geojson')
      assert.equal(init?.credentials, 'omit')
      return new Response(JSON.stringify(geometry), { status: 200, headers: { 'Content-Type': 'application/geo+json' } })
    }
    assert.equal((await getWorldGeometry()).features.length, 177)
    const controller = new AbortController()
    const reason = new Error('Selection changed')
    globalThis.fetch = async () => { controller.abort(reason); throw reason }
    await assert.rejects(getWorldGeometry(controller.signal), error => error === reason)
  } finally { globalThis.fetch = previous }
})

test('map loading failures and malformed local geometry become safe recoverable errors', async () => {
  const previous = globalThis.fetch
  try {
    globalThis.fetch = async () => new Response('Unavailable', { status: 503 })
    await assert.rejects(getWorldGeometry(), error => error instanceof ApiError && error.status === 503)
    globalThis.fetch = async () => new Response('<html>fallback</html>', { status: 200 })
    await assert.rejects(getWorldGeometry(), error => error instanceof ApiError && error.status === 502)
    globalThis.fetch = async () => new Response(JSON.stringify({ type: 'FeatureCollection', features: [] }), { status: 200 })
    await assert.rejects(getWorldGeometry(), error => error instanceof ApiError && error.status === 502)
  } finally { globalThis.fetch = previous }
})
