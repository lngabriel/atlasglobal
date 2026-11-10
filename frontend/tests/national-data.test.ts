import assert from 'node:assert/strict'
import { test } from 'node:test'
import { formatAxisValue, formatValue, unitLabel } from '../src/lib/format.ts'
import { annualChange } from '../src/lib/history.ts'
import type { Observation } from '../src/services/api.ts'

function observation(period: number, value: number | null): Observation {
  return {
    countryCode: 'BRA', period, value, status: value === null ? 'MISSING' : 'AVAILABLE',
    providerStatus: null, sourceUpdatedAt: null, importedAt: '2026-11-09T12:00:00Z',
  }
}

test('national Gini coefficient preserves three decimals in headlines, tables and chart axes', () => {
  for (const compact of [false, true]) {
    assert.equal(formatValue(0.506, 'GINI_COEFFICIENT', compact), '0,506')
    assert.equal(formatValue(0.507, 'GINI_COEFFICIENT', compact), '0,507')
  }
  assert.equal(formatAxisValue(0.506, 'GINI_COEFFICIENT'), '0,506')
  assert.equal(formatAxisValue(0.507, 'GINI_COEFFICIENT'), '0,507')
  assert.equal(unitLabel('GINI_COEFFICIENT'), 'coeficiente de Gini (0–1)')
  assert.notEqual(unitLabel('GINI_COEFFICIENT'), unitLabel('GINI_INDEX'))
})

test('zero and the upper Gini endpoint remain numeric while missing and invalid values stay unavailable', () => {
  assert.equal(formatValue(0, 'GINI_COEFFICIENT'), '0,000')
  assert.equal(formatValue(1, 'GINI_COEFFICIENT'), '1,000')
  assert.equal(formatValue(null, 'GINI_COEFFICIENT'), '—')
  assert.equal(formatValue(Number.NaN, 'GINI_COEFFICIENT'), '—')
  assert.equal(formatAxisValue(Number.POSITIVE_INFINITY, 'GINI_COEFFICIENT'), '—')
})

test('a one-thousandth Gini change is an absolute coefficient difference rather than a rounded percentage', () => {
  assert.equal(annualChange([observation(2023, 0.507), observation(2024, 0.506)],
    'GINI_COEFFICIENT')?.label, '-0,001 no coeficiente vs. 2023')
  assert.equal(annualChange([observation(2023, 0), observation(2024, 0.001)],
    'GINI_COEFFICIENT')?.label, '+0,001 no coeficiente vs. 2023')
  assert.equal(annualChange([observation(2023, 0.506), observation(2024, 0.506)],
    'GINI_COEFFICIENT')?.label, '0 no coeficiente vs. 2023')
})

test('national annual changes still require consecutive numeric observations', () => {
  assert.equal(annualChange([observation(2022, 0.507), observation(2024, 0.506)],
    'GINI_COEFFICIENT'), null)
  assert.equal(annualChange([observation(2023, null), observation(2024, 0.506)],
    'GINI_COEFFICIENT'), null)
})

test('Brazilian household income retains reais, monthly per-person meaning and the provider price base', () => {
  assert.equal(formatValue(2_123.45, 'BRL_REAL_2025_MONTHLY_PER_PERSON'), 'R$ 2.123,45')
  assert.equal(formatValue(0, 'BRL_REAL_2025_MONTHLY_PER_PERSON'), 'R$ 0')
  assert.equal(formatValue(null, 'BRL_REAL_2025_MONTHLY_PER_PERSON'), '—')
  assert.equal(unitLabel('BRL_REAL_2025_MONTHLY_PER_PERSON'), 'R$ por pessoa por mês, preços constantes de 2025')
  assert.notEqual(unitLabel('BRL_REAL_2024_MONTHLY_PER_PERSON'), unitLabel('BRL_REAL_2025_MONTHLY_PER_PERSON'))
  assert.notEqual(unitLabel('BRL_REAL_2025_MONTHLY_PER_PERSON'), unitLabel('USD_PPP_2025_ANNUAL'))
})

test('the BCB debt denominator remains distinct and its annual changes use percentage points', () => {
  assert.equal(formatValue(49.3, 'PERCENT_HOUSEHOLD_RNDBF_12M'), '49,3%')
  assert.equal(unitLabel('PERCENT_HOUSEHOLD_RNDBF_12M'), '% da renda nacional disponível bruta das famílias (restrita), em 12 meses')
  assert.notEqual(unitLabel('PERCENT_HOUSEHOLD_RNDBF_12M'), unitLabel('PERCENT_NET_DISPOSABLE_INCOME'))
  assert.equal(annualChange([observation(2023, 48.9), observation(2024, 49.3)],
    'PERCENT_HOUSEHOLD_RNDBF_12M')?.label, '+0,4 p.p. vs. 2023')
})
