import { useMemo, useState } from 'react'
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Button } from '@/components/ui/button'
import { buildRatePoints, lastCommonRateDate, type RatePoint } from '@/lib/policy-rates'
import { formatDate, formatValue } from '@/lib/format'
import type { CountryRate, RateObservation } from '@/services/policy-rates'

const styles = [undefined, '7 4', '2 4', '9 3 2 3', '1 3']
function RateTooltip({ active, payload, countries }: {
  active?: boolean; payload?: ReadonlyArray<{ payload?: RatePoint }>; countries: CountryRate[]
}) {
  const point = payload?.[0]?.payload
  if (!active || !point) return null
  return <div className="max-w-80 border border-border bg-background px-3 py-2 text-xs leading-6">
    <p className="font-medium">{formatDate(point.date)}</p>
    {countries.map(item => <p key={item.country.iso3Code} className="flex justify-between gap-4">
      <span>{item.country.name}</span><span>{formatValue(point.values[item.country.iso3Code], 'PERCENT_PER_ANNUM')}</span>
    </p>)}
  </div>
}

export function PolicyRateChart({ observations, countries }: { observations: RateObservation[]; countries: CountryRate[] }) {
  const points = useMemo(() => buildRatePoints(observations, countries.map(item => item.country.iso3Code)), [observations, countries])
  const [page, setPage] = useState(0)
  const common = countries.length >= 2 ? lastCommonRateDate(points, countries.map(item => item.country.iso3Code)) : null
  const pages = Math.ceil(points.length / 50)
  const visiblePage = Math.min(page, Math.max(0, pages - 1))
  const rows = [...points].reverse().slice(visiblePage * 50, (visiblePage + 1) * 50)
  return <div className="min-w-0">
    {countries.length >= 2 && <p id="rate-common-date" className="mb-5 text-xs leading-6 text-muted-foreground">
      Última data com valores para todos no intervalo: <span className="font-medium text-foreground">{common ? formatDate(common) : 'Sem data em comum'}</span>.
    </p>}
    <ul aria-label="Legenda das taxas" className="mb-4 flex flex-wrap gap-x-6 gap-y-2 text-xs">
      {countries.map((item, index) => <li key={item.country.iso3Code} className="flex items-center gap-2">
        <svg width="26" height="12" aria-hidden="true"><line x1="0" y1="6" x2="26" y2="6" stroke="var(--foreground)" strokeDasharray={styles[index]} strokeWidth="2" /></svg>
        {item.country.name}
      </li>)}
    </ul>
    {points.some(point => Object.values(point.values).some(value => value !== null)) ? <div className="h-72 min-w-0" role="group" aria-label="Histórico diário das taxas básicas, em percentual ao ano. Dados disponíveis na tabela abaixo.">
      <ResponsiveContainer width="100%" height="100%" minWidth={0}>
        <LineChart data={points} accessibilityLayer margin={{ top: 12, right: 12, left: 0, bottom: 8 }}>
          <CartesianGrid vertical={false} stroke="var(--border)" strokeDasharray="2 4" />
          <XAxis dataKey="date" tickFormatter={value => formatDate(String(value))} minTickGap={55} axisLine={false} tickLine={false} tick={{ fill: 'var(--muted-foreground)', fontSize: 10 }} />
          <YAxis width={48} domain={['auto', 'auto']} tickFormatter={value => `${new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 3 }).format(Number(value))}%`} axisLine={false} tickLine={false} tick={{ fill: 'var(--muted-foreground)', fontSize: 10 }} />
          <Tooltip content={<RateTooltip countries={countries} />} filterNull={false} />
          {countries.map((item, index) => <Line key={item.country.iso3Code} type="stepAfter"
            dataKey={point => (point as RatePoint).values[item.country.iso3Code]} name={item.country.name}
            stroke="var(--foreground)" strokeWidth={1.6} strokeDasharray={styles[index]} connectNulls={false}
            dot={false} activeDot={{ r: 3 }} isAnimationActive={false} />)}
        </LineChart>
      </ResponsiveContainer>
    </div> : <p className="border-y border-border py-8 text-sm text-muted-foreground">Sem valores disponíveis no intervalo. A ausência de observações não representa juros zero.</p>}
    <p className="mt-3 text-[11px] leading-5 text-muted-foreground">São preservadas as datas publicadas. Lacunas não são preenchidas com a taxa de outro dia. Instrumentos e datas de publicação podem diferir entre países.</p>
    <details className="mt-5 border-y border-border py-3 text-xs">
      <summary className="cursor-pointer font-medium">Ver taxas em tabela</summary>
      <div className="mt-3 overflow-x-auto" tabIndex={0} aria-label="Tabela diária de taxas">
        <table id="rate-history-table" className="data-table w-full text-left text-xs">
          <caption className="sr-only">Taxas básicas diárias, em percentual ao ano, sem preenchimento de lacunas.</caption>
          <thead><tr><th scope="col">Data</th>{countries.map(item => <th key={item.country.iso3Code} scope="col" className="text-right">{item.country.name}</th>)}</tr></thead>
          <tbody>{rows.map(point => <tr key={point.date}>
            <th scope="row" className="font-normal tabular-nums">{formatDate(point.date)}</th>
            {countries.map(item => <td key={item.country.iso3Code} className="text-right tabular-nums">
              <span>{formatValue(point.values[item.country.iso3Code], 'PERCENT_PER_ANNUM')}</span>
              {point.values[item.country.iso3Code] === null && <span className="mt-1 block whitespace-normal text-[10px] text-muted-foreground">
                {point.observations[item.country.iso3Code] ? 'Não informado pela fonte' : 'Sem observação nesta data'}
              </span>}
            </td>)}
          </tr>)}</tbody>
        </table>
      </div>
      {pages > 1 && <nav aria-label="Paginação das taxas" className="mt-4 flex items-center justify-between gap-3">
        <Button variant="outline" disabled={visiblePage === 0} onClick={() => setPage(visiblePage - 1)}>Mais recentes</Button>
        <span className="text-muted-foreground">{visiblePage + 1} / {pages}</span>
        <Button variant="outline" disabled={visiblePage + 1 >= pages} onClick={() => setPage(visiblePage + 1)}>Mais antigas</Button>
      </nav>}
    </details>
  </div>
}
