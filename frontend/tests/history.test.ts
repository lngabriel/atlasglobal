import assert from 'node:assert/strict'
import test from 'node:test'
import type { Observation } from '../src/services/api.ts'
import { annualChange, buildHistoryPoints } from '../src/lib/history.ts'

const observation = (period: number, value: number | null, countryCode = 'BRA'): Observation => ({
  countryCode, period, value, status: value === null ? 'MISSING' : 'AVAILABLE',
  providerStatus: null, sourceUpdatedAt: '2025-12-01', importedAt: '2026-01-10T00:00:00Z',
})

test('a chart preserves zero, explicit missing values, and years without a source record', () => {
  const input = [observation(2020, 0), observation(2021, null), observation(2023, 10)]
  const points = buildHistoryPoints(input, 2020, 2023)
  assert.deepEqual(points.map(({ period, value, status }) => ({ period, value, status })), [
    { period: 2020, value: 0, status: 'AVAILABLE' },
    { period: 2021, value: null, status: 'MISSING' },
    { period: 2022, value: null, status: 'UNREPORTED' },
    { period: 2023, value: 10, status: 'AVAILABLE' },
  ])
  assert.equal(points[2].observation, null)
  assert.equal(input.length, 3)
})

test('empty data produces an uninterrupted sequence of unavailable years, never invented values', () => {
  assert.deepEqual(buildHistoryPoints([], 2020, 2022).map(point => point.value), [null, null, null])
  assert.deepEqual(buildHistoryPoints([], 2023, 2020), [])
  assert.deepEqual(buildHistoryPoints([], 1799, 2201), [])
})

test('nonfinite source values cannot reach the graph as numeric values', () => {
  assert.equal(buildHistoryPoints([observation(2020, Infinity)], 2020, 2020)[0].value, null)
})

test('an annual rate change is in percentage points rather than a percent change', () => {
  assert.deepEqual(annualChange([observation(2023, 4), observation(2024, 5.25)], 'PERCENT_ANNUAL'), {
    label: '+1,25 p.p. vs. 2023', period: 2024, previousPeriod: 2023,
  })
})

test('count and currency changes use the previous calendar year and preserve ordering of input', () => {
  const input = [observation(2023, 100), observation(2024, 110), observation(2025, null)]
  assert.equal(annualChange(input, 'USD_CURRENT')?.label, '+10% vs. 2023')
  assert.deepEqual(input.map(value => value.period), [2023, 2024, 2025])
  assert.equal(annualChange([observation(2023, 75), observation(2024, 75.4)], 'YEARS')?.label, '+0,4 anos vs. 2023')
})

test('a missing previous year cannot be silently replaced by an older one', () => {
  assert.equal(annualChange([observation(2022, 100), observation(2023, null), observation(2024, 110)], 'PERSONS'), null)
  assert.equal(annualChange([observation(2022, 100), observation(2024, 110)], 'PERSONS'), null)
})

test('relative change is unavailable for a zero denominator, while a zero rate can change in points', () => {
  const input = [observation(2023, 0), observation(2024, 1)]
  assert.equal(annualChange(input, 'PERSONS'), null)
  assert.equal(annualChange(input, 'PERCENT_ANNUAL')?.label, '+1 p.p. vs. 2023')
})

test('observations from different countries are not silently compared', () => {
  assert.equal(annualChange([observation(2023, 100, 'USA'), observation(2024, 110)], 'USD_CURRENT'), null)
})
