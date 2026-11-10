import { useLayoutEffect, useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/services/api'
import { csvParameters, getCsv } from '@/services/exports'
import type { CsvSelection } from '@/services/exports'

export function CsvExportButton({ selection, disabled = false }: { selection: CsvSelection; disabled?: boolean }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const active = useRef<AbortController | null>(null)
  const selectionKey = JSON.stringify(selection)
  const currentSelectionKey = useRef(selectionKey)

  useLayoutEffect(() => {
    currentSelectionKey.current = selectionKey
    setBusy(false)
    setError(null)
    return () => { active.current?.abort(); active.current = null }
  }, [selectionKey])

  async function download() {
    const controller = new AbortController()
    active.current?.abort()
    active.current = controller
    setBusy(true)
    setError(null)
    try {
      const parameters = csvParameters(selection)
      const blob = await getCsv(selection, controller.signal)
      controller.signal.throwIfAborted()
      if (currentSelectionKey.current !== selectionKey) return
      const objectUrl = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = objectUrl
      link.download = `atlas-global-${parameters.get('indicator')}-${parameters.get('countries')!.replaceAll(',', '-')}-${selection.from}-${selection.to}.csv`
      document.body.appendChild(link)
      link.click()
      link.remove()
      setTimeout(() => URL.revokeObjectURL(objectUrl), 1_000)
    } catch (failure) {
      if (!controller.signal.aborted) setError(failure instanceof ApiError ? failure.message : 'Não foi possível exportar os dados. Tente novamente.')
    } finally {
      if (active.current === controller) { active.current = null; setBusy(false) }
    }
  }

  return <div className="min-w-0 text-right">
    <Button type="button" variant="outline" disabled={disabled || busy} aria-busy={busy} onClick={() => void download()}>
      {busy ? 'Preparando CSV…' : 'Exportar CSV'}
    </Button>
    {error && <p role="alert" className="mt-2 max-w-sm text-left text-xs leading-5 text-muted-foreground">{error}</p>}
  </div>
}
