import { useEffect, useRef, useState } from 'react'
import { ChevronDown, ChevronLeft, ChevronRight, Search } from 'lucide-react'
import { useWorkspaceParams } from '@/hooks/use-workspace-params'
import { useCountries, useCountryCatalog } from '@/hooks/use-atlas-data'
import { Button } from '@/components/ui/button'
import { ErrorState, LoadingState } from '@/components/query-state'
import { countryFlag, incomeLabel, regionLabel } from '@/lib/country-labels'

export function CountryBrowser({ selected, onSelect }: { selected: string; onSelect: (code: string) => void }) {
  const [params, setParams] = useWorkspaceParams()
  const [expanded, setExpanded] = useState(false)
  const toggle = useRef<HTMLButtonElement>(null)
  const [input, setInput] = useState(params.get('q') ?? '')
  const q = params.get('q')?.slice(0, 100) ?? ''
  const region = params.get('regiao') ?? ''
  const incomeLevel = params.get('renda') ?? ''
  const rawPage = Number(params.get('pagina') ?? 0)
  const page = Number.isInteger(rawPage) && rawPage >= 0 && rawPage <= 10000 ? rawPage : 0
  const countries = useCountries({ q, region, incomeLevel, page, size: 8 })
  const catalog = useCountryCatalog()
  const regions = [...new Set(catalog.data?.flatMap(country => country.region ? [country.region] : []) ?? [])].sort()
  const incomes = [...new Set(catalog.data?.flatMap(country => country.incomeLevel ? [country.incomeLevel] : []) ?? [])].sort()

  useEffect(() => {
    setInput(q)
  }, [q])

  useEffect(() => {
    if (input.trim() === q) return
    const timeout = setTimeout(() => setParams(previous => {
      const next = new URLSearchParams(previous)
      if (input.trim()) next.set('q', input.trim())
      else next.delete('q')
      next.delete('pagina')
      return next
    }, { replace: true }), 300)
    return () => clearTimeout(timeout)
  }, [input, q, setParams])

  function filter(key: string, value: string) {
    setParams(previous => {
      const next = new URLSearchParams(previous)
      if (value) next.set(key, value)
      else next.delete(key)
      if (key !== 'pagina') next.delete('pagina')
      return next
    }, { replace: true })
  }

  return <aside aria-label="Catálogo de países" className="min-w-0 lg:border-r lg:border-border lg:pr-7">
    <button
      ref={toggle}
      type="button"
      onClick={() => setExpanded(value => !value)}
      aria-expanded={expanded}
      aria-controls="country-search"
      className="flex w-full items-center justify-between border-y border-border py-3 text-sm font-medium lg:hidden"
    >
      Pesquisar países <ChevronDown size={16} className={expanded ? 'rotate-180' : ''} aria-hidden="true" />
    </button>
    <div id="country-search" className={`${expanded ? 'block' : 'hidden'} pb-6 pt-4 lg:block lg:py-0`}>
      <h2 className="mb-4 hidden text-xs font-semibold uppercase tracking-widest lg:block">
        Explorar países
      </h2>
      <label htmlFor="country-query" className="sr-only">Pesquisar por país ou código ISO</label>
      <div className="relative">
        <Search
          size={15}
          className="pointer-events-none absolute left-3 top-3 text-muted-foreground"
          aria-hidden="true"
        />
        <input
          id="country-query"
          type="search"
          maxLength={100}
          value={input}
          onChange={event => setInput(event.target.value)}
          placeholder="País ou código ISO"
          className="field w-full pl-9 pr-2"
          autoComplete="off"
        />
      </div>
      <div className="mt-4 grid grid-cols-2 gap-3 lg:grid-cols-1">
        <label className="control-label" htmlFor="country-region">Região
          <select
            id="country-region"
            className="field mt-1.5 w-full"
            value={region}
            onChange={event => filter('regiao', event.target.value)}
            disabled={!catalog.data}
          >
            <option value="">Todas as regiões</option>
            {region && !regions.includes(region) && <option value={region}>{regionLabel(region)}</option>}
            {regions.map(value => <option key={value} value={value}>{regionLabel(value)}</option>)}
          </select>
        </label>
        <label className="control-label" htmlFor="country-income">Grupo de renda
          <select
            id="country-income"
            className="field mt-1.5 w-full"
            value={incomeLevel}
            onChange={event => filter('renda', event.target.value)}
            disabled={!catalog.data}
          >
            <option value="">Todos os grupos</option>
            {incomeLevel && !incomes.includes(incomeLevel) && <option value={incomeLevel}>{incomeLabel(incomeLevel)}</option>}
            {incomes.map(value => <option key={value} value={value}>{incomeLabel(value)}</option>)}
          </select>
        </label>
      </div>
      {catalog.isError && <p className="mt-3 text-xs leading-5 text-muted-foreground">
        Filtros indisponíveis. <button onClick={() => void catalog.refetch()} className="text-link">Recarregar filtros</button>
      </p>}
      <div className="mt-6 border-t border-border pt-4">
        {countries.isPending ? (
          <LoadingState label="Consultando países…" />
        ) : countries.isError ? (
          <ErrorState error={countries.error} retry={() => void countries.refetch()} />
        ) : <>
          <p role="status" className="mb-3 text-xs text-muted-foreground">
            {countries.data.totalElements} {countries.data.totalElements === 1 ? 'país / economia' : 'países e economias'}
          </p>
          {countries.data.content.length ? <ul className="space-y-0.5">
            {countries.data.content.map(country => <li key={country.iso3Code}>
              <button
                type="button"
                aria-pressed={selected === country.iso3Code}
                onClick={() => {
                  onSelect(country.iso3Code)
                  setExpanded(false)
                  if (toggle.current?.getClientRects().length) toggle.current.focus()
                }}
                className={`flex min-h-10 w-full items-center gap-2.5 rounded-sm px-2 py-2 text-left text-sm hover:bg-muted ${selected === country.iso3Code ? 'bg-muted font-semibold' : ''}`}
              >
                <span aria-hidden="true" className="text-base">{countryFlag(country.iso2Code)}</span>
                <span className="min-w-0 flex-1 break-words">{country.name}</span>
                <span className="text-[10px] font-normal tracking-wide text-muted-foreground">{country.iso3Code}</span>
              </button>
            </li>)}
          </ul> : <div className="py-4 text-sm leading-6 text-muted-foreground">
            <p>Nenhum país encontrado. Tente outro nome ou remova os filtros.</p>
            {page > 0 && <button type="button" onClick={() => filter('pagina', '')} className="text-link mt-2 text-xs">
              Voltar à primeira página
            </button>}
          </div>}
          {(q || region || incomeLevel) && <button
            type="button"
            className="text-link mt-3 text-xs"
            onClick={() => {
              setInput('')
              setParams(previous => {
                const next = new URLSearchParams(previous);
                ['q', 'regiao', 'renda', 'pagina'].forEach(key => next.delete(key))
                return next
              }, { replace: true })
            }}
          >
            Limpar filtros
          </button>}
          {countries.data.totalPages > 1 && page < countries.data.totalPages && <div className="mt-4 flex items-center justify-between border-t border-border pt-3">
            <Button
              variant="ghost"
              size="icon"
              aria-label="Página anterior de países"
              disabled={page === 0}
              onClick={() => filter('pagina', String(page - 1))}
            >
              <ChevronLeft size={16} />
            </Button>
            <span className="text-xs text-muted-foreground">{page + 1} / {countries.data.totalPages}</span>
            <Button
              variant="ghost"
              size="icon"
              aria-label="Próxima página de países"
              disabled={page + 1 >= countries.data.totalPages}
              onClick={() => filter('pagina', String(page + 1))}
            >
              <ChevronRight size={16} />
            </Button>
          </div>}
        </>}
      </div>
      <p className="mt-6 text-xs leading-5 text-muted-foreground">
        O catálogo inclui países e economias. A cobertura dos indicadores varia por local e período.
      </p>
    </div>
  </aside>
}
