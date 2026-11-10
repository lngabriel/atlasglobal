import type { RateObservation } from '../services/policy-rates.ts'

export interface RatePoint {
  date: string
  values: Record<string, number | null>
  observations: Record<string, RateObservation | undefined>
}

/** Only actual source dates are aligned. Missing rates are never carried forward. */
export function buildRatePoints(observations: RateObservation[], countries: string[]): RatePoint[] {
  const dates = new Map<string, RatePoint>()
  for (const observation of observations) {
    if (!countries.includes(observation.countryCode)) throw new Error('Unexpected policy-rate country')
    let point = dates.get(observation.date)
    if (!point) {
      point = { date: observation.date, values: Object.fromEntries(countries.map(code => [code, null])), observations: {} }
      dates.set(observation.date, point)
    }
    if (point.observations[observation.countryCode]) throw new Error('Duplicate policy-rate date')
    point.observations[observation.countryCode] = observation
    point.values[observation.countryCode] = observation.status === 'AVAILABLE' ? observation.value : null
  }
  return [...dates.values()].sort((first, second) => first.date.localeCompare(second.date))
}

export function lastCommonRateDate(points: RatePoint[], countries: string[]): string | null {
  return points.reduce<string | null>((latest, point) => countries.every(code => point.values[code] !== null)
    && (!latest || point.date > latest) ? point.date : latest, null)
}
