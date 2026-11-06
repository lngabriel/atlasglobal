export function AboutPage() {
  return <article className="mx-auto max-w-3xl px-6 py-16 md:py-24">
    <p className="text-sm font-medium text-primary">Sobre o projeto</p>
    <h1 className="mt-4 text-4xl font-semibold tracking-tight">Dados públicos para ampliar a compreensão do mundo.</h1>
    <p className="mt-7 leading-8 text-muted-foreground">O Atlas Global está sendo desenvolvido para explorar indicadores internacionais de economia, demografia, educação, saúde, meio ambiente e tecnologia. O acesso será sempre público e gratuito.</p>
    <h2 className="mt-10 text-xl font-semibold">Primeira etapa: fundação</h2>
    <p className="mt-3 leading-8 text-muted-foreground">Esta versão estabelece a arquitetura, o banco de dados e a interface. Ainda não há indicadores importados. A próxima etapa, sujeita à aprovação, será a integração real com o World Bank.</p>
    <h2 className="mt-10 text-xl font-semibold">Compromisso com o contexto</h2>
    <p className="mt-3 leading-8 text-muted-foreground">Cada dado terá sua fonte, unidade e período identificados. Ausências serão informadas e as comparações respeitarão as definições dos indicadores.</p>
  </article>
}
