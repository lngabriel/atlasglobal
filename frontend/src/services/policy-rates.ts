import { ApiError, getJson } from './api.ts'
import type { Source } from './api.ts'

export interface RateObservation {
  countryCode: string
  date: string
  value: number | null
  status: 'AVAILABLE' | 'MISSING'
  providerStatus: string | null
  sourceUpdatedAt: string | null
  importedAt: string
}
export interface CountryRate {
  country: { iso3Code: string; name: string }
  series: { id: string; externalCode: string; frequency: 'DAILY'; instrument: string;
    methodology: string; sourceOrganization: string; scopeNote: string; source: Source } | null
  latestAvailable: RateObservation | null
  unavailableReason: string | null
}
export interface PolicyRates {
  code: 'POLICY.RATE'
  name: string
  unit: 'PERCENT_PER_ANNUM'
  asOf: string
  countries: CountryRate[]
  from: string | null
  to: string | null
  observations: RateObservation[]
}

export function validDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false
  const date = new Date(`${value}T00:00:00Z`)
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value
}

export function rateCountries(value: string | null): string[] {
  const countries = (value ?? 'BRA,USA').split(',').map(code => code.trim().toUpperCase())
  if (countries.length < 1 || countries.length > 5 || new Set(countries).size !== countries.length
    || countries.some(code => !/^[A-Z0-9]{3}$/.test(code))) throw new ApiError(400)
  return countries
}

export async function getPolicyRates(countries: string[], from?: string, to?: string, signal?: AbortSignal): Promise<PolicyRates> {
  const codes = rateCountries(countries.join(','))
  const history = from !== undefined || to !== undefined
  if (history && (!from || !to || !validDate(from) || !validDate(to) || from > to)) throw new ApiError(400)
  const result = await getJson<PolicyRates>(`/policy-rates${history ? '/history' : ''}`, { countries: codes.join(','), from, to }, signal)
  if (result.code !== 'POLICY.RATE' || result.unit !== 'PERCENT_PER_ANNUM' || !validDate(result.asOf)
    || !Array.isArray(result.countries) || result.countries.length !== codes.length
    || new Set(result.countries.map(item => item.country.iso3Code)).size !== codes.length
    || result.countries.some(item => !codes.includes(item.country.iso3Code) || (item.series && item.series.frequency !== 'DAILY'))
    || !Array.isArray(result.observations) || (history && (result.from !== from || result.to !== to))) throw new ApiError(502)
  const validObservation = (item: RateObservation, code?: string) => codes.includes(item.countryCode)
    && (!code || item.countryCode === code) && validDate(item.date) && item.date <= result.asOf
    && (item.status === 'AVAILABLE' ? item.value !== null && Number.isFinite(item.value) : item.status === 'MISSING' && item.value === null)
  for (const item of result.countries) {
    if (item.latestAvailable && (!item.series || !validObservation(item.latestAvailable, item.country.iso3Code)
      || item.latestAvailable.status !== 'AVAILABLE')) throw new ApiError(502)
  }
  const keys = new Set<string>()
  for (const observation of result.observations) {
    const key = `${observation.countryCode}:${observation.date}`
    if (!validObservation(observation) || !from || !to || observation.date < from || observation.date > to || keys.has(key)) throw new ApiError(502)
    keys.add(key)
  }
  return result
}
