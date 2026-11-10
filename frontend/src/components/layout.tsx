import { ArrowUpRight, Moon, Sun } from 'lucide-react'
import { Link, NavLink, Outlet } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { useTheme } from '@/hooks/use-theme'

export function Layout() {
  const { theme, toggleTheme } = useTheme()
  return (
    <div className="flex min-h-screen flex-col bg-background text-foreground">
      <a className="skip-link" href="#content">Pular para o conteúdo</a>
      <header className="border-b border-border">
        <div className="page-shell grid min-h-16 grid-cols-[1fr_auto] items-center gap-x-5 gap-y-3 py-3 sm:grid-cols-[auto_1fr_auto]">
          <Link to="/" aria-label="Atlas Global, início" className="text-lg font-semibold tracking-tight sm:text-xl">atlas<span className="font-normal text-muted-foreground"> global</span><span className="ml-1 text-muted-foreground">.</span></Link>
          <nav aria-label="Navegação principal" className="col-span-2 row-start-2 flex min-w-0 flex-wrap items-center gap-x-4 gap-y-2 text-xs sm:col-span-1 sm:col-start-2 sm:row-start-1 sm:justify-self-end sm:gap-x-7">
            <NavLink to="/" end className={({ isActive }) => isActive ? 'font-semibold' : 'text-muted-foreground hover:text-foreground'}>Explorar</NavLink>
            <NavLink to="/comparar" className={({ isActive }) => isActive ? 'font-semibold' : 'text-muted-foreground hover:text-foreground'}>Comparar</NavLink>
            <NavLink to="/rankings" className={({ isActive }) => isActive ? 'font-semibold' : 'text-muted-foreground hover:text-foreground'}>Rankings</NavLink>
            <NavLink to="/juros" className={({ isActive }) => isActive ? 'font-semibold' : 'text-muted-foreground hover:text-foreground'}>Juros</NavLink>
            <NavLink to="/mapa" className={({ isActive }) => isActive ? 'font-semibold' : 'text-muted-foreground hover:text-foreground'}>Mapa</NavLink>
            <NavLink to="/sobre" className={({ isActive }) => isActive ? 'font-semibold' : 'text-muted-foreground hover:text-foreground'}>O projeto</NavLink>
          </nav>
          <Button className="col-start-2 row-start-1 sm:col-start-3" variant="ghost" size="icon" onClick={toggleTheme} aria-label={theme === 'dark' ? 'Ativar modo claro' : 'Ativar modo escuro'}>
            {theme === 'dark' ? <Sun size={16} aria-hidden="true" /> : <Moon size={16} aria-hidden="true" />}
          </Button>
        </div>
      </header>
      <main id="content" className="flex-1"><Outlet /></main>
      <footer className="mt-12 border-t border-border">
        <div className="page-shell flex flex-wrap justify-between gap-5 py-6 text-[11px] text-muted-foreground">
          <p>Atlas Global · Dados públicos para entender o mundo.</p>
          <div className="flex flex-wrap items-center gap-5">
            <a href="https://github.com/lngabriel/atlasglobal" target="_blank" rel="noreferrer" className="text-link inline-flex items-center gap-1">Código no GitHub<ArrowUpRight size={12} aria-hidden="true" /></a>
            <a href="https://data.worldbank.org/" target="_blank" rel="noreferrer" className="text-link inline-flex items-center gap-1">World Bank Data<ArrowUpRight size={12} aria-hidden="true" /></a>
            <a href="https://data-explorer.oecd.org/" target="_blank" rel="noreferrer" className="text-link inline-flex items-center gap-1">OECD Data<ArrowUpRight size={12} aria-hidden="true" /></a>
          </div>
        </div>
      </footer>
    </div>
  )
}
