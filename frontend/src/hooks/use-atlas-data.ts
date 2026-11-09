import { useQuery } from '@tanstack/react-query'
import { getCountries, getCountry, getCountryCatalog, getCountryIndicators, getHistory } from '@/services/api'
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
