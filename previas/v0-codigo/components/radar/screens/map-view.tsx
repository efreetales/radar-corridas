'use client'

import { useEffect, useRef } from 'react'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

export type PointKind = 'valeta' | 'radar' | 'bom' | 'evento'
export type MapPoint = {
  id: string
  kind: PointKind
  lat: number
  lng: number
  label: string
}

const ICON: Record<PointKind, string> = {
  valeta: 'warning',
  radar: 'speed',
  bom: 'thumb_up',
  evento: 'celebration',
}

const TILES = {
  dark: 'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png',
  light: 'https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png',
}

export default function MapView({
  points,
  theme,
  selectedId,
  onSelect,
}: {
  points: MapPoint[]
  theme: 'dark' | 'light'
  selectedId: string | null
  onSelect: (id: string) => void
}) {
  const container = useRef<HTMLDivElement>(null)
  const mapRef = useRef<L.Map | null>(null)
  const tilesRef = useRef<L.TileLayer | null>(null)
  const layerRef = useRef<L.LayerGroup | null>(null)
  const firstSelect = useRef(true)

  useEffect(() => {
    if (!container.current) return
    const map = L.map(container.current, {
      zoomControl: false,
      attributionControl: false,
      zoomSnap: 0.5,
    }).setView([-23.565, -46.67], 12)

    L.control
      .attribution({ prefix: false })
      .addAttribution('© OpenStreetMap · © CARTO')
      .addTo(map)

    tilesRef.current = L.tileLayer(TILES[theme], {
      maxZoom: 19,
      subdomains: 'abcd',
    }).addTo(map)
    layerRef.current = L.layerGroup().addTo(map)
    mapRef.current = map
    const t = setTimeout(() => map.invalidateSize(), 60)

    return () => {
      clearTimeout(t)
      map.remove()
      mapRef.current = null
    }
    // O mapa é criado uma vez; o tema é tratado no efeito abaixo.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    tilesRef.current?.setUrl(TILES[theme])
  }, [theme])

  useEffect(() => {
    const layer = layerRef.current
    if (!layer) return
    layer.clearLayers()
    points.forEach((p) => {
      const selected = p.id === selectedId
      const icon = L.divIcon({
        className: '',
        iconSize: [44, 44],
        iconAnchor: [22, 22],
        html: `<div class="radar-pin radar-pin--${p.kind}${selected ? ' is-selected' : ''}"><span class="msr" style="font-size:24px;font-variation-settings:'FILL' 1,'wght' 600,'GRAD' 0,'opsz' 24">${ICON[p.kind]}</span></div>`,
      })
      const marker = L.marker([p.lat, p.lng], {
        icon,
        title: p.label,
        alt: p.label,
        keyboard: true,
      })
      if (p.kind === 'evento') marker.on('click', () => onSelect(p.id))
      marker.addTo(layer)
    })
  }, [points, selectedId, onSelect])

  useEffect(() => {
    if (firstSelect.current) {
      firstSelect.current = false
      return
    }
    const target = points.find((p) => p.id === selectedId)
    if (target && mapRef.current) {
      mapRef.current.flyTo([target.lat, target.lng], 13.5, { duration: 0.8 })
    }
    // Só reage à troca de seleção.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedId])

  return (
    <div
      ref={container}
      role="application"
      aria-label="Mapa com valetas, radares, pontos bons e eventos"
      className="size-full"
    />
  )
}
