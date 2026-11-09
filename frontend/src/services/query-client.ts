import { QueryClient } from '@tanstack/react-query'
import { isTransientError } from '@/services/api'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 60_000,
      retry: (failureCount, error) => failureCount < 1 && isTransientError(error),
      refetchOnWindowFocus: false,
    },
  },
})
