import { useQuery } from '@tanstack/react-query'
import { getCountries, getCountry, getCountryCatalog, getCountryIndicators, getHistory,
  getIndicators, getIndicator, getComparisonHistory, getOverview } from '@/services/api'
import type { CountryFilters } from '@/services/api'

export function useCountries(filters: CountryFilters = {}) {
  const parameters = {
    q: filters.q?.trim() ?? '', region: filters.region ?? '', incomeLevel: filters.incomeLevel ?? '',
    page: filters.page ?? 0, size: filters.size ?? 20,
  }
  return useQuery({
    queryKey: ['countries', parameters],
    queryFn: ({ signal }) => getCountries(parameters, signal),
  })
}

export function useCountry(code: string) {
  const normalized = code.toUpperCase()
  return useQuery({
    queryKey: ['country', normalized],
    queryFn: ({ signal }) => getCountry(normalized, signal),
    enabled: Boolean(normalized),
  })
}

export function useCountryCatalog() {
  return useQuery({
    queryKey: ['country-catalog'],
    queryFn: ({ signal }) => getCountryCatalog(signal),
    staleTime: 15 * 60_000,
  })
}

export function useCountryIndicators(code: string) {
  const normalized = code.toUpperCase()
  return useQuery({
    queryKey: ['country-indicators', normalized],
    queryFn: ({ signal }) => getCountryIndicators(normalized, signal),
    enabled: Boolean(normalized),
  })
}

export function useHistory(code: string, indicatorCode: string | undefined, seriesId: string | undefined,
  from: number, to: number) {
  const normalized = code.toUpperCase()
  return useQuery({
    queryKey: ['history', normalized, indicatorCode, seriesId, from, to],
    queryFn: ({ signal }) => getHistory(normalized, indicatorCode!, seriesId!, from, to, signal),
    enabled: Boolean(normalized && indicatorCode && seriesId)
      && Number.isInteger(from) && Number.isInteger(to) && from >= 1800 && to <= 2200 && from <= to,
  })
}

export function useIndicators() {
  return useQuery({ queryKey: ['indicators'], queryFn: ({ signal }) => getIndicators(signal) })
}

export function useIndicator(code: string | undefined) {
  const normalized = code?.toUpperCase()
  return useQuery({
    queryKey: ['indicator', normalized],
    queryFn: ({ signal }) => getIndicator(normalized!, signal),
    enabled: Boolean(normalized),
  })
}

export function useComparisonHistory(codes: string[], indicatorCode: string | undefined,
  seriesId: string | undefined, from: number, to: number) {
  return useQuery({
    queryKey: ['comparison-history', codes, indicatorCode, seriesId, from, to],
    queryFn: ({ signal }) => getComparisonHistory(codes, indicatorCode!, seriesId!, from, to, signal),
    enabled: codes.length >= 2 && codes.length <= 5 && new Set(codes).size === codes.length
      && Boolean(indicatorCode && seriesId) && Number.isInteger(from) && Number.isInteger(to)
      && from >= 1800 && to <= 2200 && from <= to,
  })
}

export function useOverview() {
  return useQuery({ queryKey: ['overview'], queryFn: ({ signal }) => getOverview(signal), staleTime: 5 * 60_000 })
}
