import type { Observation } from '../services/api.ts'

export interface HistoryPoint {
  period: number
  value: number | null
  status: 'AVAILABLE' | 'MISSING' | 'UNREPORTED'
  observation: Observation | null
}

/** Null placeholders interrupt the line without inventing an observation in the source. */
export function buildHistoryPoints(observations: Observation[], from: number, to: number): HistoryPoint[] {
  if (!Number.isInteger(from) || !Number.isInteger(to) || from < 1800 || to > 2200 || from > to) return []
  const byYear = new Map(observations.map(observation => [observation.period, observation]))
  return Array.from({ length: to - from + 1 }, (_, index) => {
    const period = from + index
    const observation = byYear.get(period) ?? null
    const available = observation?.status === 'AVAILABLE' && observation.value !== null && Number.isFinite(observation.value)
    return {
      period,
      value: available ? observation.value : null,
      status: observation ? available ? 'AVAILABLE' : 'MISSING' : 'UNREPORTED',
      observation,
    }
  })
}

/** The caller supplies one country's single series, optionally limited to its visible interval. */
export function annualChange(observations: Observation[], unit: string): { label: string; period: number; previousPeriod: number } | null {
  if (new Set(observations.map(observation => observation.countryCode)).size > 1) return null
  const numeric = observations
    .filter((observation): observation is Observation & { value: number } => observation.status === 'AVAILABLE' && observation.value !== null && Number.isFinite(observation.value))
    .sort((a, b) => b.period - a.period)
  const latest = numeric[0]
  if (!latest) return null
  const previous = numeric.find(observation => observation.period === latest.period - 1)
  if (!previous) return null
  const difference = latest.value - previous.value
  const percentageUnit = unit.startsWith('PERCENT_')
  const absolute = percentageUnit || unit === 'YEARS'
  if (!absolute && previous.value === 0) return null
  const change = absolute ? difference : difference / Math.abs(previous.value) * 100
  if (!Number.isFinite(change)) return null
  const rounded = Math.round((change + Number.EPSILON) * 100) / 100
  const value = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 2, signDisplay: 'exceptZero' }).format(Object.is(rounded, -0) ? 0 : rounded)
  const suffix = percentageUnit ? ' p.p.' : unit === 'YEARS' ? ' anos' : '%'
  return { label: `${value}${suffix} vs. ${previous.period}`, period: latest.period, previousPeriod: previous.period }
}
