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

export type Category = 'ECONOMY' | 'DEMOGRAPHY' | 'EDUCATION' | 'HEALTH' | 'ENVIRONMENT' | 'TECHNOLOGY' | 'INEQUALITY' | 'SAFETY' | 'MIGRATION'

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
  comparison?: { allowed: boolean; note: string }
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
  409: 'A série exige uma seleção explícita ou não permite esta comparação. Confira a fonte, a unidade e a metodologia.',
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

export interface IndicatorDetails {
  indicator: Indicator
  series: Series[]
}

export interface Overview {
  scope: { code: string; name: string }
  indicators: {
    indicator: Indicator
    series: Series
    latestAvailable: Omit<Observation, 'countryCode'> | null
    latestReported: Omit<Observation, 'countryCode'> | null
  }[]
}

export function getIndicators(signal?: AbortSignal): Promise<Indicator[]> {
  return collectPages(page => get('/indicators', { page, size: 100, sort: 'name', direction: 'asc' }, signal))
}

export function getIndicator(code: string, signal?: AbortSignal): Promise<IndicatorDetails> {
  return get(`/indicators/${segment(code)}`, {}, signal)
}

export function getOverview(signal?: AbortSignal): Promise<Overview> {
  return get('/overview', {}, signal)
}

/** Collects complete single-series history without mixing countries, definitions, or units. */
async function loadHistory(path: string, countries: string[], indicatorCode: string, seriesId: string,
  from: number, to: number, signal?: AbortSignal): Promise<History> {
  let metadata: History | undefined
  const expectedCountries = new Set(countries.map(code => code.toUpperCase()))
  const content = await collectPages<Observation>(async page => {
    const history = await get<History>(path, {
      countries: countries.length > 1 ? countries.join(',') : undefined,
      seriesId, from, to, page, size: 100, sort: 'period', direction: 'asc',
    }, signal)
    if (!metadata) metadata = history
    if (history.series.id !== seriesId || history.indicator.code !== indicatorCode.toUpperCase()
      || history.series.unit !== metadata.series.unit || history.series.datasetCode !== metadata.series.datasetCode
      || history.series.source.id !== metadata.series.source.id || history.series.frequency !== metadata.series.frequency
      || history.from !== from || history.to !== to
      || history.countries.length !== expectedCountries.size
      || new Set(history.countries.map(country => country.iso3Code)).size !== expectedCountries.size
      || history.countries.some(country => !expectedCountries.has(country.iso3Code))) {
      throw new ApiError(502)
    }
    return history.observations
  })
  const keys = new Set<string>()
  for (const observation of content) {
    const key = `${observation.countryCode}:${observation.period}`
    if (!expectedCountries.has(observation.countryCode) || !Number.isInteger(observation.period)
      || observation.period < from || observation.period > to || keys.has(key)) {
      throw new ApiError(502)
    }
    keys.add(key)
  }
  if (!metadata) throw new ApiError(502)
  return { ...metadata, observations: { content, page: 0, size: content.length, totalElements: content.length, totalPages: content.length ? 1 : 0 } }
}

export function getHistory(countryCode: string, indicatorCode: string, seriesId: string,
  from: number, to: number, signal?: AbortSignal): Promise<History> {
  return loadHistory(`/countries/${segment(countryCode)}/indicators/${segment(indicatorCode)}`,
    [countryCode], indicatorCode, seriesId, from, to, signal)
}

export function getComparisonHistory(countries: string[], indicatorCode: string, seriesId: string,
  from: number, to: number, signal?: AbortSignal): Promise<History> {
  const codes = countries.map(code => code.trim().toUpperCase())
  if (codes.length < 2 || codes.length > 5 || new Set(codes).size !== codes.length
    || codes.some(code => !/^[A-Z0-9]{3}$/.test(code))) return Promise.reject(new ApiError(400))
  return loadHistory(`/indicators/${segment(indicatorCode)}/history`, codes, indicatorCode, seriesId, from, to, signal)
}
