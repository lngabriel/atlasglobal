export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface Country {
  id: string
  iso2Code: string
  iso3Code: string
  name: string
  originalName: string
  region: string | null
  incomeLevel: string | null
  capital: string | null
  latitude: number | null
  longitude: number | null
}

export type Category = 'ECONOMY' | 'DEMOGRAPHY' | 'EDUCATION' | 'HEALTH' | 'ENVIRONMENT' | 'TECHNOLOGY'

export interface Indicator {
  id: string
  code: string
  name: string
  description: string | null
  category: Category
}

export interface Source {
  id: string
  code: string
  name: string
  baseUrl: string
  description: string | null
  documentationUrl: string | null
}

export interface Series {
  id: string
  externalCode: string
  datasetCode: string
  originalName: string
  methodology: string | null
  sourceOrganization: string | null
  unit: string
  frequency: string
  source: Source
}

export interface Observation {
  countryCode: string
  period: number
  value: number | null
  status: 'AVAILABLE' | 'MISSING'
  providerStatus: string | null
  sourceUpdatedAt: string | null
  importedAt: string | null
}

export interface CountryIndicatorSummary {
  indicator: Indicator
  series: Series
  latestAvailable: Observation | null
  latestReported: Observation | null
}

export interface History {
  indicator: Indicator
  series: Series
  countries: { iso3Code: string; name: string }[]
  from: number
  to: number
  observations: Page<Observation>
}

export interface CountryFilters {
  q?: string
  region?: string
  incomeLevel?: string
  page?: number
  size?: number
}

const errorMessages: Record<number, string> = {
  0: 'Não foi possível conectar ao serviço de dados. Verifique sua conexão e tente novamente.',
  400: 'Os parâmetros da consulta são inválidos. Revise os filtros e tente novamente.',
  404: 'O país ou a série solicitada não foi encontrado.',
  408: 'A consulta demorou mais que o esperado. Tente novamente.',
  409: 'Este indicador possui mais de uma série. Selecione a fonte e a definição desejadas.',
  429: 'O serviço recebeu muitas consultas. Aguarde um momento e tente novamente.',
}

/** Keeps internal/provider error details out of the interface. */
export class ApiError extends Error {
  readonly status: number

  constructor(status: number) {
    super(errorMessages[status] ?? 'O serviço de dados está temporariamente indisponível. Tente novamente.')
    this.name = 'ApiError'
    this.status = status
  }
}

export function isTransientError(error: unknown): boolean {
  return error instanceof ApiError && (error.status === 0 || error.status === 408 || error.status === 429 || error.status >= 500)
}

async function get<T>(path: string, parameters: Record<string, string | number | undefined>, signal?: AbortSignal): Promise<T> {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(parameters)) {
    if (value !== undefined && value !== '') query.set(key, String(value))
  }
  const timeout = AbortSignal.timeout(15_000)
  const requestSignal = signal ? AbortSignal.any([signal, timeout]) : timeout
  try {
    const response = await fetch(`/api/v1${path}?${query}`, {
      headers: { Accept: 'application/json' },
      credentials: 'omit',
      signal: requestSignal,
    })
    if (!response.ok) throw new ApiError(response.status)
    try {
      return await response.json() as T
    } catch {
      requestSignal.throwIfAborted()
      throw new ApiError(502)
    }
  } catch (error) {
    // Preserve caller cancellation so TanStack Query can cancel obsolete requests.
    if (signal?.aborted) throw signal.reason
    if (timeout.aborted) throw new ApiError(408)
    if (error instanceof ApiError) throw error
    throw new ApiError(0)
  }
}

const segment = (value: string) => encodeURIComponent(value)

export function getCountries(filters: CountryFilters = {}, signal?: AbortSignal): Promise<Page<Country>> {
  return get('/countries', {
    q: filters.q?.trim(), region: filters.region, incomeLevel: filters.incomeLevel,
    page: filters.page ?? 0, size: filters.size ?? 20, sort: 'name', direction: 'asc',
  }, signal)
}

export function getCountry(code: string, signal?: AbortSignal): Promise<Country> {
  return get(`/countries/${segment(code)}`, {}, signal)
}

/** A changed or incomplete collection is rejected instead of publishing partial data. */
async function collectPages<T>(load: (page: number) => Promise<Page<T>>): Promise<T[]> {
  const first = await load(0)
  const validPage = (value: Page<T>, index: number) => Array.isArray(value.content)
    && value.page === index && Number.isInteger(value.totalPages) && value.totalPages >= 0
    && value.totalPages <= 10_001 && Number.isSafeInteger(value.totalElements) && value.totalElements >= 0
  if (!validPage(first, 0)) throw new ApiError(502)
  const content = [...first.content]
  for (let page = 1; page < first.totalPages; page++) {
    const next = await load(page)
    if (!validPage(next, page) || next.totalElements !== first.totalElements || next.totalPages !== first.totalPages) {
      throw new ApiError(502)
    }
    content.push(...next.content)
  }
  if (content.length !== first.totalElements) throw new ApiError(502)
  return content
}

export function getCountryCatalog(signal?: AbortSignal): Promise<Country[]> {
  return collectPages(page => getCountries({ page, size: 100 }, signal))
}

export function getCountryIndicators(code: string, signal?: AbortSignal): Promise<CountryIndicatorSummary[]> {
  return collectPages(page => get(`/countries/${segment(code)}/indicators`, {
    page, size: 100, sort: 'name', direction: 'asc',
  }, signal))
}

/** Returns the complete selected interval, never only its first API page. */
export async function getHistory(countryCode: string, indicatorCode: string, seriesId: string,
  from: number, to: number, signal?: AbortSignal): Promise<History> {
  let metadata: History | undefined
  const content = await collectPages<Observation>(async page => {
    const history = await get<History>(`/countries/${segment(countryCode)}/indicators/${segment(indicatorCode)}`, {
      seriesId, from, to, page, size: 100, sort: 'period', direction: 'asc',
    }, signal)
    if (!metadata) metadata = history
    if (history.series.id !== seriesId || history.indicator.code !== metadata.indicator.code
      || history.from !== from || history.to !== to
      || history.countries.length !== 1 || history.countries[0]?.iso3Code !== countryCode.toUpperCase()) {
      throw new ApiError(502)
    }
    return history.observations
  })
  const periods = new Set<number>()
  for (const observation of content) {
    if (observation.countryCode !== countryCode.toUpperCase() || !Number.isInteger(observation.period)
      || observation.period < from || observation.period > to || periods.has(observation.period)) {
      throw new ApiError(502)
    }
    periods.add(observation.period)
  }
  if (!metadata) throw new ApiError(502)
  return { ...metadata, observations: { content, page: 0, size: content.length, totalElements: content.length, totalPages: content.length ? 1 : 0 } }
}
