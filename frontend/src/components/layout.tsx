import { ArrowUpRight, Globe2, Moon, Sun } from 'lucide-react'
import { Link, NavLink, Outlet } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { useTheme } from '@/hooks/use-theme'

export function Layout() {
  const { theme, toggleTheme } = useTheme()
  return (
    <div className="min-h-screen bg-background text-foreground">
      <a className="skip-link" href="#content">Pular para o conteúdo</a>
      <header className="border-b border-border">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-4 px-6 py-5 lg:px-10">
          <Link to="/" className="flex items-center gap-3" aria-label="Atlas Global, início">
            <span className="grid size-10 place-items-center rounded-xl bg-primary text-primary-foreground"><Globe2 size={24} /></span>
            <span className="text-xl font-semibold tracking-tight">atlas<span className="font-normal text-muted-foreground"> global</span></span>
          </Link>
          <nav aria-label="Navegação principal" className="flex items-center gap-5 text-sm">
            <NavLink to="/" end className={({ isActive }) => isActive ? 'font-semibold text-primary' : 'text-muted-foreground'}>Início</NavLink>
            <NavLink to="/sobre" className={({ isActive }) => isActive ? 'font-semibold text-primary' : 'text-muted-foreground'}>O projeto</NavLink>
            <Button variant="ghost" size="icon" onClick={toggleTheme} aria-label={theme === 'dark' ? 'Ativar modo claro' : 'Ativar modo escuro'}>
              {theme === 'dark' ? <Sun size={19} /> : <Moon size={19} />}
            </Button>
          </nav>
        </div>
      </header>
      <main id="content"><Outlet /></main>
      <footer className="border-t border-border">
        <div className="mx-auto flex max-w-7xl flex-wrap justify-between gap-5 px-6 py-7 text-sm text-muted-foreground lg:px-10">
          <p>Atlas Global · Conhecimento público, acesso livre.</p>
          <a href="https://data.worldbank.org/" target="_blank" rel="noreferrer" className="inline-flex items-center gap-2 hover:text-foreground">Conheça o World Bank Data <ArrowUpRight size={15} /></a>
        </div>
      </footer>
    </div>
  )
}
