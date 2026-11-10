declare const __ATLAS_API_ORIGIN__: string
declare const __ATLAS_API_TIMEOUT_MS__: number

// Vite replaces these constants. Node's service-contract tests retain local defaults.
const origin = typeof __ATLAS_API_ORIGIN__ === 'undefined' ? '' : __ATLAS_API_ORIGIN__
export const API_TIMEOUT_MS = typeof __ATLAS_API_TIMEOUT_MS__ === 'undefined' ? 15_000 : __ATLAS_API_TIMEOUT_MS__
export const API_DOCUMENTATION_URL = `${origin}/api/docs`

export function apiUrl(path: string): string {
  return `${origin}/api/v1${path}`
}
