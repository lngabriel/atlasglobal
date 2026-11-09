import { useWorkspaceParams } from '@/hooks/use-workspace-params'
import { useCountryCatalog } from '@/hooks/use-atlas-data'
import { CountryBrowser } from '@/components/country-browser'
import { CountryDashboard } from '@/components/country-dashboard'

export function ExplorePage() {
  const [params, setParams] = useWorkspaceParams()
  const catalog = useCountryCatalog()
  const code = (params.get('pais')?.trim() || 'BRA').toUpperCase()

  function selectCountry(selected: string) {
    setParams(previous => {
      const next = new URLSearchParams(previous)
      next.set('pais', selected)
      return next
    }, { preventScrollReset: true })
  }

  return (
    <div className="page-shell py-7 lg:py-9">
      <section aria-labelledby="home-title" className="mb-7 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 id="home-title" className="editorial-title text-2xl sm:text-3xl">O mundo, em perspectiva.</h1>
          <p className="mt-2 text-sm leading-6 text-muted-foreground">Explore os dados que ajudam a entender cada país.</p>
        </div>
        <div className="text-[11px] text-muted-foreground">
          <p>Dados públicos. Acesso livre.</p>
          <nav aria-label="Países em destaque" className="mt-2 flex min-h-5 flex-wrap gap-3">
            {['BRA', 'USA', 'CHN'].flatMap(iso => {
              const country = catalog.data?.find(item => item.iso3Code === iso)
              return country ? [
                <button
                  key={iso}
                  type="button"
                  onClick={() => selectCountry(iso)}
                  className={`text-link ${code === iso ? 'font-medium text-foreground' : ''}`}
                >
                  {country.name}
                </button>,
              ] : []
            })}
          </nav>
        </div>
      </section>
      <div className="grid min-w-0 gap-7 lg:grid-cols-[220px_minmax(0,1fr)] lg:gap-9">
        <CountryBrowser selected={code} onSelect={selectCountry} />
        <CountryDashboard code={code} />
      </div>
    </div>
  )
}
