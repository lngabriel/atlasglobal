import type { Observation } from '../services/api.ts'

export interface ComparisonSelection {
  countries: string[]
  error: string | null
}

export interface ComparisonCell {
  value: number | null
  status: 'AVAILABLE' | 'MISSING' | 'UNREPORTED'
  observation: Observation | null
}

export interface ComparisonPoint {
  period: number
  values: Record<string, number | null>
  cells: Record<string, ComparisonCell>
}

export function parseComparisonCountries(value: string | null): ComparisonSelection {
  const countries = (value ?? 'BRA,USA').split(',').map(code => code.trim().toUpperCase())
  if (countries.length < 2 || countries.length > 5 || countries.some(code => !/^[A-Z0-9]{3}$/.test(code))) {
    return { countries: [], error: 'Selecione de 2 a 5 países usando códigos ISO de três caracteres.' }
  }
  if (new Set(countries).size !== countries.length) {
    return { countries: [], error: 'Cada país deve aparecer apenas uma vez na comparação.' }
  }
  return { countries, error: null }
}

/** Aligns one source-specific series by year without filling either kind of missing value. */
export function buildComparisonPoints(observations: Observation[], countries: string[], from: number, to: number): ComparisonPoint[] {
  if (!Number.isInteger(from) || !Number.isInteger(to) || from < 1800 || to > 2200 || from > to) return []
  const requested = new Set(countries)
  if (requested.size !== countries.length) throw new Error('Repeated comparison country')
  const byCountryYear = new Map<string, Observation>()
  for (const observation of observations) {
    if (!requested.has(observation.countryCode) || !Number.isInteger(observation.period)
      || observation.period < from || observation.period > to) {
      throw new Error('Observation outside the selected comparison')
    }
    const key = `${observation.countryCode}:${observation.period}`
    if (byCountryYear.has(key)) throw new Error('Repeated country and year in comparison')
    byCountryYear.set(key, observation)
  }
  return Array.from({ length: to - from + 1 }, (_, index) => {
    const period = from + index
    const values: Record<string, number | null> = {}
    const cells: Record<string, ComparisonCell> = {}
    for (const code of countries) {
      const observation = byCountryYear.get(`${code}:${period}`) ?? null
      const available = observation?.status === 'AVAILABLE' && observation.value !== null && Number.isFinite(observation.value)
      const value = available ? observation.value : null
      values[code] = value
      cells[code] = {
        value,
        status: observation ? available ? 'AVAILABLE' : 'MISSING' : 'UNREPORTED',
        observation,
      }
    }
    return { period, values, cells }
  })
}

export function latestCommonPeriod(points: ComparisonPoint[], countries: string[]): number | null {
  if (countries.length < 2) return null
  return points.reduce<number | null>((latest, point) => (
    countries.every(code => point.cells[code]?.status === 'AVAILABLE') && (latest === null || point.period > latest)
      ? point.period : latest
  ), null)
}

export function comparisonStatus(status: ComparisonCell['status']): string {
  return status === 'AVAILABLE' ? 'Disponível' : status === 'MISSING' ? 'Não informado pela fonte' : 'Ano sem registro'
}
