import { useEffect, useState } from 'react'

type Theme = 'light' | 'dark'

export function useTheme() {
  const [theme, setTheme] = useState<Theme>(() => {
    try {
      const saved = localStorage.getItem('atlas-theme')
      if (saved === 'dark' || saved === 'light') return saved
    } catch { /* Storage may be disabled by the browser. */ }
    return matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  })

  useEffect(() => {
    document.documentElement.classList.toggle('dark', theme === 'dark')
    try { localStorage.setItem('atlas-theme', theme) } catch { /* The theme still works in memory. */ }
  }, [theme])

  return { theme, toggleTheme: () => setTheme(current => current === 'dark' ? 'light' : 'dark') }
}
