import { lazy, Suspense, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { CountrySelection } from '@/components/country-selection'
import { CsvExportButton } from '@/components/csv-export-button'
import { Button } from '@/components/ui/button'
import { ErrorState, LoadingState } from '@/components/query-state'
import { useCountryCatalog } from '@/hooks/use-atlas-data'
import { usePolicyRates } from '@/hooks/use-policy-rates'
import { useWorkspaceParams } from '@/hooks/use-workspace-params'
import { formatDate, formatValue } from '@/lib/format'
import { rateCountries, validDate } from '@/services/policy-rates'

const PolicyRateChart = lazy(() => import('@/components/policy-rate-chart').then(module => ({ default: module.PolicyRateChart })))
const today = new Date().toISOString().slice(0, 10)
function yearsAgo(years: number) { const date = new Date(`${today}T00:00:00Z`); date.setUTCFullYear(date.getUTCFullYear() - years); return date.toISOString().slice(0, 10) }

function DateFilter({ from, to, change }: { from: string; to: string; change: (from: string, to: string) => void }) {
  const [start, setStart] = useState(from), [end, setEnd] = useState(to), [error, setError] = useState('')
  useEffect(() => { setStart(from); setEnd(to); setError('') }, [from, to])
  return <div className="mb-6 flex flex-wrap items-end justify-between gap-4 border-y border-border py-4">
    <div className="flex gap-2">{[1, 5].map(years => <Button key={years} variant="ghost" onClick={() => change(yearsAgo(years), today)}>{years} {years === 1 ? 'ano' : 'anos'}</Button>)}</div>
    <form className="flex flex-wrap items-end gap-2" onSubmit={event => {
      event.preventDefault()
      if (!validDate(start) || !validDate(end) || start > end || end > today || start < '1900-01-01'
        || new Date(`${end}T00:00:00Z`).getUTCFullYear() - new Date(`${start}T00:00:00Z`).getUTCFullYear() > 20) {
        setError('Informe um intervalo de até 20 anos, sem datas futuras.'); return
      }
      setError(''); change(start, end)
    }}>
      <label htmlFor="rate-from" className="control-label">De<input id="rate-from" className="field mt-1 block" type="date" min="1900-01-01" max={today} required value={start} onChange={event => setStart(event.target.value)} /></label>
      <label htmlFor="rate-to" className="control-label">Até<input id="rate-to" className="field mt-1 block" type="date" min="1900-01-01" max={today} required value={end} onChange={event => setEnd(event.target.value)} /></label>
      <Button type="submit" variant="outline">Aplicar</Button>
      {error && <p role="alert" className="w-full text-xs">{error}</p>}
    </form>
  </div>
}

export function PolicyRatesPage() {
  const [params, setParams] = useWorkspaceParams()
  const catalog = useCountryCatalog()
  let codes: string[] = [], invalidSelection = false
  try { codes = rateCountries(params.get('paises')) } catch { invalidSelection = true }
  const requestedFrom = params.get('de') ?? yearsAgo(1), requestedTo = params.get('ate') ?? today
  const validRange = validDate(requestedFrom) && validDate(requestedTo) && requestedFrom <= requestedTo && requestedTo <= today
  const rates = usePolicyRates(invalidSelection || !validRange ? [] : codes, requestedFrom, requestedTo)
  function select(countries: string[]) {
    setParams(previous => { const next = new URLSearchParams(previous); next.set('paises', countries.join(',')); return next }, { preventScrollReset: true })
  }
  return <div className="page-shell py-7 lg:py-9">
    <header className="mb-7">
      <h1 id="rates-title" className="editorial-title text-3xl sm:text-4xl">Taxas básicas de juros.</h1>
      <p className="mt-3 max-w-3xl text-sm leading-6 text-muted-foreground">Metas e instrumentos de política monetária, em percentual ao ano, com datas e fontes oficiais.</p>
      <Link to="/comparar" className="text-link mt-3 inline-block text-xs">Comparar indicadores anuais</Link>
    </header>
    <section className="mb-7 border-y border-border py-5" aria-label="Selecionar países para consultar juros">
      {catalog.isPending ? <LoadingState label="Carregando países…" /> : catalog.isError ? <ErrorState error={catalog.error} retry={() => void catalog.refetch()} />
        : <CountrySelection countries={catalog.data} codes={codes} disabled={invalidSelection} onChange={select} minimum={1} />}
      {invalidSelection && <div role="alert" className="mt-3 text-sm"><p>Selecione de um a cinco países distintos.</p><Button className="mt-3" variant="outline" onClick={() => select(['BRA', 'USA'])}>Restaurar seleção inicial</Button></div>}
    </section>
    <DateFilter from={requestedFrom} to={requestedTo} change={(from, to) => setParams(previous => {
      const next = new URLSearchParams(previous); next.set('de', from); next.set('ate', to); return next
    }, { preventScrollReset: true })} />
    {!validRange ? <p role="alert" className="py-8 text-sm">Informe datas válidas no filtro acima.</p> : invalidSelection ? null
      : rates.isPending ? <LoadingState label="Consultando o histórico de juros…" /> : rates.isError ? <ErrorState error={rates.error} retry={() => void rates.refetch()} />
        : <>
          <p className="mb-6 max-w-4xl text-xs leading-6 text-muted-foreground">Compare as datas e os instrumentos, além dos valores. Metas, taxas de recompra e pontos médios de faixas não são definições idênticas. Os dados não são transformados em médias anuais.</p>
          <div id="rate-latest" className="mb-7 grid gap-6 border-b border-border pb-6 sm:grid-cols-2 lg:grid-cols-3">
            {rates.data.countries.map(item => <section key={item.country.iso3Code} data-rate-country={item.country.iso3Code} className="min-w-0">
              <h2 className="text-sm font-semibold">{item.country.name}</h2>
              <p className="mt-2 text-2xl font-medium tabular-nums" title={formatValue(item.latestAvailable?.value ?? null, 'PERCENT_PER_ANNUM')}>
                {formatValue(item.latestAvailable?.value ?? null, 'PERCENT_PER_ANNUM')}{item.latestAvailable ? ' a.a.' : ''}
              </p>
              <p className="mt-1 text-[11px] leading-5 text-muted-foreground">{item.latestAvailable ? `Última observação: ${formatDate(item.latestAvailable.date)}` : 'Sem taxa disponível'}</p>
              {item.series && <><p className="mt-1 text-xs leading-5 text-muted-foreground">{item.series.source.name} · Frequência diária</p>
                <p className="mt-1 text-[11px] leading-5 text-muted-foreground">Coleta: {formatDate(item.latestAvailable?.importedAt ?? null)} · Atualização estatística: {formatDate(item.latestAvailable?.sourceUpdatedAt ?? null)}</p></>}
              {item.unavailableReason && <p role="status" className="mt-2 text-xs leading-6 text-muted-foreground">{item.unavailableReason}</p>}
              <Link className="text-link mt-2 inline-block text-xs" to={`/paises/${item.country.iso3Code}`}>Perfil do país</Link>
            </section>)}
          </div>
          <div className="mb-4 flex justify-end"><CsvExportButton selection={{ countries: codes, from: requestedFrom, to: requestedTo }} disabled={rates.data.observations.length === 0} /></div>
          <Suspense fallback={<LoadingState label="Preparando o gráfico de juros…" />}>
            <PolicyRateChart key={`${codes.join(',')}:${requestedFrom}:${requestedTo}`} observations={rates.data.observations} countries={rates.data.countries} />
          </Suspense>
          <section className="mt-7" aria-labelledby="rate-methodology-title">
            <h2 id="rate-methodology-title" className="mb-3 text-sm font-semibold">Instrumentos e procedência</h2>
            {rates.data.countries.filter(item => item.series).map(item => <details key={item.country.iso3Code} className="border-t border-border py-3 text-xs">
              <summary className="cursor-pointer font-medium">{item.country.name} · {item.series!.source.name} · {item.series!.externalCode}</summary>
              <div className="mt-3 space-y-3 break-words text-xs leading-6 text-muted-foreground"><p className="font-medium text-foreground">{item.series!.instrument}</p>
                <p lang="en">{item.series!.methodology}</p><p>{item.series!.scopeNote}</p><p>{item.series!.sourceOrganization}</p>
                {item.series!.source.documentationUrl?.startsWith('https://') && <a className="text-link inline-block" href={item.series!.source.documentationUrl} target="_blank" rel="noreferrer">Documentação da fonte</a>}
              </div>
            </details>)}
          </section>
        </>}
  </div>
}
