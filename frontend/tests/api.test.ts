import { afterEach, test } from 'node:test'
import assert from 'node:assert/strict'
import { mock } from 'node:test'
import {
  ApiError, getCountries, getCountryCatalog, getHistory, isTransientError,
} from '../src/services/api.ts'
import type { Country, History, Observation, Page } from '../src/services/api.ts'

afterEach(() => mock.restoreAll())

function page<T>(content: T[], index = 0, totalElements = content.length): Page<T> {
  return { content, page: index, size: 100, totalElements, totalPages: Math.ceil(totalElements / 100) }
}

function country(index: number): Country {
  return {
    id: `country-${index}`, iso2Code: 'BR', iso3Code: index === 0 ? 'BRA' : String(index).padStart(3, '0'),
    name: `Country ${index}`, originalName: `Country ${index}`, region: null,
    incomeLevel: null, capital: null, latitude: null, longitude: null,
  }
}

function observation(period: number, value: number | null): Observation {
  return {
    countryCode: 'BRA', period, value, status: value === null ? 'MISSING' : 'AVAILABLE',
    providerStatus: null, sourceUpdatedAt: '2026-01-01', importedAt: '2026-01-02T00:00:00Z',
  }
}

function history(observations: Page<Observation>, from = 2000, to = 2002): History {
  return {
    indicator: { id: 'indicator', code: 'SP.POP.TOTL', name: 'Population', category: 'DEMOGRAPHY', description: null },
    series: {
      id: 'series-id', externalCode: 'SP.POP.TOTL', datasetCode: '2', originalName: 'Population',
      methodology: null, sourceOrganization: null, unit: 'PERSONS', frequency: 'ANNUAL',
      source: { id: 'source', code: 'WORLD_BANK', name: 'World Bank', baseUrl: 'https://api.worldbank.org', documentationUrl: null, description: null },
    },
    countries: [{ iso3Code: 'BRA', name: 'Brasil' }], from, to, observations,
  }
}

function respond(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })
}

test('preserves numeric zero, explicit missing values and observation metadata', async () => {
  const expected = history(page([observation(2000, 0), observation(2001, null), observation(2002, 42.125)]))
  mock.method(globalThis, 'fetch', async () => respond(expected))
  const result = await getHistory('BRA', 'SP.POP.TOTL', 'series-id', 2000, 2002)
  assert.deepEqual(result.observations.content, expected.observations.content)
  assert.equal(result.series.unit, 'PERSONS')
  assert.equal(result.observations.content[0].value, 0)
  assert.equal(result.observations.content[1].status, 'MISSING')
})

test('retrieves every history page with the same series and interval', async () => {
  const observations = Array.from({ length: 101 }, (_, index) => observation(1800 + index, index))
  const requests: URL[] = []
  mock.method(globalThis, 'fetch', async (input: string) => {
    const url = new URL(input, 'http://atlas.local')
    requests.push(url)
    const index = Number(url.searchParams.get('page'))
    return respond(history(page(observations.slice(index * 100, (index + 1) * 100), index, observations.length), 1800, 1900))
  })
  const result = await getHistory('BRA', 'SP.POP.TOTL', 'series-id', 1800, 1900)
  assert.deepEqual(result.observations.content, observations)
  assert.deepEqual(requests.map(url => url.searchParams.get('page')), ['0', '1'])
  for (const url of requests) {
    assert.equal(url.searchParams.get('seriesId'), 'series-id')
    assert.equal(url.searchParams.get('from'), '1800')
    assert.equal(url.searchParams.get('to'), '1900')
    assert.equal(url.searchParams.get('sort'), 'period')
  }
})

