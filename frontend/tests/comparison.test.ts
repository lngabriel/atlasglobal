import assert from 'node:assert/strict'
import test from 'node:test'
import type { Observation } from '../src/services/api.ts'
import { buildComparisonPoints, latestCommonPeriod, parseComparisonCountries } from '../src/lib/comparison.ts'

const observation = (countryCode: string, period: number, value: number | null): Observation => ({
  countryCode, period, value, status: value === null ? 'MISSING' : 'AVAILABLE',
  providerStatus: null, sourceUpdatedAt: '2026-01-01', importedAt: '2026-01-02T00:00:00Z',
})

test('comparison selection normalizes codes while retaining the requested country order', () => {
  assert.deepEqual(parseComparisonCountries(null), { countries: ['BRA', 'USA'], error: null })
  assert.deepEqual(parseComparisonCountries('usa, bra,TWN'), { countries: ['USA', 'BRA', 'TWN'], error: null })
})

test('comparison selection rejects duplicates and unsupported country counts or formats', () => {
  for (const input of ['', 'BRA', 'BRA,BRA', 'BRA,USA,CHN,JPN,FRA,GBR', 'BR,USA', 'BRA,<X>']) {
    const result = parseComparisonCountries(input)
    assert.ok(result.error)
    assert.deepEqual(result.countries, [])
  }
})

test('annual alignment preserves zero, explicit missing values and absent records for each country', () => {
  const input = [observation('BRA', 2020, 0), observation('USA', 2020, 5), observation('BRA', 2021, null), observation('USA', 2022, 6)]
  const points = buildComparisonPoints(input, ['BRA', 'USA'], 2020, 2022)
  assert.deepEqual(points.map(point => point.values), [
    { BRA: 0, USA: 5 }, { BRA: null, USA: null }, { BRA: null, USA: 6 },
  ])
  assert.equal(points[1].cells.BRA.status, 'MISSING')
  assert.equal(points[1].cells.USA.status, 'UNREPORTED')
  assert.equal(points[1].cells.USA.observation, null)
  assert.equal(input.length, 4)
})

test('a shared reference year requires a numeric value from every selected country', () => {
  const points = buildComparisonPoints([
    observation('BRA', 2022, 0), observation('USA', 2022, 5),
    observation('BRA', 2023, 8), observation('USA', 2023, null),
    observation('BRA', 2024, 9),
  ], ['BRA', 'USA'], 2022, 2024)
  assert.equal(latestCommonPeriod(points, ['BRA', 'USA']), 2022)
  assert.equal(latestCommonPeriod(points, ['BRA', 'USA', 'CHN']), null)
})

test('countries without imported observations remain unavailable rather than becoming zero', () => {
  const points = buildComparisonPoints([observation('BRA', 2024, 10)], ['BRA', 'TWN'], 2024, 2025)
  assert.equal(points[0].cells.TWN.status, 'UNREPORTED')
  assert.equal(points[0].values.TWN, null)
  assert.equal(latestCommonPeriod(points, ['BRA', 'TWN']), null)
})

test('alignment rejects duplicate or unrelated observations instead of silently mixing them', () => {
  assert.throws(() => buildComparisonPoints([observation('BRA', 2024, 1), observation('BRA', 2024, 2)], ['BRA', 'USA'], 2024, 2024))
  assert.throws(() => buildComparisonPoints([observation('FRA', 2024, 1)], ['BRA', 'USA'], 2024, 2024))
  assert.throws(() => buildComparisonPoints([observation('BRA', 2023, 1)], ['BRA', 'USA'], 2024, 2024))
})

test('nonfinite values and invalid intervals cannot create usable comparison points', () => {
  const points = buildComparisonPoints([observation('BRA', 2024, Infinity)], ['BRA', 'USA'], 2024, 2024)
  assert.equal(points[0].values.BRA, null)
  assert.equal(points[0].cells.BRA.status, 'MISSING')
  assert.deepEqual(buildComparisonPoints([], ['BRA', 'USA'], 2025, 2024), [])
})
