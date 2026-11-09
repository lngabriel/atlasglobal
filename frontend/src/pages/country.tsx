import { Link, useParams } from 'react-router-dom'
import { CountryDashboard } from '@/components/country-dashboard'

export function CountryPage() {
  const { iso3Code = '' } = useParams()
  return <div className="page-shell py-7 lg:py-9">
    <nav aria-label="Caminho da página" className="mb-7 text-xs text-muted-foreground"><Link to="/" className="text-link">Explorar países</Link><span className="mx-3" aria-hidden="true">/</span>Perfil do país</nav>
    <div className="mx-auto max-w-5xl"><CountryDashboard code={iso3Code.toUpperCase()} standalone /></div>
  </div>
}
