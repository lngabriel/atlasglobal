import assert from 'node:assert/strict'
import { afterEach, mock, test } from 'node:test'
import { formatValue, unitLabel } from '../src/lib/format.ts'
import { annualChange } from '../src/lib/history.ts'
import { searchFeaturedCountries } from '../src/lib/featured-countries.ts'
import { ApiError, getComparisonHistory } from '../src/services/api.ts'
import type { History, Observation, Page } from '../src/services/api.ts'

afterEach(() => mock.restoreAll())

function observation(countryCode: string, period: number, value: number | null): Observation {
  return {
    countryCode, period, value, status: value === null ? 'MISSING' : 'AVAILABLE',
    providerStatus: null, sourceUpdatedAt: '2026-10-08', importedAt: '2026-11-09T12:00:00Z',
  }
}

function paginatedHistory(countries: string[], observations: Observation[], page: number,
  from: number, to: number): History {
  const content: Page<Observation> = {
    content: observations.slice(page * 100, (page + 1) * 100),
    page, size: 100, totalElements: observations.length, totalPages: Math.ceil(observations.length / 100),
  }
  return {
    indicator: { id: 'population', code: 'SP.POP.TOTL', name: 'Population', category: 'DEMOGRAPHY', description: null },
    series: {
      id: 'population-series', externalCode: 'SP.POP.TOTL', datasetCode: '2', originalName: 'Population',
      methodology: 'Test definition', sourceOrganization: 'Test provider', unit: 'PERSONS', frequency: 'ANNUAL',
      source: {
        id: 'world-bank', code: 'WORLD_BANK', name: 'World Bank', baseUrl: 'https://api.worldbank.org/v2/',
        description: 'World Development Indicators', documentationUrl: null,
      },
      comparison: { allowed: true, note: 'Same source-specific definition.' },
    },
    countries: countries.map(iso3Code => ({ iso3Code, name: iso3Code })),
    from, to, observations: content,
  }
}

function records(countries: string[], from: number, to: number): Observation[] {
  return Array.from({ length: to - from + 1 }, (_, index) => from + index)
    .flatMap(year => [...countries].sort().map((country, index) => observation(country, year,
      year === from ? index === 0 ? 0 : null : (year - from) * 100 + index)))
}

function respond(history: History): Response {
  return new Response(JSON.stringify(history), { headers: { 'Content-Type': 'application/json' } })
}

test('Gini retains its published 0–100 scale without conversion to a ratio or percentage', () => {
  assert.equal(formatValue(50.3, 'GINI_INDEX'), '50,3')
  assert.equal(formatValue(0, 'GINI_INDEX'), '0')
  assert.equal(formatValue(100, 'GINI_INDEX'), '100')
  assert.equal(unitLabel('GINI_INDEX'), 'índice de Gini (0–100)')
})

test('PPP wages retain annual amounts and a distinct constant-price base', () => {
  assert.equal(formatValue(50_000, 'USD_PPP_2024_ANNUAL'), 'US$ PPC 50.000')
  assert.match(unitLabel('USD_PPP_2024_ANNUAL'), /anuais.*constantes de 2024/)
  assert.match(unitLabel('USD_PPP_2023_ANNUAL'), /constantes de 2023/)
  assert.notEqual(unitLabel('USD_PPP_2024_ANNUAL'), unitLabel('USD_PPP_2023_ANNUAL'))
})

test('international-dollar GDP, nominal US dollars, and household-debt ratios keep different meanings', () => {
  assert.equal(formatValue(23_433.29, 'INTERNATIONAL_DOLLARS_CURRENT_PER_PERSON'), 'Int$ 23.433,29')
  assert.equal(formatValue(23_433.29, 'USD_CURRENT_PER_PERSON'), 'US$ 23.433,29')
  assert.match(unitLabel('INTERNATIONAL_DOLLARS_CURRENT_PER_PERSON'), /internacionais correntes por pessoa/)
  assert.equal(formatValue(125.5, 'PERCENT_NET_DISPOSABLE_INCOME'), '125,5%')
  assert.equal(unitLabel('PERCENT_NET_DISPOSABLE_INCOME'), '% da renda disponível líquida')
})

test('Gini and intentional-homicide changes use absolute index or rate points', () => {
  const input = [observation('BRA', 2023, 51.5), observation('BRA', 2024, 50.3)]
  assert.equal(annualChange(input, 'GINI_INDEX', 'SI.POV.GINI')?.label, '-1,2 pontos vs. 2023')
  assert.equal(annualChange([observation('BRA', 2023, 20.5), observation('BRA', 2024, 19.3)],
    'HOMICIDES_PER_100K', 'VC.IHR.PSRC.P5')?.label, '-1,2 por 100 mil vs. 2023')
})

