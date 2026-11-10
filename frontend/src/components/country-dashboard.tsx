import { lazy, Suspense } from 'react'
import { ArrowUpRight } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useWorkspaceParams } from '@/hooks/use-workspace-params'
import { useCountry, useCountryIndicators, useHistory } from '@/hooks/use-atlas-data'
import { ErrorState, LoadingState } from '@/components/query-state'
import { PeriodFilter, currentYear, queryYear } from '@/components/period-filter'
import { CsvExportButton } from '@/components/csv-export-button'
import { PolicyRateSummary } from '@/components/policy-rate-summary'
import { ObservationNote } from '@/components/observation-note'
import { incomeLabel, regionLabel } from '@/lib/country-labels'
import { categoryLabel, formatDate, formatValue, unitLabel } from '@/lib/format'
import { annualChange } from '@/lib/history'
import type { CountryIndicatorSummary } from '@/services/api'

const HistoryChart = lazy(() => import('@/components/history-chart').then(module => ({ default: module.HistoryChart })))
const overviewCodes = ['NY.GDP.MKTP.CD', 'SP.POP.TOTL', 'NY.GDP.PCAP.CD', 'SP.DYN.LE00.IN']
const overviewNames: Record<string, string> = {
  'NY.GDP.MKTP.CD': 'PIB',
  'SP.POP.TOTL': 'População',
  'NY.GDP.PCAP.CD': 'PIB per capita',
  'SP.DYN.LE00.IN': 'Expectativa de vida',
}

function safeLink(value: string | null): string | undefined {
  if (!value) return undefined
  try {
    const url = new URL(value)
    return url.protocol === 'https:' ? url.href : undefined
  } catch {
    return undefined
  }
}

