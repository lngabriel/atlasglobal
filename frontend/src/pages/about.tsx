export function AboutPage() {
  return <article className="mx-auto max-w-3xl px-6 py-16 md:py-24">
    <p className="text-sm font-medium text-primary">Sobre o projeto</p>
    <h1 className="mt-4 text-4xl font-semibold tracking-tight">Dados públicos para ampliar a compreensão do mundo.</h1>
    <p className="mt-7 leading-8 text-muted-foreground">O Atlas Global está sendo desenvolvido para explorar indicadores internacionais de economia, demografia, educação, saúde, meio ambiente e tecnologia. O acesso será sempre público e gratuito.</p>
    <h2 className="mt-10 text-xl font-semibold">Fontes públicas e oficiais</h2>
    <p className="mt-3 leading-8 text-muted-foreground">O Atlas Global utiliza o World Bank como fonte de indicadores anuais. As séries preservam as definições originais, as unidades e a data de atualização da fonte. A disponibilidade varia conforme o país e o indicador.</p>
    <h2 className="mt-10 text-xl font-semibold">Compromisso com o contexto</h2>
    <p className="mt-3 leading-8 text-muted-foreground">Fonte, unidade e período são essenciais para interpretar um indicador. Valores ausentes são preservados como indisponíveis e nunca substituídos por zero.</p>
  </article>
}