test('net-migration changes remain absolute with negative balances and a zero previous balance', () => {
  assert.equal(annualChange([observation('BRA', 2023, -1_000), observation('BRA', 2024, -600)],
    'PERSONS', 'SM.POP.NETM')?.label, '+400 pessoas vs. 2023')
  assert.equal(annualChange([observation('BRA', 2023, 0), observation('BRA', 2024, -200)],
    'PERSONS', 'SM.POP.NETM')?.label, '-200 pessoas vs. 2023')
  assert.equal(annualChange([observation('BRA', 2023, 100), observation('BRA', 2024, 110)],
    'PERSONS', 'SP.POP.TOTL')?.label, '+10% vs. 2023')
})

test('featured-country search ignores accents and case and recognizes ISO codes', () => {
  for (const [query, expected] of [['  AUSTRALIA ', 'AUS'], ['polonia', 'POL'], ['suica', 'CHE'],
    ['japao', 'JPN'], ['vietna', 'VNM'], ['twn', 'TWN'], ['JP', 'JPN']]) {
    assert.deepEqual(searchFeaturedCountries(query).map(country => country.code), [expected])
  }
  assert.deepEqual(searchFeaturedCountries('sem-correspondencia-xyz'), [])
  const all = searchFeaturedCountries('')
  assert.equal(all.length, 25)
  assert.equal(new Set(all.map(country => country.code)).size, 25)
})

for (const countries of [['BRA', 'USA'], ['BRA', 'USA', 'CHN', 'JPN', 'FRA']]) {
  test(`comparison loads every page for ${countries.length} countries and preserves zero and missing values`, async () => {
    const from = 1960, to = 2010
    const observations = records(countries, from, to)
    const requests: URL[] = []
    assert.ok(observations.length > 100)
    mock.method(globalThis, 'fetch', async (input: string, options: RequestInit) => {
      const request = new URL(input, 'http://atlas.test')
      requests.push(request)
      assert.equal(options.credentials, 'omit')
      return respond(paginatedHistory(countries, observations, Number(request.searchParams.get('page')), from, to))
    })
    const result = await getComparisonHistory(countries.map(code => ` ${code.toLowerCase()} `),
      'SP.POP.TOTL', 'population-series', from, to)
    assert.deepEqual(result.observations.content, observations)
    assert.equal(result.observations.content[0].value, 0)
    assert.equal(result.observations.content[1].value, null)
    assert.equal(result.observations.content[1].status, 'MISSING')
    assert.equal(result.observations.content[0].sourceUpdatedAt, '2026-10-08')
    assert.deepEqual(requests.map(request => Number(request.searchParams.get('page'))),
      Array.from({ length: Math.ceil(observations.length / 100) }, (_, index) => index))
    for (const request of requests) {
      assert.equal(request.pathname, '/api/v1/indicators/SP.POP.TOTL/history')
      assert.equal(request.searchParams.get('countries'), countries.join(','))
      assert.equal(request.searchParams.get('seriesId'), 'population-series')
      assert.equal(request.searchParams.get('from'), String(from))
      assert.equal(request.searchParams.get('to'), String(to))
      assert.equal(request.searchParams.get('size'), '100')
    }
  })
}

test('invalid comparison selections are rejected before any HTTP request', async () => {
  let requests = 0
  mock.method(globalThis, 'fetch', async () => { requests++; throw new Error('Unexpected request') })
  for (const countries of [[], ['BRA'], ['BRA', 'USA', 'CHN', 'JPN', 'FRA', 'GBR'],
    ['BRA', 'bra'], ['BRA', ' USA ', 'usa'], ['BR', 'USA'], ['BRA', '<X>']]) {
    await assert.rejects(getComparisonHistory(countries, 'SP.POP.TOTL', 'population-series', 2020, 2024),
      error => error instanceof ApiError && error.status === 400)
  }
  assert.equal(requests, 0)
})

test('an unrelated country in a later page rejects the comparison rather than returning partial history', async () => {
  const countries = ['BRA', 'USA']
  const from = 1960, to = 2010
  const observations = records(countries, from, to)
  observations[observations.length - 1] = { ...observations.at(-1)!, countryCode: 'FRA' }
  let requests = 0
  mock.method(globalThis, 'fetch', async (input: string) => {
    requests++
    const page = Number(new URL(input, 'http://atlas.test').searchParams.get('page'))
    return respond(paginatedHistory(countries, observations, page, from, to))
  })
  await assert.rejects(getComparisonHistory(countries, 'SP.POP.TOTL', 'population-series', from, to),
    error => error instanceof ApiError && error.status === 502)
  assert.equal(requests, 2)
})

test('a unit change between history pages cannot silently mix definitions', async () => {
  const countries = ['BRA', 'USA']
  const from = 1960, to = 2010
  const observations = records(countries, from, to)
  let requests = 0
  mock.method(globalThis, 'fetch', async (input: string) => {
    requests++
    const page = Number(new URL(input, 'http://atlas.test').searchParams.get('page'))
    const response = paginatedHistory(countries, observations, page, from, to)
    if (page > 0) response.series.unit = 'PERCENT_POPULATION'
    return respond(response)
  })
  await assert.rejects(getComparisonHistory(countries, 'SP.POP.TOTL', 'population-series', from, to),
    error => error instanceof ApiError && error.status === 502)
  assert.equal(requests, 2)
})
