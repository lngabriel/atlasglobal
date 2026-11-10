import { ApiError } from './api.ts'
import { parseWorldGeometry } from '../lib/world-map.ts'

export async function getWorldGeometry(signal?: AbortSignal) {
  const timeout = AbortSignal.timeout(15_000)
  const requestSignal = signal ? AbortSignal.any([signal, timeout]) : timeout
  try {
    const response = await fetch('/geodata/countries-110m.geojson', {
      headers: { Accept: 'application/geo+json, application/json' }, credentials: 'omit', signal: requestSignal,
    })
    if (!response.ok) throw new ApiError(response.status)
    try { return parseWorldGeometry(await response.json()) }
    catch {
      requestSignal.throwIfAborted()
      throw new ApiError(502)
    }
  } catch (error) {
    if (signal?.aborted) throw signal.reason
    if (timeout.aborted) throw new ApiError(408)
    if (error instanceof ApiError) throw error
    throw new ApiError(0)
  }
}
