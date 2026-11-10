import { useQuery } from '@tanstack/react-query'
import { getRanking } from '@/services/rankings'
import type { RankingFilters } from '@/services/rankings'

export function useRanking(indicatorCode: string | undefined, seriesId: string | undefined,
  filters: RankingFilters = {}) {
  const code = indicatorCode?.trim().toUpperCase()
  const parameters = {
    year: filters.year, page: filters.page ?? 0, size: filters.size ?? 20,
    direction: filters.direction ?? 'desc',
  }
  return useQuery({
    queryKey: ['ranking', code, seriesId, parameters],
    queryFn: ({ signal }) => getRanking(code!, seriesId!, parameters, signal),
    enabled: Boolean(code && seriesId),
  })
}