export function CountryDashboard({ code, standalone = false }: { code: string; standalone?: boolean }) {
  const [params, setParams] = useWorkspaceParams()
  const country = useCountry(code)
  const summaries = useCountryIndicators(code)
  const indicatorCode = params.get('indicador') ?? 'NY.GDP.MKTP.CD'
  const requestedSeries = params.get('serie')
  const matching = summaries.data?.filter(item => item.indicator.code === indicatorCode) ?? []
  const selected = requestedSeries ? matching.find(item => item.series.id === requestedSeries) : matching[0]
  const requestedFrom = queryYear(params.get('de'), currentYear - 19)
  const to = queryYear(params.get('ate'), currentYear)
  const from = Math.min(requestedFrom, to)
  const history = useHistory(code, selected?.indicator.code, selected?.series.id, from, to)
  const countryData = country.data
  const items = summaries.data ?? []
  const category = params.get('categoria') ?? ''
  const categories = [...new Set(items.map(item => item.indicator.category))]
  const visibleItems = category ? items.filter(item => item.indicator.category === category) : items
  const observations = history.data?.observations.content ?? []
  const available = observations.filter(item => (
    item.status === 'AVAILABLE' && item.value !== null && Number.isFinite(item.value)
  ))
  const latest = available.reduce<(typeof available)[number] | null>(
    (last, item) => !last || item.period > last.period ? item : last,
    null,
  )
  const change = selected ? annualChange(observations, selected.series.unit, selected.indicator.code) : null
  const providerDate = observations.flatMap(item => item.sourceUpdatedAt ? [item.sourceUpdatedAt] : []).sort().at(-1) ?? null
  const collectedDate = observations.flatMap(item => item.importedAt ? [item.importedAt] : []).sort().at(-1) ?? null

  function select(item: CountryIndicatorSummary) {
    setParams(previous => {
      const next = new URLSearchParams(previous)
      next.set('indicador', item.indicator.code)
      next.set('serie', item.series.id)
      return next
    }, { preventScrollReset: true })
  }

  function period(first: number, last: number) {
    setParams(previous => {
      const next = new URLSearchParams(previous)
      next.set('de', String(first))
      next.set('ate', String(last))
      return next
    }, { preventScrollReset: true })
  }

  if (country.isPending) return <LoadingState label="Carregando o país…" />
  if (country.isError) return <ErrorState error={country.error} retry={() => void country.refetch()} />
  if (!countryData) return null

  const Heading = standalone ? 'h1' : 'h2'
  const SectionHeading = standalone ? 'h2' : 'h3'
  return <article aria-label={`Indicadores de ${countryData.name}`} className="min-w-0" key={code}>
    <header className="flex flex-wrap items-start justify-between gap-4 pb-6">
      <div className="min-w-0">
        <p className="mb-2 text-[11px] font-medium uppercase tracking-[0.14em] text-muted-foreground">
          Perfil do país <span className="mx-1.5" aria-hidden="true">/</span> {countryData.iso3Code}
        </p>
        <Heading className="editorial-title flex items-center gap-3 text-3xl sm:text-4xl">
          {countryData.name}
        </Heading>
        <p className="mt-2 max-w-2xl text-xs leading-5 text-muted-foreground">
          {regionLabel(countryData.region)} <span className="mx-1.5" aria-hidden="true">·</span> {incomeLabel(countryData.incomeLevel)}
          {countryData.capital && <> <span className="mx-1.5" aria-hidden="true">·</span> Capital: {countryData.capital}</>}
        </p>
      </div>
      {!standalone && <Link
        to={{ pathname: `/paises/${code}`, search: params.toString() }}
        className="text-link mt-1 inline-flex items-center gap-1 text-xs"
      >
        Página do país <ArrowUpRight size={13} aria-hidden="true" />
      </Link>}
    </header>
    {summaries.isPending ? (
      <LoadingState label="Carregando indicadores…" />
    ) : summaries.isError ? (
      <ErrorState error={summaries.error} retry={() => void summaries.refetch()} />
    ) : items.length === 0 ? (
      <p className="border-y border-border py-10 text-sm text-muted-foreground">
        Não há séries cadastradas para este país.
      </p>
    ) : <>
      <section aria-label="Principais indicadores" className="grid grid-cols-2 gap-y-6 border-y border-border py-5 xl:grid-cols-4">
        {overviewCodes.flatMap(indicator => {
          const item = items.find(entry => entry.indicator.code === indicator)
          if (!item) return []
          return [<button
            type="button"
            key={item.series.id}
            onClick={() => select(item)}
            className="metric group min-w-0 pr-3 text-left"
            title={`Ver histórico: ${item.indicator.name} — ${item.series.source.name}`}
          >
            <span className="text-xs text-muted-foreground group-hover:text-foreground">{overviewNames[indicator]}</span>
            <span
              className="mt-2 block text-xl font-medium tracking-tight tabular-nums sm:text-2xl"
              title={formatValue(item.latestAvailable?.value ?? null, item.series.unit)}
            >
              {formatValue(item.latestAvailable?.value ?? null, item.series.unit, true)}
            </span>
            <span className="mt-1.5 block text-[11px] leading-5 text-muted-foreground">
              {item.latestAvailable ? `${item.latestAvailable.period} · ${item.series.source.name}` : 'Sem valor disponível'}
            </span>
          </button>]
        })}
      </section>
      <section aria-labelledby="history-title" className="pt-7">
        <div className="mb-5 flex flex-wrap items-start justify-between gap-4">
          <div>
            <p className="mb-2 text-[11px] font-medium uppercase tracking-[0.14em] text-muted-foreground">
              Série histórica
            </p>
            <SectionHeading id="history-title" className="text-lg font-semibold tracking-tight">
              {selected?.indicator.name ?? 'Selecione uma série'}
            </SectionHeading>
          </div>
          <label htmlFor="indicator-select" className="control-label max-w-full">Indicador e fonte
            <select
              id="indicator-select"
              className="field mt-1.5 block w-full max-w-80 sm:w-72"
              value={selected?.series.id ?? ''}
              onChange={event => {
                const item = items.find(entry => entry.series.id === event.target.value)
                if (item) select(item)
              }}
            >
              {!selected && <option value="" disabled>Série indisponível</option>}
              {categories.map(group => <optgroup key={group} label={categoryLabel(group)}>{items.filter(item => item.indicator.category === group).map(item => <option key={item.series.id} value={item.series.id}>
                {item.indicator.name} · {item.series.source.name}
                {items.filter(other => other.indicator.code === item.indicator.code).length > 1
                  ? ` · ${item.series.externalCode} · ${item.series.datasetCode} (${unitLabel(item.series.unit)})`
                  : ''}
              </option>)}</optgroup>)}
            </select>
          </label>
        </div>
        {selected && <p className="mb-4 text-xs leading-6 text-muted-foreground">
          {categoryLabel(selected.indicator.category)} · {selected.series.comparison?.allowed === false ? (
            <span>{selected.series.comparison.note}</span>
          ) : <Link
            to={`/comparar?paises=${code === 'USA' ? 'USA,BRA' : `${code},USA`}&indicador=${encodeURIComponent(selected.indicator.code)}&serie=${selected.series.id}&de=${from}&ate=${to}`}
            className="text-link"
          >Comparar este indicador</Link>}
        </p>}
        {!selected ? <p role="status" className="py-8 text-sm text-muted-foreground">
          A série deste endereço não está disponível. Escolha um indicador acima.
        </p> : <>
          <div className="mb-4 flex justify-end">
            <CsvExportButton selection={{ countries: [code], indicator: selected.indicator.code, seriesId: selected.series.id, from, to }}
              disabled={history.isPending || history.isError || observations.length === 0} />
          </div>
          <div className="border-b border-border pb-4">
            <PeriodFilter from={from} to={to} onChange={period} />
          </div>
          {history.isPending ? (
            <LoadingState label="Carregando o histórico completo…" />
          ) : history.isError ? (
            <ErrorState error={history.error} retry={() => void history.refetch()} />
          ) : <>
            <div className="flex flex-wrap items-baseline justify-between gap-3 pb-2 pt-5">
              <p>
                <span className="text-2xl font-medium tracking-tight tabular-nums">
                  {formatValue(latest?.value ?? null, selected.series.unit, true)}
                </span>
                <span className="ml-3 text-xs text-muted-foreground">{latest ? `em ${latest.period}` : 'no período'}</span>
                <ObservationNote status={latest?.providerStatus} />
              </p>
              {change && <p className="text-xs tabular-nums text-muted-foreground" title="Variação em relação ao ano anterior, na mesma série">
                {change.label}
              </p>}
            </div>
            <div className="mb-4 flex flex-wrap justify-between gap-2 text-xs text-muted-foreground">
              <p>{unitLabel(selected.series.unit)} · {selected.series.frequency === 'ANNUAL' ? 'Frequência anual' : selected.series.frequency}</p>
              <p>{from}–{to}</p>
            </div>
            {!latest && <p role="status" className="border-y border-border py-10 text-sm leading-6 text-muted-foreground">
              Nenhum valor disponível para este país e período. Experimente outro indicador ou amplie o intervalo. A ausência de dados não representa zero.
            </p>}
            <Suspense fallback={<LoadingState label="Preparando o gráfico…" />}>
              <HistoryChart observations={observations} from={from} to={to} unit={selected.series.unit} />
            </Suspense>
            <div className="mt-4 flex flex-wrap justify-between gap-x-6 gap-y-2 text-[11px] leading-5 text-muted-foreground">
              <p>Fonte: {selected.series.source.name} · {selected.series.source.description ?? selected.series.datasetCode}</p>
              <p>Atualização na fonte: {formatDate(providerDate)} <span className="mx-1" aria-hidden="true">·</span> Coleta: {formatDate(collectedDate)}</p>
            </div>
            <p className="mt-2 text-[11px] leading-5 text-muted-foreground">
              Lacunas indicam anos sem valor disponível. Os valores seguem a unidade e a metodologia da fonte, sem interpolação.
            </p>
          </>}
          <details className="mt-5 border-y border-border py-3 text-xs">
            <summary className="cursor-pointer font-medium">Definição e procedência</summary>
            <div className="mt-4 space-y-4 break-words leading-6 text-muted-foreground">
              <p>
                <span className="font-medium text-foreground">{selected.series.originalName}</span>
                <br />
                {categoryLabel(selected.indicator.category)} · {unitLabel(selected.series.unit)} · {selected.series.externalCode} · Conjunto {selected.series.datasetCode}
              </p>
              <p lang="en">
                {selected.series.methodology ?? selected.indicator.description ?? 'A fonte não informou uma definição para esta série.'}
              </p>
              {selected.series.comparison && <p>{selected.series.comparison.note}</p>}
              {selected.series.sourceOrganization && <p>
                <span className="font-medium text-foreground">Organização de origem</span>
                <br />
                <span lang="en" className="whitespace-pre-line">{selected.series.sourceOrganization}</span>
              </p>}
              {safeLink(selected.series.source.documentationUrl) && <a
                href={safeLink(selected.series.source.documentationUrl)}
                target="_blank"
                rel="noreferrer"
                className="text-link inline-flex items-center gap-1"
              >
                Documentação da fonte <ArrowUpRight size={13} aria-hidden="true" />
              </a>}
            </div>
          </details>
        </>}
      </section>
      <PolicyRateSummary country={code} />
      <section aria-labelledby="indicators-title" className="mt-8">
        <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
          <div>
            <SectionHeading id="indicators-title" className="text-sm font-semibold">Todos os indicadores</SectionHeading>
            <p className="mt-1 text-xs text-muted-foreground">Último valor disponível de cada série</p>
          </div>
          <label className="control-label" htmlFor="indicator-category">
            Categoria
            <select
              id="indicator-category"
              className="field mt-1 block w-40"
              value={category}
              onChange={event => setParams(previous => {
                const next = new URLSearchParams(previous)
                if (event.target.value) next.set('categoria', event.target.value)
                else next.delete('categoria')
                return next
              }, { preventScrollReset: true })}
            >
              <option value="">Todas as categorias</option>
              {category && !categories.some(value => value === category) && (
                <option value={category}>Categoria indisponível</option>
              )}
              {categories.map(value => <option key={value} value={value}>{categoryLabel(value)}</option>)}
            </select>
          </label>
        </div>
        <div className="overflow-x-auto">
          <table className="data-table w-full text-left text-xs">
            <caption className="sr-only">
              Últimos valores de {countryData.name}, com ano, unidade e fonte. Selecione um indicador para consultar o histórico.
            </caption>
            <thead>
              <tr>
                <th scope="col">Indicador</th>
                <th scope="col" className="text-right">Valor</th>
                <th scope="col" className="hidden text-right sm:table-cell">Ano</th>
                <th scope="col" className="hidden sm:table-cell">Fonte</th>
              </tr>
            </thead>
            <tbody>
              {visibleItems.map(item => <tr key={item.series.id} className={selected?.series.id === item.series.id ? 'bg-muted/60' : ''}>
                <th scope="row" className="max-w-72 font-normal">
                  <button type="button" onClick={() => select(item)} className="text-link text-left font-medium">
                    {item.indicator.name}
                  </button>
                  <span className="mt-1 block text-[11px] text-muted-foreground">{unitLabel(item.series.unit)}</span>
                  <span className="mt-1 block text-[11px] text-muted-foreground sm:hidden">{item.latestAvailable?.period ?? '—'} · {item.series.source.name}</span>
                  {item.latestReported?.status === 'MISSING' && <span className="mt-1 block text-[11px] text-muted-foreground">
                    {item.latestReported.period}: não informado pela fonte
                  </span>}
                </th>
                <td
                  className="text-right tabular-nums"
                  title={formatValue(item.latestAvailable?.value ?? null, item.series.unit)}
                >
                  {formatValue(item.latestAvailable?.value ?? null, item.series.unit, true)}
                  <ObservationNote status={item.latestAvailable?.providerStatus} />
                </td>
                <td className="hidden text-right tabular-nums sm:table-cell">{item.latestAvailable?.period ?? '—'}</td>
                <td className="hidden text-muted-foreground sm:table-cell">{item.series.source.name}</td>
              </tr>)}
              {visibleItems.length === 0 && <tr>
                <td colSpan={4}>Nenhum indicador nesta categoria. Selecione outra categoria acima.</td>
              </tr>}
            </tbody>
          </table>
        </div>
        <p className="mt-3 text-[11px] leading-5 text-muted-foreground">
          Cada série tem seu próprio ano de referência. Os valores mais recentes podem pertencer a períodos diferentes.
        </p>
      </section>
    </>}
  </article>
}
