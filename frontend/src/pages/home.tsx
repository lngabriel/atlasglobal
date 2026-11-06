import { ArrowRight, Database, Globe2, Layers3, LockKeyholeOpen } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'

const principles = [
  { icon: Database, title: 'Dados com origem', text: 'Fontes públicas identificadas, com unidades, períodos e contexto para cada indicador.' },
  { icon: Globe2, title: 'Uma visão global', text: 'Uma base preparada para explorar países e entender suas diferentes realidades.' },
  { icon: LockKeyholeOpen, title: 'Acesso para todos', text: 'Uma plataforma gratuita e aberta, sem cadastro e sem barreiras para consultar.' },
]

export function HomePage() {
  return (
    <div className="mx-auto max-w-7xl px-6 lg:px-10">
      <section className="grid items-center gap-12 py-16 md:py-24 lg:grid-cols-[1.2fr_1fr]" aria-labelledby="home-title">
        <div>
          <p className="mb-6 flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.18em] text-primary"><span className="size-2 rounded-full bg-primary" /> Global Public Data Explorer</p>
          <h1 id="home-title" className="max-w-2xl text-5xl font-semibold leading-[1.08] tracking-tight md:text-7xl">O mundo em dados.<br /><span className="text-primary">Novas perspectivas.</span></h1>
          <p className="mt-7 max-w-lg text-lg leading-relaxed text-muted-foreground">Um espaço público para entender países, descobrir contextos e transformar informação em conhecimento.</p>
          <Button asChild className="mt-8"><Link to="/sobre">Conheça o Atlas Global <ArrowRight size={17} /></Link></Button>
          <p className="mt-5 text-sm text-muted-foreground">Gratuito. Público. Sem cadastro.</p>
        </div>
        <div className="globe-panel" role="img" aria-label="Ilustração abstrata de um globo, sem representação de dados">
          <div className="globe-orbit orbit-one" /><div className="globe-orbit orbit-two" />
          <div className="globe-sphere"><div className="globe-meridian" /><div className="globe-equator" /></div>
          <span className="globe-label">UM MUNDO. MUITAS PERSPECTIVAS.</span>
        </div>
      </section>
      <section className="mb-12 flex flex-wrap items-start gap-4 rounded-xl border border-border bg-muted/50 p-6" aria-labelledby="phase-title">
        <Layers3 className="mt-1 shrink-0 text-primary" size={22} />
        <div><h2 id="phase-title" className="font-semibold">Estamos construindo a base</h2><p className="mt-1 max-w-3xl text-sm leading-relaxed text-muted-foreground">Esta é a primeira etapa do Atlas Global. A infraestrutura e a interface inicial estão em preparação. Consultas de países e indicadores estarão disponíveis após a integração com fontes oficiais. Nenhum dado está sendo apresentado nesta versão.</p></div>
      </section>
      <section className="grid gap-8 border-t border-border py-12 md:grid-cols-3" aria-label="Princípios do projeto">
        {principles.map(({ icon: Icon, title, text }) => <article key={title}><Icon className="mb-4 text-primary" size={25} /><h2 className="text-lg font-semibold">{title}</h2><p className="mt-2 text-sm leading-7 text-muted-foreground">{text}</p></article>)}
      </section>
    </div>
  )
}