test('rejects a failed subsequent page instead of returning partial history', async () => {
  mock.method(globalThis, 'fetch', async (input: string) => {
    const index = Number(new URL(input, 'http://atlas.local').searchParams.get('page'))
    return index === 0
      ? respond(history(page(Array.from({ length: 100 }, (_, n) => observation(1800 + n, n)), 0, 101), 1800, 1900))
      : respond({ detail: 'internal detail' }, 503)
  })
  await assert.rejects(getHistory('BRA', 'SP.POP.TOTL', 'series-id', 1800, 1900), error => error instanceof ApiError && error.status === 503)
})

test('rejects changed collection totals and duplicated history periods', async () => {
  mock.method(globalThis, 'fetch', async (input: string) => {
    const index = Number(new URL(input, 'http://atlas.local').searchParams.get('page'))
    return respond(page(index === 0 ? Array.from({ length: 100 }, (_, n) => country(n)) : [country(100)], index, index === 0 ? 101 : 102))
  })
  await assert.rejects(getCountryCatalog(), error => error instanceof ApiError && error.status === 502)
  mock.restoreAll()
  mock.method(globalThis, 'fetch', async () => respond(history(page([observation(2000, 1), observation(2000, 2)]))))
  await assert.rejects(getHistory('BRA', 'SP.POP.TOTL', 'series-id', 2000, 2002), error => error instanceof ApiError && error.status === 502)
})

test('retrieves the complete country catalog and preserves optional metadata', async () => {
  const countries = Array.from({ length: 101 }, (_, n) => country(n))
  mock.method(globalThis, 'fetch', async (input: string) => {
    const index = Number(new URL(input, 'http://atlas.local').searchParams.get('page'))
    return respond(page(countries.slice(index * 100, (index + 1) * 100), index, 101))
  })
  assert.deepEqual(await getCountryCatalog(), countries)
})

test('encodes literal searches and filters without changing wildcard characters', async () => {
  let request: URL | undefined
  mock.method(globalThis, 'fetch', async (input: string, options: RequestInit) => {
    request = new URL(input, 'http://atlas.local')
    assert.equal(options.credentials, 'omit')
    return respond(page([]))
  })
  await getCountries({ q: '  %_! a&b  ', region: 'Europe & Central Asia', incomeLevel: 'High income', page: 3, size: 12 })
  assert.equal(request?.searchParams.get('q'), '%_! a&b')
  assert.equal(request?.searchParams.get('region'), 'Europe & Central Asia')
  assert.equal(request?.searchParams.get('page'), '3')
  assert.equal(request?.searchParams.get('size'), '12')
})

test('provides safe public errors and retries only transient failures', async () => {
  mock.method(globalThis, 'fetch', async () => respond({ detail: 'database credentials and stack trace' }, 400))
  await assert.rejects(getCountries(), error => {
    assert.ok(error instanceof ApiError)
    assert.equal(error.status, 400)
    assert.doesNotMatch(error.message, /credentials|stack trace/)
    assert.equal(isTransientError(error), false)
    return true
  })
  for (const status of [0, 408, 429, 500, 502, 503]) assert.equal(isTransientError(new ApiError(status)), true)
  for (const status of [400, 404, 409]) assert.equal(isTransientError(new ApiError(status)), false)
  assert.equal(isTransientError(new DOMException('Cancelled', 'AbortError')), false)
})

test('preserves caller cancellation for obsolete requests', async () => {
  const controller = new AbortController()
  mock.method(globalThis, 'fetch', (_input: string, options: RequestInit) => new Promise((_resolve, reject) => {
    options.signal?.addEventListener('abort', () => reject(options.signal?.reason), { once: true })
  }))
  const pending = getCountries({}, controller.signal)
  controller.abort(new DOMException('Cancelled', 'AbortError'))
  await assert.rejects(pending, error => error instanceof DOMException && error.name === 'AbortError')
})

test('rejects invalid JSON as an unavailable service without leaking response bodies', async () => {
  mock.method(globalThis, 'fetch', async () => new Response('<html>private server exception</html>'))
  await assert.rejects(getCountries(), error => error instanceof ApiError && error.status === 502 && !error.message.includes('private'))
})
