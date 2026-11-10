/** Provider classifications remain separate from whether a numeric observation is available. */
export function providerStatusLabel(status: string | null | undefined): string | null {
  if (!status) return null
  if (status === 'IMF_PROJECTION') return 'Projeção do FMI'
  if (status === 'IMF_HISTORICAL') return 'Histórico do FMI; pode conter estimativas'
  const flags: Record<string, string> = {
    p: 'Provisório', e: 'Estimativa da fonte', b: 'Quebra de série', d: 'Definição diferente',
    u: 'Baixa confiabilidade', r: 'Revisado', s: 'Estimativa do Eurostat', z: 'Não aplicável',
  }
  const codes = [...new Set(status.replaceAll(' ', '').split(''))]
  if (codes.length && codes.every(code => flags[code])) return codes.map(code => flags[code]).join(' · ')
  return null
}
