import { Link } from 'react-router-dom'
import { useIndicator, useIndicators } from '@/hooks/use-atlas-data'
import { useRanking } from '@/hooks/use-ranking'
import { useWorkspaceParams } from '@/hooks/use-workspace-params'
import { ErrorState, LoadingState } from '@/components/query-state'
import { Button } from '@/components/ui/button'
import { categoryLabel, formatValue, unitLabel } from '@/lib/format'

const count = (value: number) => new Intl.NumberFormat('pt-BR').format(value)

function requestedYear(value: string | null) {
  const year = Number(value)
  return value && /^\d{4}$/.test(value) && year >= 1800 && year <= 2200 ? year : undefined
}

export function RankingsPage() {
  const [params, setParams] = useWorkspaceParams()
  const indicatorCode = (params.get('indicador')?.trim() || 'NY.GDP.MKTP.CD').toUpperCase()
  const indicators = useIndicators()
  const definition = useIndicator(indicatorCode)
  const series = definition.data?.series ?? []
  const requestedSeries = params.get('serie')
  const selected = requestedSeries ? series.find(item => item.id === requestedSeries) : series.length === 1 ? series[0] : undefined
  const allowed = selected?.comparison?.allowed !== false
  const year = requestedYear(params.get('ano'))
  const rawPage = Number(params.get('pagina') ?? 0)
  const page = Number.isInteger(rawPage) && rawPage >= 0 && rawPage <= 10000 ? rawPage : 0
  const direction = params.get('ordem') === 'asc' ? 'asc' : 'desc'
  const ranking = useRanking(selected && allowed ? indicatorCode : undefined, selected && allowed ? selected.id : undefined,
    { year, page, size: 20, direction })
  const categories = [...new Set(indicators.data?.map(item => item.category) ?? [])]
  const years = [...new Set([...(ranking.data?.availableYears ?? []), ...(year === undefined ? [] : [year])])]
    .sort((first, second) => second - first)
  const national = ['IBGE', 'BCB'].includes(selected?.source.code ?? '')
  let documentationUrl: string | undefined
  try {
    const candidate = new URL(selected?.source.documentationUrl ?? '')
    if (candidate.protocol === 'https:') documentationUrl = candidate.href
  } catch { /* Definition metadata remains available without a public documentation URL. */ }

  function filter(key: string, value: string) {
    setParams(previous => {
      const next = new URLSearchParams(previous)
      if (value) next.set(key, value); else next.delete(key)
      if (key === 'indicador') { next.delete('serie'); next.delete('ano') }
      if (key === 'serie') next.delete('ano')
      if (key !== 'pagina') next.delete('pagina')
      return next
    }, { preventScrollReset: true })
  }

  return <div className="page-shell py-7 lg:py-9">
    <nav aria-label="Caminho da página" className="mb-7 text-xs text-muted-foreground">
      <Link to="/" className="text-link">Explorar países</Link><span className="mx-3" aria-hidden="true">/</span>Rankings
    </nav>
    <header className="mb-7">
      <h1 className="editorial-title text-3xl sm:text-4xl">Um indicador. O mesmo ano.</h1>
      <p className="mt-3 max-w-2xl text-sm leading-6 text-muted-foreground">Ordene os países com dados disponíveis em uma única série, com a cobertura e a fonte identificadas.</p>
    </header>
    <section aria-label="Selecionar ranking" className="grid min-w-0 gap-4 border-y border-border py-6 sm:grid-cols-2 lg:grid-cols-[minmax(0,2fr)_minmax(0,2fr)_minmax(0,1fr)_minmax(0,1fr)]">
      {indicators.isPending ? <LoadingState label="Carregando indicadores…" /> : indicators.isError ? (
        <ErrorState error={indicators.error} retry={() => void indicators.refetch()} />
      ) : <label htmlFor="ranking-indicator" className="control-label min-w-0">
        Indicador
        <select id="ranking-indicator" className="field mt-1.5 block w-full" value={indicatorCode}
          onChange={event => filter('indicador', event.target.value)}>
          {!indicators.data.some(item => item.code === indicatorCode) && <option value={indicatorCode}>Indicador indisponível</option>}
          {categories.map(category => <optgroup key={category} label={categoryLabel(category)}>
            {indicators.data.filter(item => item.category === category).map(item => <option key={item.code} value={item.code}>{item.name}</option>)}
          </optgroup>)}
        </select>
      </label>}
      <label htmlFor="ranking-series" className="control-label min-w-0">
        Fonte e definição da série
        <select id="ranking-series" className="field mt-1.5 block w-full" value={selected?.id ?? ''}
          disabled={series.length === 0} onChange={event => filter('serie', event.target.value)}>
          {!selected && <option value="" disabled>{series.length ? 'Selecione uma definição' : 'Nenhuma série disponível'}</option>}
          {series.map(item => <option key={item.id} value={item.id}>{item.source.name} · {unitLabel(item.unit)}{series.length > 1 ? ` · ${item.datasetCode} · ${item.externalCode}` : ''}</option>)}
        </select>
      </label>
      <label htmlFor="ranking-year" className="control-label min-w-0">
        Ano de referência
        <select id="ranking-year" className="field mt-1.5 block w-full" value={year ?? ''}
          disabled={!selected || !allowed} onChange={event => filter('ano', event.target.value)}>
          <option value="">{year === undefined && ranking.data?.year ? `Último disponível (${ranking.data.year})` : 'Último disponível'}</option>
          {years.map(item => <option key={item} value={item}>{item}</option>)}
        </select>
      </label>
      <label htmlFor="ranking-direction" className="control-label min-w-0">
        Ordenação
        <select id="ranking-direction" className="field mt-1.5 block w-full" value={direction}
          disabled={!selected || !allowed} onChange={event => filter('ordem', event.target.value)}>
          <option value="desc">Maior para menor</option><option value="asc">Menor para maior</option>
        </select>
      </label>
    </section>

    {definition.isPending ? <LoadingState label="Carregando a definição do indicador…" /> : definition.isError ? (
      <div className="mt-6"><ErrorState error={definition.error} retry={() => void definition.refetch()} /></div>
    ) : selected ? <section aria-labelledby="ranking-title" className="mt-7 min-w-0">
      <div className="mb-4 flex flex-wrap items-baseline justify-between gap-3">
        <h2 id="ranking-title" className="text-lg font-semibold tracking-tight">{definition.data.indicator.name}</h2>
        <p className="text-xs text-muted-foreground">{unitLabel(selected.unit)} · {selected.source.name}</p>
      </div>
      <p className="mb-5 max-w-4xl text-xs leading-6 text-muted-foreground" role={!allowed ? 'status' : undefined}>
        <span className="font-medium text-foreground">Comparabilidade. </span>
        {selected.comparison?.note ?? 'A mesma série e unidade não eliminam diferenças de cobertura e metodologia. Consulte a definição antes de interpretar a ordenação.'}
      </p>
      {!allowed ? <div className="border-y border-border py-8 text-sm leading-6 text-muted-foreground">
        <p>Esta definição não permite ranking entre países. Consulte os históricos individuais para preservar seu contexto.</p>
        {national && <Link className="text-link mt-3 inline-block text-xs"
          to={{ pathname: '/paises/BRA', search: new URLSearchParams({ indicador: indicatorCode, serie: selected.id }).toString() }}>
          Consultar Brasil
        </Link>}
        {!national && <Link className="text-link mt-3 inline-block text-xs" to="/explorar">Consultar os perfis dos países</Link>}
      </div> : ranking.isPending ? <LoadingState label="Consultando o ranking…" /> : ranking.isError ? (
        <ErrorState error={ranking.error} retry={() => void ranking.refetch()} />
      ) : ranking.data ? <>
        <div id="ranking-coverage" className="mb-5 border-y border-border py-4 text-xs leading-6" role="status">
          <p className="font-medium">{ranking.data.coverage.withoutData > 0 ? 'Cobertura parcial' : 'Cobertura do catálogo'} · {count(ranking.data.coverage.withData)} de {count(ranking.data.coverage.totalCountries)} países e economias com valor{ranking.data.year === null ? '' : ` em ${ranking.data.year}`}.</p>
          <p className="text-muted-foreground">{count(ranking.data.coverage.withoutData)} sem valor disponível nesse ano e nesta série. A cobertura se limita aos países e economias do catálogo.</p>
        </div>
        {ranking.data.ranking.content.length ? <>
          <div className="overflow-x-auto" tabIndex={0} role="region" aria-label="Tabela do ranking">
            <table id="ranking-table" className="data-table w-full text-left text-xs">
              <caption className="sr-only">{definition.data.indicator.name} em {ranking.data.year}. {unitLabel(selected.unit)}. Fonte: {selected.source.name}. Somente países com valores nesse ano; empates compartilham a posição.</caption>
              <thead><tr><th scope="col" className="w-20">Posição</th><th scope="col">País / economia</th><th scope="col" className="text-right">Valor</th></tr></thead>
              <tbody>{ranking.data.ranking.content.map(entry => <tr key={entry.country.iso3Code}>
                <td className="tabular-nums text-muted-foreground">{entry.rank}</td>
                <th scope="row" className="font-normal">
                  <Link className="text-link font-medium" to={{ pathname: `/paises/${entry.country.iso3Code}`, search: new URLSearchParams({
                    indicador: indicatorCode, serie: selected.id, de: String(ranking.data!.year), ate: String(ranking.data!.year),
                  }).toString() }}>{entry.country.name}</Link>
                  <span className="ml-2 text-[10px] text-muted-foreground">{entry.country.iso3Code}</span>
                </th>
                <td className="text-right tabular-nums" title={formatValue(entry.value, selected.unit)}>{formatValue(entry.value, selected.unit, true)}</td>
              </tr>)}</tbody>
            </table>
          </div>
          <p className="mt-3 text-[11px] leading-5 text-muted-foreground">Valores iguais compartilham a posição. Nenhum valor de outro ano é utilizado para completar a lista.</p>
        </> : <div className="border-b border-border py-8 text-sm leading-6 text-muted-foreground">
          <p>{ranking.data.coverage.withData > 0 ? 'Esta página não contém resultados.' : 'Nenhum valor disponível para esta série e ano de referência.'}</p>
          {page > 0 && <Button className="mt-3" variant="outline" onClick={() => filter('pagina', '')}>Voltar à primeira página</Button>}
          {ranking.data.coverage.withData === 0 && <p className="mt-2 text-xs">Selecione outro ano ou indicador. A ausência de dados não representa zero.</p>}
        </div>}
        {ranking.data.ranking.totalPages > 1 && page < ranking.data.ranking.totalPages && <nav aria-label="Paginação do ranking" className="mt-5 flex flex-wrap items-center justify-between gap-3 border-t border-border pt-4">
          <Button variant="outline" disabled={page === 0} aria-label="Página anterior do ranking" onClick={() => filter('pagina', page === 1 ? '' : String(page - 1))}>Anterior</Button>
          <p className="text-xs text-muted-foreground">Página {page + 1} de {ranking.data.ranking.totalPages}</p>
          <Button variant="outline" disabled={page + 1 >= ranking.data.ranking.totalPages} aria-label="Próxima página do ranking" onClick={() => filter('pagina', String(page + 1))}>Próxima</Button>
        </nav>}
      </> : null}
      <details className="mt-7 border-y border-border py-3 text-xs">
        <summary className="cursor-pointer font-medium">Definição e procedência</summary>
        <div className="mt-4 space-y-4 break-words leading-6 text-muted-foreground">
          <p><span className="font-medium text-foreground">{selected.originalName}</span><br />{unitLabel(selected.unit)} · {selected.externalCode} · Conjunto {selected.datasetCode}</p>
          <p lang="en">{selected.methodology ?? definition.data.indicator.description ?? 'A fonte não informou uma definição para esta série.'}</p>
          {selected.sourceOrganization && <p><span className="font-medium text-foreground">Organização de origem</span><br /><span lang="en" className="whitespace-pre-line">{selected.sourceOrganization}</span></p>}
          {documentationUrl && <a href={documentationUrl} className="text-link inline-block" target="_blank" rel="noreferrer">Documentação da fonte</a>}
        </div>
      </details>
    </section> : <p role="status" className="py-8 text-sm leading-6 text-muted-foreground">{series.length ? 'Selecione uma definição da série para consultar o ranking. Fontes e unidades diferentes não são combinadas.' : 'Este indicador ainda não possui uma série disponível para ranking.'}</p>}
  </div>
}
