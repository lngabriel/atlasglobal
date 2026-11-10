import assert from 'node:assert/strict'
import { afterEach, mock, test } from 'node:test'
import { ApiError } from '../src/services/api.ts'
import { getRanking } from '../src/services/rankings.ts'
import type { Ranking } from '../src/services/rankings.ts'

afterEach(() => mock.restoreAll())

function fixture(): Ranking {
  return {
    indicator: { id: 'population', code: 'SP.POP.TOTL', name: 'Population', category: 'DEMOGRAPHY', description: null },
    series: {
      id: 'population-series', externalCode: 'SP.POP.TOTL', datasetCode: '2', originalName: 'Population',
      methodology: 'Test annual definition', sourceOrganization: 'Test provider', unit: 'PERSONS', frequency: 'ANNUAL',
      source: { id: 'world-bank', code: 'WORLD_BANK', name: 'World Bank', baseUrl: 'https://api.worldbank.org/v2/',
        description: 'World Development Indicators', documentationUrl: null },
      comparison: { allowed: true, note: 'One source-specific series.' },
    },
    year: 2024, availableYears: [2024, 2023],
    coverage: { totalCountries: 218, withData: 3, withoutData: 215 },
    ranking: {
      content: [
        { rank: 1, country: { iso3Code: 'USA', name: 'Estados Unidos' }, value: 10 },
        { rank: 2, country: { iso3Code: 'BRA', name: 'Brasil' }, value: 0 },
        { rank: 3, country: { iso3Code: 'ARG', name: 'Argentina' }, value: -2 },
      ],
      page: 0, size: 20, totalElements: 3, totalPages: 1,
    },
  }
}

function respond(value: unknown) {
  return new Response(JSON.stringify(value), { headers: { 'Content-Type': 'application/json' } })
}

test('ranking requests one explicit series and descending order without substituting a reference year', async () => {
  let requested: URL | undefined
  mock.method(globalThis, 'fetch', async (input: string, options: RequestInit) => {
    requested = new URL(input, 'http://atlas.test')
    assert.equal(options.credentials, 'omit')
    assert.equal(new Headers(options.headers).get('Accept'), 'application/json')
    return respond(fixture())
  })
  const response = await getRanking(' sp.pop.totl ', 'population-series')
  assert.equal(requested?.pathname, '/api/v1/indicators/SP.POP.TOTL/ranking')
  assert.equal(requested?.searchParams.get('seriesId'), 'population-series')
  assert.equal(requested?.searchParams.get('direction'), 'desc')
  assert.equal(requested?.searchParams.get('sort'), 'value')
  assert.equal(requested?.searchParams.get('size'), '20')
  assert.equal(requested?.searchParams.has('year'), false)
  assert.equal(response.year, 2024)
  assert.equal(response.ranking.content[1].value, 0)
  assert.equal(response.ranking.content[2].value, -2)
  assert.equal(response.coverage.withoutData, 215)
})

test('ranking preserves an explicitly selected empty year instead of falling back to a newer or older observation', async () => {
  const data = fixture()
  data.year = 2026
  data.coverage = { totalCountries: 218, withData: 0, withoutData: 218 }
  data.ranking = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }
  mock.method(globalThis, 'fetch', async (input: string) => {
    assert.equal(new URL(input, 'http://atlas.test').searchParams.get('year'), '2026')
    return respond(data)
  })
  const response = await getRanking('SP.POP.TOTL', 'population-series', { year: 2026 })
  assert.equal(response.year, 2026)
  assert.equal(response.ranking.content.length, 0)
  assert.equal(response.coverage.withData, 0)
  assert.equal(response.coverage.withoutData, 218)
})

test('an unimported series can report no available reference year with zero coverage', async () => {
  const data = fixture()
  data.year = null
  data.availableYears = []
  data.coverage = { totalCountries: 218, withData: 0, withoutData: 218 }
  data.ranking = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }
  mock.method(globalThis, 'fetch', async () => respond(data))
  const response = await getRanking('SP.POP.TOTL', 'population-series')
  assert.equal(response.year, null)
  assert.deepEqual(response.availableYears, [])
  assert.equal(response.coverage.withData, 0)
})

test('pagination retains server ranks and ties rather than renumbering each page', async () => {
  const data = fixture()
  data.coverage = { totalCountries: 218, withData: 5, withoutData: 213 }
  data.ranking = {
    content: [
      { rank: 2, country: { iso3Code: 'BRA', name: 'Brasil' }, value: 0 },
      { rank: 4, country: { iso3Code: 'USA', name: 'Estados Unidos' }, value: 10 },
    ],
    page: 1, size: 2, totalElements: 5, totalPages: 3,
  }
  mock.method(globalThis, 'fetch', async (input: string) => {
    const request = new URL(input, 'http://atlas.test')
    assert.equal(request.searchParams.get('page'), '1')
    assert.equal(request.searchParams.get('direction'), 'asc')
    return respond(data)
  })
  const response = await getRanking('SP.POP.TOTL', 'population-series', { page: 1, size: 2, direction: 'asc' })
  assert.deepEqual(response.ranking.content.map(row => row.rank), [2, 4])
  assert.equal(response.ranking.content[0].value, 0)
})

test('invalid filters are rejected before requesting data', async () => {
  let requests = 0
  mock.method(globalThis, 'fetch', async () => { requests++; throw new Error('Unexpected request') })
  for (const filters of [{ year: 1799 }, { year: 2201 }, { year: 2024.5 }, { page: -1 }, { page: 10001 },
    { size: 0 }, { size: 101 }, { direction: 'invalid' as 'asc' }]) {
    await assert.rejects(getRanking('SP.POP.TOTL', 'population-series', filters), error => error instanceof ApiError && error.status === 400)
  }
  await assert.rejects(getRanking('SP.POP.TOTL', ''), error => error instanceof ApiError && error.status === 400)
  assert.equal(requests, 0)
})

test('inconsistent source identity, reference years, coverage and partial pages reject the ranking', async () => {
  const changes: ((value: Ranking) => void)[] = [
    value => { value.series.id = 'another-series' },
    value => { value.indicator.code = 'ANOTHER.INDICATOR' },
    value => { value.year = 2023 },
    value => { value.availableYears = [2023, 2024] },
    value => { value.coverage.withoutData = 0 },
    value => { value.ranking.content.pop() },
    value => { value.ranking.content[1].country = value.ranking.content[0].country },
    value => { value.ranking.content[1].value = Number.NaN },
    value => { value.ranking.content[2].value = 99 },
    value => { value.series.frequency = 'MONTHLY' },
  ]
  for (const change of changes) {
    const data = fixture()
    change(data)
    mock.method(globalThis, 'fetch', async () => respond(data))
    await assert.rejects(getRanking('SP.POP.TOTL', 'population-series'), error => error instanceof ApiError && error.status === 502)
    mock.restoreAll()
  }
})

test('a methodologically blocked series returns a safe error instead of provider details', async () => {
  mock.method(globalThis, 'fetch', async () => new Response('internal query details', { status: 409 }))
  await assert.rejects(getRanking('SI.POV.NAHC', 'national-poverty'), error => error instanceof ApiError
    && error.status === 409 && !error.message.includes('internal query'))
})
