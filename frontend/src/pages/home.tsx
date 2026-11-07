import { Database } from 'lucide-react'

export function HomePage() {
  return (
    <div className="mx-auto max-w-7xl px-6 py-8 lg:px-10 lg:py-10">
      <section aria-labelledby="home-title">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <h1 id="home-title" className="text-2xl font-semibold tracking-tight sm:text-3xl">Indicadores globais</h1>
            <p className="mt-2 text-sm leading-6 text-muted-foreground">Dados públicos de países, reunidos em um só lugar.</p>
          </div>
          <span className="text-xs text-muted-foreground">Acesso livre e gratuito</span>
        </div>

        <div className="mt-7 rounded-xl border border-border">
          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border px-5 py-4">
            <h2 className="text-sm font-medium">Visão geral</h2>
            <span className="text-xs text-muted-foreground">Consulta indisponível</span>
          </div>
          <div className="flex flex-col items-center px-6 py-16 text-center sm:py-20">
            <Database size={24} strokeWidth={1.5} className="text-muted-foreground" aria-hidden="true" />
            <h3 className="mt-4 text-base font-medium">Os indicadores ainda não estão disponíveis</h3>
            <p className="mt-2 max-w-md text-sm leading-6 text-muted-foreground">A visualização de dados ainda não está disponível nesta interface. Conheça as fontes e os princípios do Atlas Global na página do projeto.</p>
          </div>
        </div>
      </section>
    </div>
  )
}
