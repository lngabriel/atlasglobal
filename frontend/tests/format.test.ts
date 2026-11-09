import assert from 'node:assert/strict'
import test from 'node:test'
import { formatAxisValue, formatDate, formatValue, unitLabel } from '../src/lib/format.ts'

test('unavailable and invalid values are never formatted as zero', () => {
  for (const value of [null, NaN, Infinity, -Infinity]) assert.equal(formatValue(value, 'PERSONS'), '—')
  assert.equal(formatValue(0, 'PERCENT_ANNUAL'), '0%')
})

test('large values use Brazilian decimal notation and explicit scale', () => {
  assert.equal(formatValue(2_173_000_000_000, 'USD_CURRENT', true), 'US$ 2,17 tri')
  assert.equal(formatValue(213_450_000, 'PERSONS', true), '213,45 mi pessoas')
  assert.equal(formatValue(2_173_000_000_000, 'USD_CURRENT'), 'US$ 2.173.000.000.000')
})

test('rate, life expectancy and negative values retain their meaning', () => {
  assert.equal(formatValue(-2.345, 'PERCENT_ANNUAL'), '-2,35%')
  assert.equal(formatValue(75.83, 'YEARS'), '75,83 anos')
  assert.equal(formatValue(12_345.67, 'USD_CURRENT_PER_PERSON'), 'US$ 12.345,67')
  assert.equal(unitLabel('PERCENT_LABOR_FORCE'), '% da força de trabalho')
  assert.equal(unitLabel('OTHER_UNIT'), 'OTHER_UNIT')
})

test('chart axis abbreviates numbers while keeping rate marks', () => {
  assert.equal(formatAxisValue(1_200_000_000, 'USD_CURRENT'), '1,2 bi')
  assert.equal(formatAxisValue(4.25, 'PERCENT_POPULATION'), '4,25%')
})

test('source dates are stable in UTC and absent or invalid dates stay explicit', () => {
  assert.match(formatDate('2024-01-01'), /01.*jan.*2024/)
  assert.equal(formatDate(null), 'Não informada')
  assert.equal(formatDate('not-a-date'), 'Não informada')
})
