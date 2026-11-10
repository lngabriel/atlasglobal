import type { Country } from '@/services/api'

export function CountrySelection({ countries, codes, disabled, onChange, minimum = 2 }: {
  countries: Country[]
  codes: string[]
  disabled: boolean
  minimum?: number
  onChange: (codes: string[]) => void
}) {
  return (
    <fieldset className="min-w-0" disabled={disabled}>
      <legend className="text-xs font-semibold">Países e territórios</legend>
      <ul className="mt-3 flex flex-wrap gap-2" aria-label="Países selecionados para comparação">
        {codes.map(code => {
          const country = countries.find(item => item.iso3Code === code)
          return <li key={code} className="flex items-center gap-3 rounded-sm border border-border px-3 py-2 text-xs">
            <span>{country?.name ?? `${code} (não encontrado)`}</span>
            <button
              type="button"
              className="text-link text-[11px] text-muted-foreground disabled:opacity-40"
              aria-label={`Remover ${country?.name ?? code} da comparação`}
              disabled={codes.length <= minimum}
              onClick={() => onChange(codes.filter(item => item !== code))}
            >
              Remover
            </button>
          </li>
        })}
      </ul>
      <label htmlFor="comparison-country" className="control-label mt-4 block">
        Adicionar país
        <select
          id="comparison-country"
          className="field mt-1.5 block w-full sm:max-w-80"
          value=""
          disabled={disabled || codes.length >= 5}
          onChange={event => { if (event.target.value) onChange([...codes, event.target.value]) }}
        >
          <option value="">{codes.length >= 5 ? 'Limite de cinco países atingido' : 'Selecione um país ou território'}</option>
          {countries.filter(country => !codes.includes(country.iso3Code)).map(country => (
            <option key={country.iso3Code} value={country.iso3Code}>{country.name}</option>
          ))}
        </select>
      </label>
      <p className="mt-2 text-[11px] leading-5 text-muted-foreground">Selecione de {minimum} a 5 países para consultar o mesmo intervalo.</p>
    </fieldset>
  )
}
