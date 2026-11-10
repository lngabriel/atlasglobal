import assert from 'node:assert/strict'
import { test } from 'node:test'
import { providerStatusLabel } from '../src/lib/provider-status.ts'
import { formatValue, formatAxisValue, unitLabel } from '../src/lib/format.ts'
import { annualChange } from '../src/lib/history.ts'
import type { Observation } from '../src/services/api.ts'

test('source classifications distinguish projections from historical IMF estimates and missing values', () => {
  assert.equal(providerStatusLabel('IMF_PROJECTION'), 'Projeção do FMI')
  assert.match(providerStatusLabel('IMF_HISTORICAL')!, /pode conter estimativas/)
  assert.equal(providerStatusLabel(null), null)
  assert.equal(providerStatusLabel('A'), null)
  assert.equal(providerStatusLabel('bp'), 'Quebra de série · Provisório')
  assert.equal(providerStatusLabel('e'), 'Estimativa da fonte')
})

test('development indices and renewable-energy percentages retain native precision and scales', () => {
  assert.equal(formatValue(0.786, 'INDEX_0_1'), '0,786')
  assert.equal(formatValue(0, 'INDEX_0_1'), '0,000')
  assert.equal(formatValue(null, 'INDEX_0_1'), '—')
  assert.equal(formatAxisValue(0.786, 'INDEX_0_1'), '0,786')
  assert.equal(formatValue(26.128, 'PERCENT_GROSS_FINAL_ENERGY'), '26,128%')
  assert.equal(unitLabel('PERCENT_GDP'), '% do PIB')
  assert.equal(unitLabel('INDEX_0_1'), 'índice (0–1)')
})

test('index changes use absolute index points rather than implying a relative percentage change', () => {
  const observation = (period: number, value: number): Observation => ({ countryCode: 'BRA', period, value,
    status: 'AVAILABLE', providerStatus: null, sourceUpdatedAt: null, importedAt: null })
  assert.equal(annualChange([observation(2022, 0.783), observation(2023, 0.786)], 'INDEX_0_1')?.label, '+0,003 no índice vs. 2022')
})
