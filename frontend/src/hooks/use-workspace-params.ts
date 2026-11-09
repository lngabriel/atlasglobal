import { useCallback } from 'react'
import { useSearchParams, type NavigateOptions } from 'react-router-dom'

/** Merge with the latest URL, including navigations not yet committed by React. */
export function useWorkspaceParams() {
  const [params, setParams] = useSearchParams()
  const update = useCallback((change: (current: URLSearchParams) => URLSearchParams, options?: NavigateOptions) => {
    setParams(change(new URLSearchParams(window.location.search)), options)
  }, [setParams])
  return [params, update] as const
}
