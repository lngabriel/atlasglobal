import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'

export function NotFoundPage() {
  return <div className="mx-auto max-w-3xl px-6 py-24"><p className="text-sm text-primary">404</p><h1 className="mt-3 text-4xl font-semibold">Página não encontrada</h1><p className="my-6 text-muted-foreground">Este endereço não está disponível no Atlas Global.</p><Button asChild><Link to="/">Voltar ao início</Link></Button></div>
}
