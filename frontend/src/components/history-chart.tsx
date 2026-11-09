import { useMemo } from 'react'
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { Observation } from '@/services/api'
import { buildHistoryPoints, type HistoryPoint } from '@/lib/history'
import { formatAxisValue, formatValue, unitLabel } from '@/lib/format'

function ChartTooltip({ active, payload, label, unit }: {
  active?: boolean
  payload?: ReadonlyArray<{ payload?: HistoryPoint }>
  label?: string | number
  unit: string
}) {
  if (!active || !payload?.length) return null
  const point = payload[0]?.payload
  if (!point) return null
  return <div className="border border-border bg-background px-3 py-2 text-xs leading-5 text-foreground">
    <p className="font-medium">{label}</p>
    <p className="tabular-nums">{formatValue(point.value, unit)}</p>
    {point.value === null && <p className="text-muted-foreground">{point.status === 'UNREPORTED' ? 'Ano sem registro' : 'Valor não informado pela fonte'}</p>}
  </div>
}

export function HistoryChart({ observations, from, to, unit }: {
  observations: Observation[]
  from: number
  to: number
  unit: string
}) {
  const points = useMemo(() => buildHistoryPoints(observations, from, to), [observations, from, to])
  const ticks = useMemo(() => {
    const step = Math.max(1, Math.ceil((to - from) / 5))
    const years = [from]
    for (let year = from + step; year < to; year += step) years.push(year)
    if (to !== from) years.push(to)
    return years
  }, [from, to])

  return <div className="min-w-0">
    {points.some(point => point.value !== null) && <div className="h-[280px] min-w-0" role="group" aria-label={`Série histórica de ${from} a ${to}. Unidade: ${unitLabel(unit)}. Valores disponíveis na tabela abaixo.`}>
      <ResponsiveContainer width="100%" height="100%" minWidth={0}>
        <LineChart data={points} margin={{ top: 12, right: 10, bottom: 8, left: 0 }} accessibilityLayer>
          <CartesianGrid vertical={false} stroke="var(--border)" strokeDasharray="2 4" />
          <XAxis dataKey="period" type="number" domain={from === to ? [from - .5, to + .5] : [from, to]} ticks={ticks} allowDecimals={false} axisLine={false} tickLine={false} tick={{ fill: 'var(--muted-foreground)', fontSize: 11 }} tickMargin={12} minTickGap={22} />
          <YAxis width={62} tickFormatter={value => formatAxisValue(Number(value), unit)} axisLine={false} tickLine={false} tick={{ fill: 'var(--muted-foreground)', fontSize: 11 }} tickMargin={8} domain={['auto', 'auto']} />
          <Tooltip content={<ChartTooltip unit={unit} />} filterNull={false} cursor={{ stroke: 'var(--muted-foreground)', strokeWidth: 1, strokeDasharray: '3 3' }} />
          <Line type="linear" dataKey="value" stroke="var(--foreground)" strokeWidth={1.8} connectNulls={false} dot={{ r: points.length <= 3 ? 3 : 1.4, strokeWidth: 0, fill: 'var(--foreground)' }} activeDot={{ r: 4, strokeWidth: 2, stroke: 'var(--background)', fill: 'var(--foreground)' }} isAnimationActive={false} />
        </LineChart>
      </ResponsiveContainer>
    </div>}
    <div className="mt-3 flex flex-wrap items-baseline justify-between gap-x-4 gap-y-2 text-xs leading-5 text-muted-foreground">
      <details className="w-full border-t border-border pt-3">
        <summary className="w-fit cursor-pointer font-medium text-foreground underline-offset-4 hover:underline">Ver dados em tabela</summary>
        <div className="mt-3 max-h-72 overflow-auto overscroll-contain border-b border-border" tabIndex={0} aria-label="Tabela de dados históricos, área de rolagem">
          <table className="w-full border-collapse text-left text-xs">
            <caption className="sr-only">Dados anuais de {from} a {to}, em {unitLabel(unit)}</caption>
            <thead className="sticky top-0 bg-background text-muted-foreground"><tr><th scope="col" className="border-b border-border px-2 py-2 font-medium">Ano</th><th scope="col" className="border-b border-border px-2 py-2 text-right font-medium">Valor</th><th scope="col" className="border-b border-border px-2 py-2 font-medium">Situação</th></tr></thead>
            <tbody>{[...points].reverse().map(point => <tr key={point.period} className="border-b border-border last:border-0">
              <th scope="row" className="px-2 py-2 font-normal tabular-nums text-foreground">{point.period}</th>
              <td className="px-2 py-2 text-right tabular-nums text-foreground">{formatValue(point.value, unit)}</td>
              <td className="px-2 py-2">{point.status === 'AVAILABLE' ? 'Disponível' : point.status === 'MISSING' ? 'Não informado pela fonte' : 'Ano sem registro'}</td>
            </tr>)}</tbody>
          </table>
        </div>
      </details>
    </div>
  </div>
}
