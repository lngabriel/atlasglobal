export function AboutPage() {
  return <article className="mx-auto max-w-3xl px-6 py-12 sm:py-16">
    <p className="text-xs font-medium uppercase tracking-widest text-muted-foreground">Sobre o Atlas Global</p>
    <h1 className="editorial-title mt-4 text-3xl leading-tight sm:text-4xl">Dados públicos para ampliar a compreensão do mundo.</h1>
    <p className="mt-7 text-base leading-8 text-muted-foreground">O Atlas Global reúne indicadores internacionais em uma interface pública e gratuita. Explore a economia, a população, a saúde e o acesso à tecnologia de cada país, com os dados e seu contexto sempre próximos.</p>
    <section className="mt-10 border-t border-border pt-7"><h2 className="text-base font-semibold">Fontes identificadas, definições preservadas</h2>
      <p className="mt-3 text-sm leading-7 text-muted-foreground">Os indicadores anuais vêm do World Bank e da OCDE. Cada série preserva sua unidade, definição original, organização de origem e atualização, quando informada. Os destaques mundiais usam agregados oficiais, separados dos dados de países e territórios.</p>
      <a className="text-link mt-3 inline-block text-sm" href="https://datahelpdesk.worldbank.org/knowledgebase/articles/889392" target="_blank" rel="noreferrer">Consultar a documentação do World Bank ↗</a>
      <a className="text-link ml-5 mt-3 inline-block text-sm" href="https://www.oecd.org/en/data/insights/data-explainers/2024/09/api.html" target="_blank" rel="noreferrer">Consultar a documentação da OCDE ↗</a>
    </section>
    <section className="mt-8 border-t border-border pt-7"><h2 className="text-base font-semibold">Ausência também é informação</h2>
      <p className="mt-3 text-sm leading-7 text-muted-foreground">A disponibilidade varia por país, indicador e período. Valores ausentes nunca são substituídos por zero. Os gráficos preservam lacunas e as tabelas distinguem valores não informados pela fonte de anos sem registro importado. O último valor de uma série pode pertencer a um ano diferente do de outra.</p>
      <p className="mt-3 text-sm leading-7 text-muted-foreground">Comparações usam a mesma série e um ano comum. Pesquisas de renda e consumo podem ter coberturas diferentes, e taxas nacionais de pobreza não permitem comparação direta entre países. Salários em PPC preservam seu ano-base e a remuneração anual informada pela OCDE.</p>
    </section>
    <section className="mt-8 border-t border-border pt-7"><h2 className="text-base font-semibold">Transparência e acesso livre</h2>
      <p className="mt-3 text-sm leading-7 text-muted-foreground">O projeto tem código aberto e não exige cadastro. As consultas usam dados previamente coletados e armazenados; a data de coleta é exibida junto à atualização da fonte. Revisões na origem podem alterar valores históricos.</p>
      <div className="mt-4 flex flex-wrap gap-5 text-sm"><a className="text-link" href="https://github.com/lngabriel/atlasglobal" target="_blank" rel="noreferrer">Repositório no GitHub ↗</a><a className="text-link" href="/api/docs">Documentação da API ↗</a><a className="text-link" href="https://www.worldbank.org/en/about/legal/terms-of-use-for-datasets" target="_blank" rel="noreferrer">Termos de uso dos dados ↗</a></div>
    </section>
  </article>
}
