const number = (value: number, maximumFractionDigits = 2, minimumFractionDigits = 0) =>
  new Intl.NumberFormat('pt-BR', { maximumFractionDigits, minimumFractionDigits }).format(value)

const compactNumber = (value: number) => {
  const magnitude = Math.abs(value)
  for (const [threshold, suffix] of [[1e12, 'tri'], [1e9, 'bi'], [1e6, 'mi'], [1e3, 'mil']] as const) {
    if (magnitude >= threshold) return `${number(value / threshold)} ${suffix}`
  }
  return number(value)
}

export function unitLabel(unit: string): string {
  const units: Record<string, string> = {
    USD_CURRENT: 'US$ correntes',
    USD_CURRENT_PER_PERSON: 'US$ correntes por pessoa',
    PERCENT_ANNUAL: '% ao ano',
    PERSONS: 'pessoas',
    PERCENT_LABOR_FORCE: '% da força de trabalho',
    YEARS: 'anos',
    PERCENT_POPULATION: '% da população',
  }
  return units[unit] ?? unit
}

/** Missing and invalid numbers remain unavailable; they are never coerced to zero. */
export function formatValue(value: number | null, unit: string, compact = false): string {
  if (value === null || !Number.isFinite(value)) return '—'
  const amount = compact ? compactNumber(value) : number(value, unit === 'PERSONS' ? 0 : 2)
  if (unit === 'USD_CURRENT' || unit === 'USD_CURRENT_PER_PERSON') return `US$ ${amount}`
  if (unit.startsWith('PERCENT_')) return `${amount}%`
  if (unit === 'YEARS') return `${amount} anos`
  if (unit === 'PERSONS') return `${amount} pessoas`
  return `${amount}${unit ? ` ${unitLabel(unit)}` : ''}`
}

/** Axes use the chart's separate unit label to keep repeated ticks concise. */
export function formatAxisValue(value: number, unit: string): string {
  if (!Number.isFinite(value)) return '—'
  return unit.startsWith('PERCENT_') ? `${number(value)}%` : compactNumber(value)
}

export function formatDate(value: string | null): string {
  if (!value) return 'Não informada'
  const date = new Date(value)
  if (!Number.isFinite(date.getTime())) return 'Não informada'
  return new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: 'short', year: 'numeric', timeZone: 'UTC' }).format(date)
}

export function categoryLabel(category: string): string {
  const labels: Record<string, string> = {
    ECONOMY: 'Economia', DEMOGRAPHY: 'Demografia', EDUCATION: 'Educação',
    HEALTH: 'Saúde', ENVIRONMENT: 'Meio ambiente', TECHNOLOGY: 'Tecnologia',
  }
  return labels[category] ?? category
}
