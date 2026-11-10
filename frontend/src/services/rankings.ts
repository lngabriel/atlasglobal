import { ApiError, getJson } from './api.ts'
import type { Indicator, Page, Series } from './api.ts'

export interface RankingEntry {
  rank: number
  country: { iso3Code: string; name: string }
  value: number
  providerStatus?: string | null
}

export interface Ranking {
  indicator: Indicator
  series: Series
  year: number | null
  availableYears: number[]
  coverage: { totalCountries: number; withData: number; withoutData: number }
  ranking: Page<RankingEntry>
}

export interface RankingFilters {
  year?: number
  page?: number
  size?: number
  direction?: 'asc' | 'desc'
}

const isYear = (value: number) => Number.isInteger(value) && value >= 1800 && value <= 2200
const isCount = (value: number) => Number.isSafeInteger(value) && value >= 0

/** A ranking contains one series and one reference year, never each country's latest value. */
export async function getRanking(indicatorCode: string, seriesId: string, filters: RankingFilters = {},
  signal?: AbortSignal): Promise<Ranking> {
  const code = indicatorCode.trim().toUpperCase()
  const page = filters.page ?? 0, size = filters.size ?? 20, direction = filters.direction ?? 'desc'
  if (!/^[A-Z0-9.]{1,100}$/.test(code) || !seriesId.trim() || !isCount(page) || page > 10000
    || !Number.isInteger(size) || size < 1 || size > 100 || !['asc', 'desc'].includes(direction)
    || (filters.year !== undefined && !isYear(filters.year))) throw new ApiError(400)
  const response = await getJson<Ranking>(`/indicators/${encodeURIComponent(code)}/ranking`, {
    seriesId, year: filters.year, page, size, sort: 'value', direction,
  }, signal)
  if (!response || typeof response !== 'object') throw new ApiError(502)
  const coverage = response.coverage, entries = response.ranking
  if (response.indicator?.code !== code || response.series?.id !== seriesId
    || typeof response.series.unit !== 'string' || !response.series.unit
    || response.series.frequency !== 'ANNUAL' || !response.series.source?.id
    || response.series.comparison?.allowed === false
    || !Array.isArray(response.availableYears) || response.availableYears.some((year, index, years) => !isYear(year)
      || (index > 0 && year >= years[index - 1]))
    || (response.year !== null && !isYear(response.year))
    || (filters.year !== undefined && response.year !== filters.year)
    || (filters.year === undefined && response.year !== (response.availableYears[0] ?? null))
    || !coverage || !isCount(coverage.totalCountries) || !isCount(coverage.withData) || !isCount(coverage.withoutData)
    || coverage.withData + coverage.withoutData !== coverage.totalCountries
    || !entries || !Array.isArray(entries.content) || entries.page !== page || entries.size !== size
    || entries.totalElements !== coverage.withData || entries.totalPages !== Math.ceil(coverage.withData / size)
    || entries.content.length !== Math.min(size, Math.max(0, coverage.withData - page * size))
    || (response.year === null && coverage.withData !== 0)) throw new ApiError(502)
  const countries = new Set<string>()
  for (const [index, entry] of entries.content.entries()) {
    const previous = entries.content[index - 1]
    if (!entry || typeof entry !== 'object' || !Number.isSafeInteger(entry.rank) || entry.rank < 1 || entry.rank > coverage.withData
      || !entry.country || !/^[A-Z0-9]{3}$/.test(entry.country.iso3Code)
      || typeof entry.country.name !== 'string' || !entry.country.name.trim()
      || countries.has(entry.country.iso3Code) || !Number.isFinite(entry.value)
      || (previous && (entry.rank < previous.rank
        || (direction === 'desc' ? entry.value > previous.value : entry.value < previous.value)))) throw new ApiError(502)
    countries.add(entry.country.iso3Code)
  }
  return response
}
