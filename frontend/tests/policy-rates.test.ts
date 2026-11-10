import assert from 'node:assert/strict'
import { test } from 'node:test'
import { buildRatePoints, lastCommonRateDate } from '../src/lib/policy-rates.ts'
import { getPolicyRates, rateCountries, validDate } from '../src/services/policy-rates.ts'
import type { RateObservation } from '../src/services/policy-rates.ts'
import { formatValue, formatAxisValue } from '../src/lib/format.ts'
import { getRateCsv, rateCsvParameters } from '../src/services/exports.ts'
import { ApiError } from '../src/services/api.ts'

const observation = (countryCode: string, date: string, value: number | null): RateObservation => ({
  countryCode, date, value, status: value === null ? 'MISSING' : 'AVAILABLE', providerStatus: null,
  importedAt: '2026-11-10T12:00:00Z', sourceUpdatedAt: null,
})

test('policy rates preserve target-band midpoints, zero and negative rates without annual rounding', () => {
  assert.equal(formatValue(3.875, 'PERCENT_PER_ANNUM'), '3,875%')
  assert.equal(formatAxisValue(3.875, 'PERCENT_PER_ANNUM'), '3,875%')
  assert.equal(formatValue(0, 'PERCENT_PER_ANNUM'), '0%')
  assert.equal(formatValue(-0.125, 'PERCENT_PER_ANNUM'), '-0,125%')
  assert.equal(formatValue(null, 'PERCENT_PER_ANNUM'), '—')
})

test('daily rates align actual dates and never carry a value forward into another country gap', () => {
  const points = buildRatePoints([
    observation('BRA', '2026-11-01', 0), observation('USA', '2026-11-01', -0.125),
    observation('BRA', '2026-11-02', 1), observation('USA', '2026-11-03', null),
  ], ['BRA', 'USA'])
  assert.equal(lastCommonRateDate(points, ['BRA', 'USA']), '2026-11-01')
  assert.equal(points[1].values.USA, null)
  assert.equal(points[1].observations.USA, undefined)
  assert.equal(points[2].observations.USA?.status, 'MISSING')
  assert.equal(points[0].values.BRA, 0)
})

test('daily chart data rejects duplicate country dates and unrelated countries', () => {
  const row = observation('BRA', '2026-11-01', 1)
  assert.throws(() => buildRatePoints([row, row], ['BRA']))
  assert.throws(() => buildRatePoints([row], ['USA']))
})

test('policy selection supports individual countries and five-country comparisons with valid ISO dates', () => {
  assert.deepEqual(rateCountries('bra'), ['BRA'])
  assert.deepEqual(rateCountries('BRA,USA,CHN,JPN,FRA'), ['BRA', 'USA', 'CHN', 'JPN', 'FRA'])
  assert.throws(() => rateCountries('BRA,bra'))
  assert.throws(() => rateCountries('BRA,USA,CHN,JPN,FRA,CAN'))
  assert.equal(validDate('2024-02-29'), true)
  assert.equal(validDate('2026-02-29'), false)
  assert.equal(validDate('2026-02-30'), false)
})

test('policy client rejects source-frequency drift and invented observations', async () => {
  const original = globalThis.fetch
  try {
    const result = { code: 'POLICY.RATE', unit: 'PERCENT_PER_ANNUM', asOf: '2026-11-10', from: '2026-11-01', to: '2026-11-10',
      countries: [{ country: { iso3Code: 'BRA', name: 'Brasil' }, series: { frequency: 'ANNUAL' }, latestAvailable: null }], observations: [] }
    globalThis.fetch = async () => new Response(JSON.stringify(result), { headers: { 'Content-Type': 'application/json' } })
    await assert.rejects(getPolicyRates(['BRA'], '2026-11-01', '2026-11-10'), error => error instanceof ApiError && error.status === 502)
    result.countries[0].series.frequency = 'DAILY'
    const wrong = { ...result, observations: [observation('USA', '2026-11-01', 1)] }
    globalThis.fetch = async () => new Response(JSON.stringify(wrong), { headers: { 'Content-Type': 'application/json' } })
    await assert.rejects(getPolicyRates(['BRA'], '2026-11-01', '2026-11-10'), error => error instanceof ApiError && error.status === 502)
  } finally { globalThis.fetch = original }
})

test('dated CSV exports retain native dates without passing them as annual integer periods', async () => {
  const original = globalThis.fetch
  const selection = { countries: ['BRA', 'USA'], from: '2026-01-01', to: '2026-11-10' }
  assert.deepEqual(Object.fromEntries(rateCsvParameters(selection)), { countries: 'BRA,USA', from: '2026-01-01', to: '2026-11-10' })
  try {
    globalThis.fetch = async url => {
      assert.match(String(url), /policy-rates\/export\?countries=BRA%2CUSA&from=2026-01-01/)
      return new Response('country,period,value\r\nBRA,2026-11-10,13.75\r\n', { headers: { 'Content-Type': 'text/csv' } })
    }
    assert.ok((await (await getRateCsv(selection)).text()).includes('2026-11-10'))
  } finally { globalThis.fetch = original }
})
