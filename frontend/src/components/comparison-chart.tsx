import { useMemo } from 'react'
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { Observation } from '@/services/api'
import { buildComparisonPoints, comparisonStatus, type ComparisonPoint } from '@/lib/comparison'
import { formatAxisValue, formatValue, unitLabel } from '@/lib/format'

interface ComparisonCountry {
  iso3Code: string
  name: string
}

const lineStyles = [
  { stroke: 'var(--foreground)', dash: undefined },
  { stroke: 'var(--foreground)', dash: '7 4' },
  { stroke: 'var(--muted-foreground)', dash: undefined },
  { stroke: 'var(--foreground)', dash: '2 4' },
  { stroke: 'var(--muted-foreground)', dash: '9 3 2 3' },
]

function ComparisonTooltip({ active, payload, countries, unit }: {
  active?: boolean
  payload?: ReadonlyArray<{ payload?: ComparisonPoint }>
  countries: ComparisonCountry[]
  unit: string
}) {
  const point = payload?.[0]?.payload
  if (!active || !point) return null
  return (
    <div className="max-w-80 border border-border bg-background px-3 py-2 text-xs leading-5 text-foreground">
      <p className="mb-1 font-medium">{point.period}</p>
      {countries.map(country => {
        const cell = point.cells[country.iso3Code]
        return <p key={country.iso3Code} className="flex flex-wrap justify-between gap-x-5">
          <span>{country.name}</span>
          <span className="tabular-nums">{formatValue(cell.value, unit)}</span>
          {cell.value === null && <span className="w-full text-[11px] text-muted-foreground">{comparisonStatus(cell.status)}</span>}
        </p>
      })}
    </div>
  )
}

export function ComparisonChart({ observations, countries, from, to, unit }: {
  observations: Observation[]
  countries: ComparisonCountry[]
  from: number
  to: number
  unit: string
}) {
  const codes = countries.map(country => country.iso3Code)
  const points = useMemo(
    () => buildComparisonPoints(observations, countries.map(country => country.iso3Code), from, to),
    [observations, countries, from, to],
  )
  const hasValues = points.some(point => codes.some(code => point.values[code] !== null))
  const step = Math.max(1, Math.ceil((to - from) / 5))
  const ticks = points.filter(point => point.period === from || point.period === to || (point.period - from) % step === 0).map(point => point.period)

  return (
    <div className="min-w-0">
      <ul aria-label="Legenda dos países" className="mb-5 flex flex-wrap gap-x-6 gap-y-2 text-xs">
        {countries.map((country, index) => (
          <li key={country.iso3Code} className="flex items-center gap-2">
            <svg width="26" height="12" aria-hidden="true" focusable="false">
              <line x1="0" y1="6" x2="26" y2="6" stroke={lineStyles[index].stroke} strokeDasharray={lineStyles[index].dash} strokeWidth="2" />
            </svg>
            {country.name}
          </li>
        ))}
      </ul>
      {hasValues ? <div
        className="h-80 min-w-0"
        role="group"
        aria-label={`Comparação anual entre ${countries.map(country => country.name).join(', ')}, de ${from} a ${to}. Unidade: ${unitLabel(unit)}. Dados disponíveis na tabela abaixo.`}
      >
        <ResponsiveContainer width="100%" height="100%" minWidth={0}>
          <LineChart data={points} margin={{ top: 12, right: 12, bottom: 8, left: 0 }} accessibilityLayer>
            <CartesianGrid vertical={false} stroke="var(--border)" strokeDasharray="2 4" />
            <XAxis dataKey="period" type="number" domain={from === to ? [from - .5, to + .5] : [from, to]} ticks={ticks} allowDecimals={false} axisLine={false} tickLine={false} tick={{ fill: 'var(--muted-foreground)', fontSize: 11 }} tickMargin={12} minTickGap={22} />
            <YAxis width={68} tickFormatter={value => formatAxisValue(Number(value), unit)} axisLine={false} tickLine={false} tick={{ fill: 'var(--muted-foreground)', fontSize: 11 }} tickMargin={8} domain={['auto', 'auto']} />
            <Tooltip content={<ComparisonTooltip countries={countries} unit={unit} />} filterNull={false} cursor={{ stroke: 'var(--muted-foreground)', strokeWidth: 1, strokeDasharray: '3 3' }} />
            {countries.map((country, index) => <Line
              key={country.iso3Code}
              name={country.name}
              type="linear"
              dataKey={`values.${country.iso3Code}`}
              stroke={lineStyles[index].stroke}
              strokeDasharray={lineStyles[index].dash}
              strokeWidth={1.8}
              connectNulls={false}
              dot={{ r: points.length <= 3 ? 3 : 1.2, strokeWidth: 0, fill: lineStyles[index].stroke }}
              activeDot={{ r: 4, strokeWidth: 2, stroke: 'var(--background)', fill: lineStyles[index].stroke }}
              isAnimationActive={false}
            />)}
          </LineChart>
        </ResponsiveContainer>
      </div> : <p role="status" className="border-y border-border py-10 text-sm leading-6 text-muted-foreground">
        Não há valores disponíveis para os países, a série e o período selecionados. Amplie o intervalo ou escolha outro indicador.
      </p>}
      <p className="mt-3 text-[11px] leading-5 text-muted-foreground">Cada linha preserva os anos disponíveis. Lacunas não são interpoladas nem substituídas por zero.</p>
      <details className="mt-4 border-y border-border py-3 text-xs">
        <summary className="w-fit cursor-pointer font-medium">Ver comparação em tabela</summary>
        <div className="mt-4 max-h-96 overflow-auto overscroll-contain" tabIndex={0} aria-label="Tabela comparativa anual, área de rolagem">
          <table className="w-full min-w-[32rem] border-collapse text-left text-xs">
            <caption className="sr-only">Comparação anual de {from} a {to}, em {unitLabel(unit)}. Valores ausentes são identificados separadamente por país.</caption>
            <thead className="sticky top-0 bg-background text-muted-foreground">
              <tr>
                <th scope="col" className="border-b border-border px-3 py-2 font-medium">Ano</th>
                {countries.map(country => <th key={country.iso3Code} scope="col" className="border-b border-border px-3 py-2 text-right font-medium">{country.name}</th>)}
              </tr>
            </thead>
            <tbody>{[...points].reverse().map(point => <tr key={point.period}>
              <th scope="row" className="border-b border-border px-3 py-3 align-top font-normal tabular-nums">{point.period}</th>
              {countries.map(country => {
                const cell = point.cells[country.iso3Code]
                return <td key={country.iso3Code} className="border-b border-border px-3 py-3 text-right align-top tabular-nums" title={comparisonStatus(cell.status)}>
                  <span className="whitespace-nowrap">{formatValue(cell.value, unit)}</span>
                  {cell.value === null && <span className="mt-1 block text-[10px] leading-4 text-muted-foreground">{comparisonStatus(cell.status)}</span>}
                </td>
              })}
            </tr>)}</tbody>
          </table>
        </div>
      </details>
    </div>
  )
}
