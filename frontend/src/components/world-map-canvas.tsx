import { useEffect, useMemo, useRef } from 'react'
import { GeoJSON, MapContainer, useMap } from 'react-leaflet'
import { CRS, type GeoJSON as LeafletGeoJSON, type Layer, type Path } from 'leaflet'
import type { Feature, Geometry } from 'geojson'
import type { Country } from '@/services/api'
import { countryForGeometry, type CountryGeometry, type WorldGeometry } from '@/lib/world-map'
import 'leaflet/dist/leaflet.css'
import './world-map-canvas.css'

const worldBounds: [[number, number], [number, number]] = [[-60, -180], [85, 180]]

function ViewControls() {
  const map = useMap()
  useEffect(() => {
    map.zoomControl.setPosition('topright')
    const container = map.getContainer()
    container.setAttribute('role', 'region')
    container.setAttribute('aria-label', 'Mapa mundial. Use os controles para ampliar e o seletor de países para navegar pelo teclado.')
    map.attributionControl.setPrefix(false)
    const observer = new ResizeObserver(() => {
      map.invalidateSize({ pan: false })
    })
    observer.observe(container)
    return () => observer.disconnect()
  }, [map])
  return <button type="button" className="absolute bottom-3 left-3 z-[500] rounded-sm border border-border bg-background px-3 py-2 text-[11px] hover:bg-muted"
    onClick={() => map.fitBounds(worldBounds, { animate: false, padding: [8, 8] })}>Visão mundial</button>
}

function Countries({ geometry, countries, selected, onSelect }: {
  geometry: WorldGeometry; countries: Country[]; selected: string; onSelect: (code: string) => void
}) {
  const layer = useRef<LeafletGeoJSON>(null)
  const catalog = useMemo(() => new Map(countries.map(country => [country.iso3Code, country])), [countries])
  useEffect(() => {
    layer.current?.eachLayer(item => {
      const feature = (item as Path & { feature?: CountryGeometry }).feature
      const code = feature ? countryForGeometry(feature, catalog)?.iso3Code : undefined
      ;(item as Path).getElement()?.setAttribute('aria-pressed', String(code === selected))
    })
  }, [selected, catalog])

  function bindCountry(feature: Feature<Geometry>, item: Layer) {
    const shape = feature as CountryGeometry
    const country = countryForGeometry(shape, catalog)
    const label = document.createElement('span')
    label.textContent = country?.name ?? `${shape.properties.NAME_PT || shape.properties.NAME} · fora do catálogo`
    item.bindTooltip(label, { sticky: true, opacity: 1 })
    const path = item as Path
    item.on('add', () => {
      const element = path.getElement()
      if (!element) return
      element.setAttribute('class', country ? 'leaflet-interactive map-country' : 'leaflet-interactive map-country-unavailable')
      if (!country) return
      element.setAttribute('data-country', country.iso3Code)
      element.setAttribute('role', 'button')
      element.setAttribute('tabindex', '0')
      element.setAttribute('aria-label', `Selecionar ${country.name}`)
      element.setAttribute('aria-pressed', String(country.iso3Code === selected))
      element.addEventListener('keydown', event => {
        if (!(event instanceof KeyboardEvent)) return
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault()
          event.stopPropagation()
          onSelect(country.iso3Code)
        }
      })
    })
    if (country) item.on('click', () => onSelect(country.iso3Code))
  }

  return <GeoJSON ref={layer} data={geometry} onEachFeature={bindCountry} />
}

export function WorldMapCanvas({ geometry, countries, selected, onSelect }: {
  geometry: WorldGeometry; countries: Country[]; selected: string; onSelect: (code: string) => void
}) {
  return <MapContainer className="world-map-canvas" crs={CRS.EPSG4326} bounds={worldBounds} boundsOptions={{ padding: [8, 8] }}
    minZoom={-2} maxZoom={6} zoomSnap={0.25} zoomDelta={0.5} maxBounds={[[-90, -210], [90, 210]]} maxBoundsViscosity={1}
    scrollWheelZoom={false} zoomControl={true} attributionControl={true}>
    <Countries geometry={geometry} countries={countries} selected={selected} onSelect={onSelect} />
    <ViewControls />
  </MapContainer>
}
