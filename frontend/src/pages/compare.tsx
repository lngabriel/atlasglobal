import { lazy, Suspense } from 'react'
import { Link } from 'react-router-dom'
import { useCountryCatalog, useIndicators, useIndicator, useComparisonHistory } from '@/hooks/use-atlas-data'
import { useWorkspaceParams } from '@/hooks/use-workspace-params'
import { ErrorState, LoadingState } from '@/components/query-state'
import { PeriodFilter, currentYear, queryYear } from '@/components/period-filter'
import { Button } from '@/components/ui/button'
import { buildComparisonPoints, latestCommonPeriod, parseComparisonCountries } from '@/lib/comparison'
import { categoryLabel, formatDate, formatValue, unitLabel } from '@/lib/format'
import type { Country, History } from '@/services/api'

const ComparisonChart = lazy(() => import('@/components/comparison-chart').then(module => ({ default: module.ComparisonChart })))

function CountrySelection({ countries, codes, disabled, onChange }: {
  countries: Country[]
  codes: string[]
  disabled: boolean
  onChange: (codes: string[]) => void
}) {
  return (
    <fieldset className="min-w-0" disabled={disabled}>
      <legend className="text-xs font-semibold">Países e territórios</legend>
      <ul className="mt-3 flex flex-wrap gap-2" aria-label="Países selecionados para comparação">
        {codes.map(code => {
          const country = countries.find(item => item.iso3Code === code)
          return <li key={code} className="flex items-center gap-3 rounded-sm border border-border px-3 py-2 text-xs">
            <span>{country?.name ?? `${code} (não encontrado)`}</span>
            <button
              type="button"
              className="text-link text-[11px] text-muted-foreground disabled:opacity-40"
              aria-label={`Remover ${country?.name ?? code} da comparação`}
              disabled={codes.length <= 2}
              onClick={() => onChange(codes.filter(item => item !== code))}
            >
              Remover
            </button>
          </li>
        })}
      </ul>
      <label htmlFor="comparison-country" className="control-label mt-4 block">
        Adicionar país
        <select
          id="comparison-country"
          className="field mt-1.5 block w-full sm:max-w-80"
          value=""
          disabled={disabled || codes.length >= 5}
          onChange={event => { if (event.target.value) onChange([...codes, event.target.value]) }}
        >
          <option value="">{codes.length >= 5 ? 'Limite de cinco países atingido' : 'Selecione um país ou território'}</option>
          {countries.filter(country => !codes.includes(country.iso3Code)).map(country => (
            <option key={country.iso3Code} value={country.iso3Code}>{country.name}</option>
          ))}
        </select>
      </label>
      <p className="mt-2 text-[11px] leading-5 text-muted-foreground">Compare de 2 a 5 países na mesma série e no mesmo intervalo.</p>
    </fieldset>
  )
}

function CommonYearSummary({ history, countries }: { history: History; countries: Country[] }) {
  const points = buildComparisonPoints(history.observations.content, countries.map(country => country.iso3Code), history.from, history.to)
  const commonYear = latestCommonPeriod(points, countries.map(country => country.iso3Code))
  const common = points.find(point => point.period === commonYear)
  return (
    <section aria-labelledby="comparison-reference-title" className="my-6 border-y border-border py-5">
      <div className="mb-4 flex flex-wrap items-baseline justify-between gap-2">
        <h2 id="comparison-reference-title" className="text-xs font-semibold">Último ano com valores para todos</h2>
        <p className="text-xs tabular-nums text-muted-foreground">{commonYear ?? 'Sem ano em comum no período'}</p>
      </div>
      <dl className="grid grid-cols-2 gap-5 md:flex md:flex-wrap md:gap-x-12">
        {countries.map(country => {
          const latest = history.observations.content
            .filter(item => item.countryCode === country.iso3Code && item.status === 'AVAILABLE' && item.value !== null && Number.isFinite(item.value))
            .reduce<(typeof history.observations.content)[number] | null>((last, item) => !last || item.period > last.period ? item : last, null)
          const value = common?.values[country.iso3Code] ?? null
          return <div key={country.iso3Code} className="min-w-0">
            <dt className="text-xs text-muted-foreground">{country.name}</dt>
            <dd className="mt-2 text-xl font-medium tracking-tight tabular-nums" title={formatValue(value, history.series.unit)}>{formatValue(value, history.series.unit, true)}</dd>
            <dd className="mt-1 text-[11px] leading-5 text-muted-foreground">{latest ? `Último valor individual: ${latest.period}` : 'Sem valor no período'}</dd>
          </div>
        })}
      </dl>
      {!commonYear && <p className="mt-4 text-xs leading-6 text-muted-foreground">Os históricos abaixo preservam os anos individuais. Nenhum valor é transportado de um ano para outro para completar a comparação.</p>}
    </section>
  )
}

