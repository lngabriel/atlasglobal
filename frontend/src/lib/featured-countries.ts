/** Editorial selection; names follow the product's Portuguese country labels. */
export const featuredCountries = [
  { code: 'ARG', iso2: 'AR', name: 'Argentina' },
  { code: 'AUS', iso2: 'AU', name: 'Austrália' },
  { code: 'BRA', iso2: 'BR', name: 'Brasil' },
  { code: 'CAN', iso2: 'CA', name: 'Canadá' },
  { code: 'CHL', iso2: 'CL', name: 'Chile' },
  { code: 'CHN', iso2: 'CN', name: 'China' },
  { code: 'FRA', iso2: 'FR', name: 'França' },
  { code: 'HKG', iso2: 'HK', name: 'Hong Kong' },
  { code: 'ISR', iso2: 'IL', name: 'Israel' },
  { code: 'ITA', iso2: 'IT', name: 'Itália' },
  { code: 'JPN', iso2: 'JP', name: 'Japão' },
  { code: 'PRY', iso2: 'PY', name: 'Paraguai' },
  { code: 'POL', iso2: 'PL', name: 'Polônia' },
  { code: 'PRT', iso2: 'PT', name: 'Portugal' },
  { code: 'RUS', iso2: 'RU', name: 'Rússia' },
  { code: 'SGP', iso2: 'SG', name: 'Singapura' },
  { code: 'KOR', iso2: 'KR', name: 'Coreia do Sul' },
  { code: 'ESP', iso2: 'ES', name: 'Espanha' },
  { code: 'CHE', iso2: 'CH', name: 'Suíça' },
  { code: 'TWN', iso2: 'TW', name: 'Taiwan' },
  { code: 'UKR', iso2: 'UA', name: 'Ucrânia' },
  { code: 'GBR', iso2: 'GB', name: 'Reino Unido' },
  { code: 'USA', iso2: 'US', name: 'Estados Unidos' },
  { code: 'VEN', iso2: 'VE', name: 'Venezuela' },
  { code: 'VNM', iso2: 'VN', name: 'Vietnã' },
] as const

export function searchFeaturedCountries(query: string) {
  const normalize = (text: string) => text.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLocaleLowerCase('pt-BR')
  const term = normalize(query.trim())
  return featuredCountries.filter(country => normalize(`${country.name} ${country.code} ${country.iso2}`).includes(term))
}
