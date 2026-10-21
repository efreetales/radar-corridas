'use client'

import { useMemo, useState } from 'react'
import dynamic from 'next/dynamic'
import { cn } from '@/lib/utils'
import { Icon } from '../icon'
import {
  ActionButton,
  ErrorState,
  ScreenHeader,
  Skeleton,
  StateMessage,
} from '../ui'
import type { ScreenProps } from '../config'
import type { MapPoint, PointKind } from './map-view'

function MapLoading() {
  return (
    <div
      role="status"
      className="grid size-full place-items-center bg-surface-2 text-lg font-bold"
    >
      <span className="flex items-center gap-3">
        <Icon name="progress_activity" size={32} className="animate-spin text-accent-text" />
        Carregando o mapa…
      </span>
    </div>
  )
}

const MapView = dynamic(() => import('./map-view'), {
  ssr: false,
  loading: () => <MapLoading />,
})

const EVENTS = [
  { id: 'e1', icon: 'music_note', name: 'Show de rock', place: 'Allianz Parque', time: 'Saída 22:30', dist: '6,1 km', lat: -23.5275, lng: -46.6785 },
  { id: 'e2', icon: 'sports_soccer', name: 'Jogo de futebol', place: 'Estádio do Morumbi', time: 'Fim 21:45', dist: '9,4 km', lat: -23.6001, lng: -46.7222 },
  { id: 'e3', icon: 'celebration', name: 'Show de pop', place: 'Ginásio do Ibirapuera', time: 'Saída 23:15', dist: '3,2 km', lat: -23.5874, lng: -46.6576 },
]

const OTHER_POINTS: MapPoint[] = [
  { id: 'v1', kind: 'valeta', lat: -23.556, lng: -46.67, label: 'Valeta na Av. Rebouças' },
  { id: 'v2', kind: 'valeta', lat: -23.5785, lng: -46.689, label: 'Buraco na Marginal Pinheiros' },
  { id: 'v3', kind: 'valeta', lat: -23.592, lng: -46.664, label: 'Valeta na Av. 23 de Maio' },
  { id: 'r1', kind: 'radar', lat: -23.564, lng: -46.654, label: 'Radar na Av. Paulista, 50 km/h' },
  { id: 'r2', kind: 'radar', lat: -23.547, lng: -46.6395, label: 'Radar no centro, 40 km/h' },
  { id: 'r3', kind: 'radar', lat: -23.61, lng: -46.699, label: 'Radar na Av. Roberto Marinho, 70 km/h' },
  { id: 'b1', kind: 'bom', lat: -23.5705, lng: -46.69, label: 'Ponto bom: Pinheiros' },
  { id: 'b2', kind: 'bom', lat: -23.5505, lng: -46.6333, label: 'Ponto bom: Sé' },
]

const LEGEND: { kind: PointKind; label: string; icon: string; cls: string }[] = [
  { kind: 'valeta', label: 'Valeta', icon: 'warning', cls: 'bg-warn' },
  { kind: 'radar', label: 'Radar', icon: 'speed', cls: 'bg-bad' },
  { kind: 'bom', label: 'Ponto bom', icon: 'thumb_up', cls: 'bg-good' },
  { kind: 'evento', label: 'Evento', icon: 'celebration', cls: 'bg-foreground text-background' },
]