export function ComparePage() {
  const [params, setParams] = useWorkspaceParams()
  const selection = parseComparisonCountries(params.get('paises'))
  const catalog = useCountryCatalog()
  const indicators = useIndicators()
  const indicatorCode = params.get('indicador')?.trim() || 'NY.GDP.MKTP.CD'
  const definition = useIndicator(indicatorCode)
  const requestedSeries = params.get('serie')
  const series = definition.data?.series ?? []
  const selected = requestedSeries ? series.find(item => item.id === requestedSeries) : series.length === 1 ? series[0] : undefined
  const comparisonAllowed = selected?.comparison?.allowed !== false
  const knownCountries = catalog.data ?? []
  const countries = selection.countries.flatMap(code => {
    const country = knownCountries.find(item => item.iso3Code === code)
    return country ? [country] : []
  })
  const individualCountries = ['IBGE', 'BCB'].includes(selected?.source.code ?? '')
    ? knownCountries.filter(country => country.iso3Code === 'BRA') : countries
  const unknownCountry = Boolean(catalog.data) && countries.length !== selection.countries.length
  const to = queryYear(params.get('ate'), currentYear)
  const from = Math.min(queryYear(params.get('de'), currentYear - 19), to)
  const history = useComparisonHistory(
    catalog.data && !selection.error && !unknownCountry && comparisonAllowed ? selection.countries : [],
    selected ? indicatorCode : undefined,
    selected?.id,
    from,
    to,
  )
  const observations = history.data?.observations.content ?? []
  const providerDate = observations.flatMap(item => item.sourceUpdatedAt ? [item.sourceUpdatedAt] : []).sort().at(-1) ?? null
  const collectedDate = observations.flatMap(item => item.importedAt ? [item.importedAt] : []).sort().at(-1) ?? null
  const categories = [...new Set(indicators.data?.map(indicator => indicator.category) ?? [])]
  let documentationUrl: string | undefined
  try {
    const candidate = new URL(selected?.source.documentationUrl ?? '')
    if (candidate.protocol === 'https:') documentationUrl = candidate.href
  } catch { /* A source without public documentation still has its metadata displayed. */ }

  function changeCountries(codes: string[]) {
    setParams(previous => {
      const next = new URLSearchParams(previous)
      next.set('paises', codes.join(','))
      return next
    }, { preventScrollReset: true })
  }

  return (
    <div className="page-shell py-7 lg:py-9">
      <nav aria-label="Caminho da página" className="mb-7 text-xs text-muted-foreground">
        <Link to="/" className="text-link">Explorar países</Link><span className="mx-3" aria-hidden="true">/</span>Comparação
      </nav>
      <header className="mb-7">
        <h1 className="editorial-title text-3xl sm:text-4xl">Países em perspectiva.</h1>
        <p className="mt-3 max-w-2xl text-sm leading-6 text-muted-foreground">Consulte o mesmo indicador entre países, com unidade, ano e fonte identificados.</p>
      </header>
      <div className="grid min-w-0 gap-7 border-y border-border py-6 lg:grid-cols-2 lg:gap-12">
        <div className="min-w-0">
          {catalog.isPending ? <LoadingState label="Carregando países…" /> : catalog.isError ? (
            <ErrorState error={catalog.error} retry={() => void catalog.refetch()} />
          ) : <CountrySelection countries={knownCountries} codes={selection.countries} disabled={Boolean(selection.error)} onChange={changeCountries} />}
          {selection.error && <div role="alert" className="mt-3 text-xs leading-6">
            <p>{selection.error}</p><Button variant="outline" className="mt-3" onClick={() => changeCountries(['BRA', 'USA'])}>Restaurar seleção inicial</Button>
          </div>}
          {unknownCountry && <p role="status" className="mt-3 text-xs leading-6 text-muted-foreground">Um dos códigos não está no catálogo. Adicione um país válido e remova o código não encontrado.</p>}
        </div>
        <div className="min-w-0">
          {indicators.isPending ? <LoadingState label="Carregando indicadores…" /> : indicators.isError ? (
            <ErrorState error={indicators.error} retry={() => void indicators.refetch()} />
          ) : <label htmlFor="comparison-indicator" className="control-label block">
            Indicador
            <select
              id="comparison-indicator"
              className="field mt-1.5 block w-full"
              value={indicatorCode}
              onChange={event => setParams(previous => {
                const next = new URLSearchParams(previous)
                next.set('indicador', event.target.value)
                next.delete('serie')
                return next
              }, { preventScrollReset: true })}
            >
              {!indicators.data?.some(item => item.code === indicatorCode) && <option value={indicatorCode}>Indicador indisponível</option>}
              {categories.map(category => <optgroup key={category} label={categoryLabel(category)}>
                {indicators.data?.filter(item => item.category === category).map(indicator => <option key={indicator.code} value={indicator.code}>{indicator.name}</option>)}
              </optgroup>)}
            </select>
          </label>}
          {series.length > 0 && <label htmlFor="comparison-series" className="control-label mt-4 block">
            Fonte e definição da série
            <select
              id="comparison-series"
              className="field mt-1.5 block w-full"
              value={selected?.id ?? ''}
              onChange={event => setParams(previous => {
                const next = new URLSearchParams(previous)
                next.set('serie', event.target.value)
                return next
              }, { preventScrollReset: true })}
            >
              {!selected && <option value="" disabled>Selecione uma série para comparar</option>}
              {series.map(item => <option key={item.id} value={item.id}>{item.source.name} · {unitLabel(item.unit)} · {item.externalCode}{series.length > 1 ? ` · Conjunto ${item.datasetCode}` : ''}</option>)}
            </select>
          </label>}
        </div>
      </div>

      {definition.isPending ? <LoadingState label="Carregando a definição do indicador…" /> : definition.isError ? (
        <div className="mt-6"><ErrorState error={definition.error} retry={() => void definition.refetch()} /></div>
      ) : selected ? <section aria-labelledby="comparison-title" className="mt-7 min-w-0">
        <div className="mb-5 flex flex-wrap items-baseline justify-between gap-3">
          <h2 id="comparison-title" className="text-lg font-semibold tracking-tight">{definition.data?.indicator.name}</h2>
          <p className="text-xs text-muted-foreground">{unitLabel(selected.unit)} · {selected.frequency === 'ANNUAL' ? 'Frequência anual' : selected.frequency}</p>
        </div>
        <p className="mb-5 max-w-4xl text-xs leading-6 text-muted-foreground" role={!comparisonAllowed ? 'status' : undefined}>
          <span className="font-medium text-foreground">Comparabilidade. </span>
          {selected.comparison?.note ?? 'A mesma série e unidade não eliminam diferenças entre definições nacionais, pesquisas, cobertura e revisões. Consulte a metodologia antes de interpretar diferenças entre países.'}
        </p>
        {!comparisonAllowed ? <div className="border-y border-border py-8 text-sm leading-6 text-muted-foreground">
          <p>Esta série pode ser consultada nos perfis individuais. A comparação entre países está indisponível devido à definição ou à cobertura da série.</p>
          <div className="mt-3 flex flex-wrap gap-x-5 gap-y-2 text-xs">
            {individualCountries.map(country => <Link
              key={country.iso3Code}
              className="text-link"
              to={{ pathname: `/paises/${country.iso3Code}`, search: new URLSearchParams({ indicador: indicatorCode, serie: selected.id, de: String(from), ate: String(to) }).toString() }}
            >
              Consultar {country.name}
            </Link>)}
          </div>
        </div> : <>
          <PeriodFilter from={from} to={to} onChange={(first, last) => setParams(previous => {
            const next = new URLSearchParams(previous)
            next.set('de', String(first)); next.set('ate', String(last))
            return next
          }, { preventScrollReset: true })} />
          {selection.error || unknownCountry || !catalog.data ? null : history.isPending ? (
            <LoadingState label="Carregando o histórico completo da comparação…" />
          ) : history.isError ? <div className="mt-6"><ErrorState error={history.error} retry={() => void history.refetch()} /></div> : history.data ? <>
            <CommonYearSummary history={history.data} countries={countries} />
            <Suspense fallback={<LoadingState label="Preparando o gráfico comparativo…" />}>
              <ComparisonChart observations={observations} countries={countries} from={from} to={to} unit={selected.unit} />
            </Suspense>
            <div className="mt-4 flex flex-wrap justify-between gap-x-6 gap-y-2 text-[11px] leading-5 text-muted-foreground">
              <p>Fonte: {selected.source.name} · {selected.source.description ?? selected.datasetCode}</p>
              <p>Atualização mais recente na fonte: {formatDate(providerDate)} · Coleta: {formatDate(collectedDate)}</p>
            </div>
          </> : null}
        </>}
        <details className="mt-6 border-y border-border py-3 text-xs">
          <summary className="cursor-pointer font-medium">Definição e procedência</summary>
          <div className="mt-4 space-y-4 break-words leading-6 text-muted-foreground">
            <p><span className="font-medium text-foreground">{selected.originalName}</span><br />{unitLabel(selected.unit)} · {selected.externalCode} · Conjunto {selected.datasetCode}</p>
            <p lang="en">{selected.methodology ?? definition.data?.indicator.description ?? 'A fonte não informou uma definição para esta série.'}</p>
            {selected.sourceOrganization && <p><span className="font-medium text-foreground">Organização de origem</span><br /><span lang="en" className="whitespace-pre-line">{selected.sourceOrganization}</span></p>}
            {documentationUrl && <a href={documentationUrl} target="_blank" rel="noreferrer" className="text-link inline-block">Documentação da fonte</a>}
          </div>
        </details>
      </section> : <p role="status" className="py-8 text-sm leading-6 text-muted-foreground">{series.length ? 'Selecione uma definição da série para iniciar a comparação. Fontes e unidades diferentes não são combinadas.' : 'Este indicador ainda não possui uma série publicada para comparação.'}</p>}
    </div>
  )
}
