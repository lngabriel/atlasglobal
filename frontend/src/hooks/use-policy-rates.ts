import { useQuery } from '@tanstack/react-query'
import { getPolicyRates } from '@/services/policy-rates'

export function usePolicyRates(countries: string[], from?: string, to?: string) {
  return useQuery({
    queryKey: ['policy-rates', countries, from, to],
    queryFn: ({ signal }) => getPolicyRates(countries, from, to, signal),
    enabled: countries.length >= 1 && countries.length <= 5,
    staleTime: 5 * 60_000,
  })
}
