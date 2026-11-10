import { ApiError } from './api.ts'
import { rateCountries, validDate } from './policy-rates.ts'

export interface RateCsvSelection { countries: string[]; from: string; to: string }

export interface CsvSelection {
  countries: string[]
  indicator: string
  seriesId: string
  from: number
  to: number
}

export function csvParameters(selection: CsvSelection): URLSearchParams {
  const countries = selection.countries.map(code => code.trim().toUpperCase())
  const indicator = selection.indicator.toUpperCase()
  if (countries.length < 1 || countries.length > 5 || new Set(countries).size !== countries.length
    || countries.some(code => !/^[A-Z0-9]{3}$/.test(code)) || !/^[A-Z0-9.]{1,100}$/.test(indicator)
    || !/^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/i.test(selection.seriesId)
    || !Number.isInteger(selection.from) || !Number.isInteger(selection.to)
    || selection.from < 1800 || selection.to > 2200 || selection.from > selection.to) throw new ApiError(400)
  return new URLSearchParams({ countries: countries.join(','), indicator, seriesId: selection.seriesId,
    from: String(selection.from), to: String(selection.to) })
}

export async function getCsv(selection: CsvSelection, signal?: AbortSignal): Promise<Blob> {
  const parameters = csvParameters(selection)
  return getDownload('/exports/indicators', parameters, signal)
}

export function rateCsvParameters(selection: RateCsvSelection): URLSearchParams {
  const countries = rateCountries(selection.countries.join(','))
  if (!validDate(selection.from) || !validDate(selection.to) || selection.from > selection.to) throw new ApiError(400)
  return new URLSearchParams({ countries: countries.join(','), from: selection.from, to: selection.to })
}

export function getRateCsv(selection: RateCsvSelection, signal?: AbortSignal): Promise<Blob> {
  return getDownload('/policy-rates/export', rateCsvParameters(selection), signal)
}

async function getDownload(path: string, parameters: URLSearchParams, signal?: AbortSignal): Promise<Blob> {
  const timeout = AbortSignal.timeout(15_000)
  const requestSignal = signal ? AbortSignal.any([signal, timeout]) : timeout
  try {
    const response = await fetch(`/api/v1${path}?${parameters}`, {
      headers: { Accept: 'text/csv, application/problem+json' }, credentials: 'omit', signal: requestSignal,
    })
    if (!response.ok) throw new ApiError(response.status)
    if (response.headers.get('Content-Type')?.split(';')[0].trim().toLowerCase() !== 'text/csv') throw new ApiError(502)
    return await response.blob()
  } catch (error) {
    if (signal?.aborted) throw signal.reason
    if (timeout.aborted) throw new ApiError(408)
    if (error instanceof ApiError) throw error
    throw new ApiError(0)
  }
}
