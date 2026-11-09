import { useEffect, useState } from 'react'
import { Button } from '@/components/ui/button'

export const currentYear = new Date().getUTCFullYear()

export function queryYear(value: string | null, fallback: number) {
  const parsed = Number(value)
  return value && /^\d{4}$/.test(value) && parsed >= 1800 && parsed <= currentYear ? parsed : fallback
}

export function PeriodFilter({ from, to, onChange }: {
  from: number
  to: number
  onChange: (from: number, to: number) => void
}) {
  const [start, setStart] = useState(String(from))
  const [end, setEnd] = useState(String(to))
  const [error, setError] = useState('')

  useEffect(() => {
    setStart(String(from))
    setEnd(String(to))
    setError('')
  }, [from, to])

  return (
    <div className="flex flex-wrap items-start justify-between gap-3">
      <div aria-label="Períodos rápidos" className="flex gap-1">
        {[10, 20, null].map(years => {
          const beginning = years ? currentYear - years + 1 : 1960
          const active = from === beginning && to === currentYear
          return (
            <button
              key={years ?? 'all'}
              type="button"
              aria-pressed={active}
              className={`rounded-sm px-2.5 py-2 text-xs ${active ? 'bg-foreground text-background' : 'text-muted-foreground hover:bg-muted'}`}
              onClick={() => onChange(beginning, currentYear)}
            >
              {years ? `${years} anos` : 'Desde 1960'}
            </button>
          )
        })}
      </div>
      <form className="flex flex-wrap items-end gap-2" onSubmit={event => {
        event.preventDefault()
        const first = Number(start), last = Number(end)
        if (!/^\d{4}$/.test(start) || !/^\d{4}$/.test(end) || first < 1800 || last > currentYear || first > last) {
          setError(`Informe um intervalo válido entre 1800 e ${currentYear}.`)
          return
        }
        setError('')
        onChange(first, last)
      }}>
        <label htmlFor="period-from" className="control-label">
          De
          <input id="period-from" className="field mt-1 block w-20" type="number" min={1800} max={currentYear} step={1} required value={start} onChange={event => setStart(event.target.value)} aria-invalid={Boolean(error)} aria-describedby={error ? 'period-error' : undefined} />
        </label>
        <label htmlFor="period-to" className="control-label">
          Até
          <input id="period-to" className="field mt-1 block w-20" type="number" min={1800} max={currentYear} step={1} required value={end} onChange={event => setEnd(event.target.value)} aria-invalid={Boolean(error)} aria-describedby={error ? 'period-error' : undefined} />
        </label>
        <Button type="submit" variant="outline">Aplicar</Button>
        {error && <p id="period-error" role="alert" className="w-full text-xs leading-5">{error}</p>}
      </form>
    </div>
  )
}
