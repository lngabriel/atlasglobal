/** Public build configuration only; database credentials never belong in Vite variables. */
export function apiOrigin(value: string | undefined): string {
  if (!value?.trim()) return ''
  const input = value.trim()
  let url: URL
  try { url = new URL(input) } catch { throw new Error('VITE_API_BASE_URL must be an HTTPS origin.') }
  if (!/^https:\/\/[^/?#\\\s]+\/?$/.test(input) || url.protocol !== 'https:' || url.username || url.password || url.search || url.hash
    || url.pathname !== '/' || input.includes('\\') || /[\r\n]/.test(value)) {
    throw new Error('VITE_API_BASE_URL must be an HTTPS origin without credentials, a path, query, or fragment.')
  }
  return url.origin
}

export function apiTimeout(value: string | undefined, external: boolean): number {
  if (!value?.trim()) return external ? 90_000 : 15_000
  if (!/^\d+$/.test(value) || Number(value) < 15_000 || Number(value) > 120_000) {
    throw new Error('VITE_API_TIMEOUT_MS must be an integer between 15000 and 120000.')
  }
  return Number(value)
}

export function pagesHeaders(origin: string): string {
  const allowedOrigin = apiOrigin(origin)
  if (!allowedOrigin) throw new Error('Cloudflare Pages requires VITE_API_BASE_URL.')
  return `/*
  X-Content-Type-Options: nosniff
  X-Frame-Options: DENY
  Referrer-Policy: strict-origin-when-cross-origin
  Permissions-Policy: camera=(), microphone=(), geolocation=()
  Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self' ${allowedOrigin}; object-src 'none'; base-uri 'self'; frame-ancestors 'none'

/assets/*
  Cache-Control: public, max-age=31536000, immutable

/geodata/*
  Cache-Control: public, max-age=86400

/geodata/*.geojson
  Content-Type: application/geo+json
`
}
