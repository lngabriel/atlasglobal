import { providerStatusLabel } from '@/lib/provider-status'

export function ObservationNote({ status }: { status: string | null | undefined }) {
  const label = providerStatusLabel(status)
  return label ? <span className="mt-1 block text-[11px] font-normal leading-5 text-muted-foreground">{label}</span> : null
}
