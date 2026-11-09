import { Link } from 'react-router-dom'
import { useOverview } from '@/hooks/use-atlas-data'
import { LoadingState, ErrorState } from '@/components/query-state'
import { formatValue, unitLabel } from '@/lib/format'

const names: Record<string, string> = {
  'NY.GDP.MKTP.CD': 'PIB mundial',
  'SP.POP.TOTL': 'População mundial',
  'SP.DYN.LE00.IN': 'Expectativa de vida',
  'IT.NET.USER.ZS': 'Uso da internet',
}

export function WorldOverview() {
  const overview = useOverview()
  return <section id="overview" aria-labelledby="overview-title" className="mb-10">
    <div className="mb-3 flex flex-wrap items-baseline justify-between gap-2">
      <h2 id="overview-title" className="text-xs font-medium uppercase tracking-widest text-muted-foreground">Perspectiva global</h2>
      <p className="text-[11px] text-muted-foreground">Agregados mundiais publicados pelo Banco Mundial</p>
    </div>
    {overview.isPending ? <LoadingState label="Consultando indicadores globais…" /> : overview.isError ? (
      <ErrorState error={overview.error} retry={() => void overview.refetch()} />
    ) : overview.data.indicators.length === 0 ? <p className="border-y border-border py-6 text-sm text-muted-foreground">Os agregados mundiais ainda não estão disponíveis.</p> : <div className="grid grid-cols-2 gap-y-6 border-y border-border py-5 xl:grid-cols-4">
      {Object.keys(names).flatMap(code => overview.data.indicators.filter(item => item.indicator.code === code)).map(item => <Link
        key={item.series.id}
        data-indicator={item.indicator.code}
        to={`/comparar?indicador=${encodeURIComponent(item.indicator.code)}`}
        className="metric group min-w-0 pr-3"
      >
        <span className="text-xs text-muted-foreground group-hover:text-foreground">{names[item.indicator.code] ?? item.indicator.name}</span>
        <span className="mt-2 block text-xl font-medium tracking-tight tabular-nums sm:text-2xl" title={formatValue(item.latestAvailable?.value ?? null, item.series.unit)}>
          {formatValue(item.latestAvailable?.value ?? null, item.series.unit, true)}
        </span>
        <span className="mt-1 block text-[11px] leading-5 text-muted-foreground">{unitLabel(item.series.unit)}</span>
        <span className="mt-1 block text-[11px] text-muted-foreground">{item.latestAvailable?.period ?? 'Sem valor disponível'} · {item.series.source.name}</span>
      </Link>)}
    </div>}
  </section>
}
