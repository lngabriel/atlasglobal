import { Link } from 'react-router-dom'
import { Search } from 'lucide-react'
import { useWorkspaceParams } from '@/hooks/use-workspace-params'
import { WorldOverview } from '@/components/world-overview'
import { featuredCountries, searchFeaturedCountries } from '@/lib/featured-countries'

export function HomePage() {
  const [params, update] = useWorkspaceParams()
  const query = params.get('q')?.slice(0, 100) ?? ''
  const countries = searchFeaturedCountries(query)

  return <div className="page-shell py-8 lg:py-10">
    <header className="mb-9 flex flex-wrap items-end justify-between gap-5">
      <div>
        <p className="mb-3 text-[11px] uppercase tracking-widest text-muted-foreground">Dados públicos · Acesso livre</p>
        <h1 id="home-title" className="editorial-title text-3xl sm:text-4xl">O mundo, em perspectiva.</h1>
        <p className="mt-3 text-sm leading-6 text-muted-foreground">Indicadores socioeconômicos, fontes oficiais e séries históricas.</p>
      </div>
      <Link to="/comparar" className="inline-flex h-9 items-center rounded-sm border border-border px-4 text-xs font-medium hover:bg-muted">Comparar países</Link>
    </header>
    <WorldOverview />
    <section aria-labelledby="countries-title">
      <div className="mb-5 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h2 id="countries-title" className="text-lg font-semibold tracking-tight">Países e territórios</h2>
          <p role="status" className="mt-1 text-xs text-muted-foreground">{query ? `${countries.length} de ${featuredCountries.length} locais` : `${featuredCountries.length} locais para explorar`}</p>
        </div>
        <div className="relative w-full sm:w-72">
          <label htmlFor="home-country-query" className="sr-only">Pesquisar nos países e territórios selecionados</label>
          <Search size={15} className="pointer-events-none absolute left-3 top-3 text-muted-foreground" aria-hidden="true" />
          <input id="home-country-query" type="search" maxLength={100} autoComplete="off" placeholder="Nome ou código ISO" className="field w-full pl-9" value={query} onChange={event => {
            const value = event.target.value
            update(previous => {
              const next = new URLSearchParams(previous)
              if (value) next.set('q', value); else next.delete('q')
              return next
            }, { replace: true })
          }} />
        </div>
      </div>
      <div id="featured-countries" className="grid grid-cols-2 gap-x-6 border-t border-border md:grid-cols-3 xl:grid-cols-5">
        {countries.map(country => <Link key={country.code} data-country={country.code} to={`/paises/${country.code}`} className="group flex min-h-19 items-center justify-between gap-2 border-b border-border py-4 text-sm hover:bg-muted/50">
          <span className="font-medium group-hover:underline group-hover:underline-offset-4">{country.name}</span>
          <span className="text-[10px] tracking-wide text-muted-foreground">{country.code}</span>
        </Link>)}
      </div>
      {countries.length === 0 && <p className="border-b border-border py-8 text-sm text-muted-foreground">Nenhum local encontrado nesta seleção.</p>}
      <div className="mt-5 flex flex-wrap justify-between gap-3 text-xs text-muted-foreground">
        <p>A disponibilidade varia conforme o país, a série e o período.</p>
        <Link to={{ pathname: '/explorar', search: query ? `?q=${encodeURIComponent(query)}` : '' }} className="text-link">Explorar o catálogo completo</Link>
      </div>
    </section>
  </div>
}
