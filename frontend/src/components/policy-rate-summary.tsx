import { Link } from 'react-router-dom'
import { usePolicyRates } from '@/hooks/use-policy-rates'
import { formatDate, formatValue } from '@/lib/format'

export function PolicyRateSummary({ country }: { country: string }) {
  const rates = usePolicyRates([country])
  const item = rates.data?.countries[0]
  return <div className="mt-4 flex flex-wrap items-baseline justify-between gap-2 border-t border-border pt-3 text-xs">
    <p className="text-muted-foreground">Taxa básica de juros <span className="ml-2 font-medium tabular-nums text-foreground">
      {formatValue(item?.latestAvailable?.value ?? null, 'PERCENT_PER_ANNUM')}{item?.latestAvailable ? ' a.a.' : ''}
    </span>{item?.latestAvailable && <span className="ml-2">{formatDate(item.latestAvailable.date)} · {item.series?.source.name}</span>}
      {rates.isPending && <span className="ml-2">Consultando…</span>}
      {rates.isError && <span className="ml-2">Consulta temporariamente indisponível.</span>}
    </p>
    <Link className="text-link" to={`/juros?paises=${country}`}>Histórico e comparação de juros</Link>
    {item?.unavailableReason && <p className="w-full leading-5 text-muted-foreground">{item.unavailableReason}</p>}
  </div>
}
