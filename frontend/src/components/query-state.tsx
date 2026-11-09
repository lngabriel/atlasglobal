import { Button } from '@/components/ui/button'
import { ApiError } from '@/services/api'

export function LoadingState({ label = 'Carregando dados…' }: { label?: string }) {
  return <div role="status" className="py-10 text-sm text-muted-foreground">{label}</div>
}

export function ErrorState({ error, retry }: { error: unknown; retry: () => void }) {
  return <div role="alert" className="border-y border-border py-6">
    <p className="text-sm font-medium">Não foi possível carregar os dados</p>
    <p className="mt-2 max-w-xl text-sm leading-6 text-muted-foreground">{error instanceof ApiError ? error.message : 'Ocorreu uma falha na consulta. Tente novamente.'}</p>
    <Button variant="outline" onClick={retry} className="mt-4">Tentar novamente</Button>
  </div>
}
