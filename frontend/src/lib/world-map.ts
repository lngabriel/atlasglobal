import type { Feature, FeatureCollection, MultiPolygon, Polygon } from 'geojson'
import type { Country } from '../services/api.ts'

export interface CountryGeometryProperties {
  ADM0_A3: string
  ISO_A3: string
  ISO_A3_EH: string
  WB_A3: string
  NAME: string
  NAME_PT: string
}

export type CountryGeometry = Feature<Polygon | MultiPolygon, CountryGeometryProperties>
export type WorldGeometry = FeatureCollection<Polygon | MultiPolygon, CountryGeometryProperties>

/** Natural Earth's legacy World Bank identifier maps to the current catalog identity. */
const legacyWorldBankCodes: Record<string, string> = { KSV: 'XKX' }

export function countryForGeometry(feature: CountryGeometry, catalog: Map<string, Country>): Country | undefined {
  const properties = feature.properties
  for (const code of [properties.ISO_A3_EH, properties.ISO_A3, properties.WB_A3, properties.ADM0_A3]) {
    if (!/^[A-Z]{3}$/.test(code)) continue
    const country = catalog.get(code) ?? catalog.get(legacyWorldBankCodes[code])
    if (country) return country
  }
  return undefined
}

/** Reject malformed local geometry rather than drawing incomplete or unrelated shapes. */
export function parseWorldGeometry(value: unknown): WorldGeometry {
  const collection = value as WorldGeometry | null
  if (!collection || collection.type !== 'FeatureCollection' || !Array.isArray(collection.features)
    || collection.features.length === 0 || collection.features.length > 300) throw new Error('Invalid world geometry')
  let points = 0
  for (const feature of collection.features) {
    if (feature.type !== 'Feature' || !feature.properties || !feature.geometry
      || !['Polygon', 'MultiPolygon'].includes(feature.geometry.type)) throw new Error('Invalid country geometry')
    for (const key of ['ADM0_A3', 'ISO_A3', 'ISO_A3_EH', 'WB_A3', 'NAME', 'NAME_PT'] as const) {
      if (typeof feature.properties[key] !== 'string' || feature.properties[key].length > 200) throw new Error('Invalid country identity')
    }
    const polygons = feature.geometry.type === 'Polygon' ? [feature.geometry.coordinates] : feature.geometry.coordinates
    if (!Array.isArray(polygons) || polygons.length === 0) throw new Error('Missing polygon coordinates')
    for (const polygon of polygons) {
      if (!Array.isArray(polygon) || polygon.length === 0) throw new Error('Missing polygon rings')
      for (const ring of polygon) {
        if (!Array.isArray(ring) || ring.length < 4) throw new Error('Invalid polygon ring')
        for (const position of ring) {
          if (!Array.isArray(position) || position.length !== 2 || !Number.isFinite(position[0]) || !Number.isFinite(position[1])
            || Math.abs(position[0]) > 180 || Math.abs(position[1]) > 90 || ++points > 100_000) throw new Error('Invalid geographic coordinate')
        }
        const first = ring[0], last = ring[ring.length - 1]
        if (first[0] !== last[0] || first[1] !== last[1]) throw new Error('Unclosed polygon ring')
      }
    }
  }
  return collection
}
