const regions: Record<string, string> = {
  'East Asia & Pacific': 'Ásia Oriental e Pacífico',
  'Europe & Central Asia': 'Europa e Ásia Central',
  'Latin America & Caribbean': 'América Latina e Caribe',
  'Middle East & North Africa': 'Oriente Médio e Norte da África',
  'Middle East, North Africa, Afghanistan & Pakistan': 'Oriente Médio, Norte da África, Afeganistão e Paquistão',
  'North America': 'América do Norte',
  'South Asia': 'Ásia Meridional',
  'Sub-Saharan Africa': 'África Subsaariana',
}
const incomes: Record<string, string> = {
  'High income': 'Renda alta',
  'Upper middle income': 'Renda média alta',
  'Lower middle income': 'Renda média baixa',
  'Low income': 'Renda baixa',
  'Not classified': 'Não classificado',
}
export const regionLabel = (value: string | null) => value ? regions[value] ?? value : 'Região não informada'
export const incomeLabel = (value: string | null) => value ? incomes[value] ?? value : 'Renda não informada'
export function countryFlag(iso2Code: string): string {
  return /^[A-Z]{2}$/.test(iso2Code.toUpperCase())
    ? String.fromCodePoint(...[...iso2Code.toUpperCase()].map(letter => 127397 + letter.charCodeAt(0))) : ''
}
