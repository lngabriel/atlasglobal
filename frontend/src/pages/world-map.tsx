import { lazy, Suspense } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useCountry, useCountryCatalog, useCountryIndicators } from '@/hooks/use-atlas-data'
import { useWorkspaceParams } from '@/hooks/use-workspace-params'
import { LoadingState, ErrorState } from '@/components/query-state'
import { getWorldGeometry } from '@/services/world-map'
import { countryForGeometry } from '@/lib/world-map'
import { formatValue, unitLabel } from '@/lib/format'
import { regionLabel } from '@/lib/country-labels'

const WorldMapCanvas = lazy(() => import('@/components/world-map-canvas').then(module => ({ default: module.WorldMapCanvas })))

function SelectedCountry({ code }: { code: string }) {
  const country = useCountry(code)
  const indicators = useCountryIndicators(code)
  if (country.isPending) return <LoadingState label="Consultando país selecionado…" />
  if (country.isError) return <ErrorState error={country.error} retry={() => void country.refetch()} />
  const metrics = [
    { code: 'SP.POP.TOTL', name: 'População' },
    { code: 'NY.GDP.MKTP.CD', name: 'PIB' },
    { code: 'NY.GDP.PCAP.CD', name: 'PIB por pessoa' },
  ]
  return <section aria-labelledby="map-country-title" className="mt-7 border-y border-border py-5">
    <div className="flex flex-wrap items-baseline justify-between gap-4">
      <div>
        <p className="mb-2 text-[11px] uppercase tracking-widest text-muted-foreground">{country.data.iso3Code} · {regionLabel(country.data.region)}</p>
        <h2 id="map-country-title" className="editorial-title text-2xl">{country.data.name}</h2>
      </div>
      <Link className="text-link text-xs font-medium" to={`/paises/${country.data.iso3Code}`}>Abrir perfil do país →</Link>
    </div>
    {indicators.isPending ? <LoadingState label="Consultando indicadores…" /> : indicators.isError ? <ErrorState error={indicators.error} retry={() => void indicators.refetch()} /> : <dl className="mt-5 grid gap-5 sm:grid-cols-3">
      {metrics.map(metric => {
        const item = indicators.data.find(entry => entry.indicator.code === metric.code && entry.series.source.code === 'WORLD_BANK')
        return <div key={metric.code} data-map-indicator={metric.code} className="min-w-0">
          <dt className="text-xs text-muted-foreground">{metric.name}</dt>
          <dd className="mt-2 text-xl font-medium tracking-tight tabular-nums" title={formatValue(item?.latestAvailable?.value ?? null, item?.series.unit ?? '')}>
            {item ? formatValue(item.latestAvailable?.value ?? null, item.series.unit, true) : '—'}
          </dd>
          <dd className="mt-1 text-[11px] leading-5 text-muted-foreground">{item ? unitLabel(item.series.unit) : 'Sem série disponível'}</dd>
          <dd className="mt-1 text-[11px] text-muted-foreground">{item?.latestAvailable ? `${item.latestAvailable.period} · ${item.series.source.name}` : 'Sem valor disponível'}</dd>
        </div>
      })}
    </dl>}
  </section>
}

export function WorldMapPage() {
  const [params, setParams] = useWorkspaceParams()
  const selected = (params.get('pais')?.trim() || 'BRA').toUpperCase()
  const catalog = useCountryCatalog()
  const geometry = useQuery({ queryKey: ['world-geometry'], queryFn: ({ signal }) => getWorldGeometry(signal), staleTime: Infinity })
  const countries = catalog.data ?? []
  const known = countries.some(country => country.iso3Code === selected)
  const countryLookup = new Map(countries.map(country => [country.iso3Code, country]))
  const depicted = new Set(geometry.data?.features.flatMap(feature => {
    const country = countryForGeometry(feature, countryLookup)
    return country ? [country.iso3Code] : []
  }) ?? [])

  function select(code: string) {
    setParams(previous => {
      const next = new URLSearchParams(previous)
      next.set('pais', code)
      return next
    }, { preventScrollReset: true })
  }

  return <div className="page-shell py-7 lg:py-9">
    <header className="mb-7 flex flex-wrap items-end justify-between gap-5">
      <div>
        <h1 className="editorial-title text-3xl sm:text-4xl">O mundo, país a país.</h1>
        <p className="mt-3 max-w-2xl text-sm leading-6 text-muted-foreground">Selecione um país no mapa para consultar seus indicadores e explorar o perfil completo.</p>
      </div>
      <label htmlFor="map-country" className="control-label w-full sm:w-72">País ou território
        <select id="map-country" className="field mt-1.5 block w-full" value={known ? selected : ''} disabled={!catalog.data} onChange={event => select(event.target.value)}>
          {!known && <option value="">Selecione um país ou território</option>}
          {countries.map(country => <option key={country.iso3Code} value={country.iso3Code}>{country.name}</option>)}
        </select>
      </label>
    </header>
    {catalog.isPending ? <LoadingState label="Carregando catálogo de países…" /> : catalog.isError ? <ErrorState error={catalog.error} retry={() => void catalog.refetch()} /> : <>
      {geometry.isPending ? <LoadingState label="Carregando mapa mundial…" /> : geometry.isError ? <ErrorState error={geometry.error} retry={() => void geometry.refetch()} /> : <div className="relative overflow-hidden border-y border-border">
        <Suspense fallback={<LoadingState label="Preparando mapa interativo…" />}>
          <WorldMapCanvas geometry={geometry.data} countries={countries} selected={selected} onSelect={select} />
        </Suspense>
      </div>}
      <div className="mt-3 flex flex-wrap justify-between gap-x-6 gap-y-2 text-[11px] leading-5 text-muted-foreground">
        <p>Limites simplificados. Países e territórios pequenos podem não aparecer nesta escala; use o seletor para acessá-los.</p>
        <a className="text-link" href="https://www.naturalearthdata.com/" target="_blank" rel="noreferrer">Geometria: Natural Earth · 1:110 milhões ↗</a>
      </div>
      {!known ? <p role="status" className="mt-7 border-y border-border py-6 text-sm text-muted-foreground">O código selecionado não está no catálogo. Escolha um país no seletor acima.</p> : <>
        {geometry.data && !depicted.has(selected) && <p role="status" className="mt-5 text-xs leading-6 text-muted-foreground">{countries.find(country => country.iso3Code === selected)?.name} está disponível no catálogo, mas não possui uma área individual nesta escala do mapa.</p>}
        <SelectedCountry code={selected} />
      </>}
    </>}
  </div>
}