export function MapScreen({ variant, retry, resolvedTheme }: ScreenProps) {
  const [selectedId, setSelectedId] = useState<string>('e1')
  const empty = variant === 'vazio'
  const selected = EVENTS.find((e) => e.id === selectedId)

  const points = useMemo<MapPoint[]>(() => {
    if (empty) return OTHER_POINTS.filter((p) => p.kind === 'radar')
    return [
      ...OTHER_POINTS,
      ...EVENTS.map((e) => ({
        id: e.id,
        kind: 'evento' as const,
        lat: e.lat,
        lng: e.lng,
        label: `${e.name} · ${e.place}`,
      })),
    ]
  }, [empty])

  const showMap = variant === 'normal' || empty
  const canNavigate = showMap && !empty && !!selected

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <ScreenHeader title="Mapa" subtitle="Perto de você, em São Paulo" />

      <div className="min-h-0 flex-1 overflow-y-auto px-4 pb-4">
        {variant === 'erro' && (
          <ErrorState
            title="Não deu para carregar o mapa"
            text="Confira a internet. Os avisos de radar continuam funcionando."
            onRetry={retry}
          />
        )}

        {variant === 'carregando' && (
          <div className="flex flex-col gap-4">
            <div className="h-64 overflow-hidden rounded-3xl border border-border">
              <MapLoading />
            </div>
            <Skeleton className="h-[72px]" />
            <Skeleton className="h-[72px]" />
          </div>
        )}

        {showMap && (
          <>
            <div className="relative h-64 overflow-hidden rounded-3xl border border-border">
              <MapView
                points={points}
                theme={resolvedTheme}
                selectedId={canNavigate ? selectedId : null}
                onSelect={setSelectedId}
              />
              <ul
                aria-label="Legenda do mapa"
                className="pointer-events-none absolute inset-x-2 top-2 z-[500] flex flex-wrap gap-1.5"
              >
                {LEGEND.map((l) => (
                  <li
                    key={l.kind}
                    className="inline-flex min-h-9 items-center gap-1.5 rounded-full border border-border bg-card py-1 pl-1.5 pr-3 text-base font-bold text-card-foreground shadow"
                  >
                    <span className={cn('grid size-6 place-items-center rounded-full text-on-verdict', l.cls)}>
                      <Icon name={l.icon} size={16} fill />
                    </span>
                    {l.label}
                  </li>
                ))}
              </ul>
            </div>

            {empty && (
              <StateMessage
                className="mt-4"
                icon="add_location_alt"
                title="Nenhuma valeta marcada ainda"
                text="Viu uma valeta ou buraco? Marque para avisar os outros motoristas."
                action={
                  <ActionButton variant="secondary" icon="add_location_alt">
                    Marcar valeta aqui
                  </ActionButton>
                }
              />
            )}

            <section aria-labelledby="eventos" className="mt-5">
              <h2 id="eventos" className="px-1 pb-3 text-2xl font-bold">
                Eventos de hoje
              </h2>
              {empty ? (
                <StateMessage
                  icon="event_busy"
                  title="Nenhum evento hoje"
                  text="Quando houver show ou jogo por perto, ele aparece aqui."
                />
              ) : (
                <ul className="flex flex-col gap-3">
                  {EVENTS.map((e) => {
                    const active = e.id === selectedId
                    return (
                      <li key={e.id}>
                        <button
                          type="button"
                          aria-pressed={active}
                          onClick={() => setSelectedId(e.id)}
                          className={cn(
                            'flex min-h-[76px] w-full items-center gap-3 rounded-2xl border-2 bg-card px-3 py-3 text-left focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent-text',
                            active ? 'border-primary' : 'border-border',
                          )}
                        >
                          <span className="grid size-12 shrink-0 place-items-center rounded-full bg-surface-2">
                            <Icon name={e.icon} size={26} />
                          </span>
                          <span className="min-w-0 flex-1">
                            <span className="block text-lg font-bold leading-snug">
                              {e.name}
                            </span>
                            <span className="block text-base text-muted-foreground">
                              {e.place} · {e.time}
                            </span>
                          </span>
                          {active ? (
                            <span className="inline-flex items-center gap-1 rounded-full bg-primary px-3 py-1 text-base font-bold text-primary-foreground">
                              <Icon name="check" size={18} />
                              Destino
                            </span>
                          ) : (
                            <span className="text-base font-bold tabular-nums">
                              {e.dist}
                            </span>
                          )}
                        </button>
                      </li>
                    )
                  })}
                </ul>
              )}
            </section>
          </>
        )}
      </div>

      {showMap && (
        <div className="shrink-0 border-t border-border bg-card px-4 pb-3 pt-3">
          <p className="mb-2 flex items-center gap-2 text-lg">
            <Icon name="pin_drop" size={22} />
            {canNavigate ? (
              <span className="truncate">
                <span className="font-bold">{selected?.place}</span> · {selected?.time}
              </span>
            ) : (
              <span className="text-muted-foreground">Escolha um evento para navegar</span>
            )}
          </p>
          <div className="grid grid-cols-2 gap-3">
            <ActionButton
              icon="navigation"
              size="lg"
              disabled={!canNavigate}
              className="px-2"
            >
              Ir com Waze
            </ActionButton>
            <ActionButton
              variant="secondary"
              icon="local_taxi"
              size="lg"
              disabled={!canNavigate}
              className="px-2"
            >
              Ir com Uber
            </ActionButton>
          </div>
        </div>
      )}
    </div>
  )
}
