'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { cn } from '@/lib/utils'
import { Icon } from './icon'
import { PhoneFrame, PHONE_H, PHONE_W } from './phone-frame'
import { BottomNav } from './bottom-nav'
import { OfferScreen } from './screens/offer-screen'
import { HomeScreen } from './screens/home-screen'
import { SettingsScreen } from './screens/settings-screen'
import { EarningsScreen } from './screens/earnings-screen'
import { MapScreen } from './screens/map-screen'
import { BubbleScreen } from './screens/bubble-screen'
import { AlertsScreen } from './screens/alerts-screen'
import {
  NOTES,
  PRINCIPLES,
  SCREENS,
  VARIANTS,
  defaultVariant,
  type ScreenId,
  type ScreenProps,
  type ThemeMode,
} from './config'

const IN_APP: ScreenId[] = ['inicio', 'ganhos', 'mapa', 'ajustes']

function useFitScale() {
  const [scale, setScale] = useState(1)
  useEffect(() => {
    const calc = () => {
      const w = window.innerWidth
      const h = window.innerHeight
      const byWidth = (w - 32) / PHONE_W
      const byHeight = (h - 112) / PHONE_H
      setScale(Math.max(0.5, Math.min(1, w >= 1024 ? byHeight : byWidth)))
    }
    calc()
    window.addEventListener('resize', calc)
    return () => window.removeEventListener('resize', calc)
  }, [])
  return scale
}

function noteKey(screen: ScreenId) {
  return screen === 'inicio' || screen === 'ajustes' ? 'menu' : screen
}

export function Prototype() {
  const [screen, setScreen] = useState<ScreenId>('oferta')
  const [variant, setVariant] = useState('bom')
  const [radarOn, setRadarOn] = useState(true)
  const [themeMode, setThemeMode] = useState<ThemeMode>('dark')
  const [systemDark, setSystemDark] = useState(true)
  const retryTimer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const scale = useFitScale()

  const resolvedTheme: 'dark' | 'light' =
    themeMode === 'auto' ? (systemDark ? 'dark' : 'light') : themeMode

  useEffect(() => {
    const mq = window.matchMedia('(prefers-color-scheme: dark)')
    setSystemDark(mq.matches)
    const onChange = (e: MediaQueryListEvent) => setSystemDark(e.matches)
    mq.addEventListener('change', onChange)
    return () => mq.removeEventListener('change', onChange)
  }, [])

  useEffect(() => {
    document.documentElement.classList.toggle('dark', resolvedTheme === 'dark')
  }, [resolvedTheme])

  useEffect(
    () => () => {
      if (retryTimer.current) clearTimeout(retryTimer.current)
    },
    [],
  )

  const clearRetry = () => {
    if (retryTimer.current) clearTimeout(retryTimer.current)
  }

  const go = useCallback(
    (next: ScreenId) => {
      clearRetry()
      setScreen(next)
      setVariant(defaultVariant(next, radarOn))
    },
    [radarOn],
  )

  const selectVariant = useCallback(
    (v: string) => {
      clearRetry()
      if (screen === 'inicio' && (v === 'ativa' || v === 'desligada')) {
        setRadarOn(v === 'ativa')
      }
      if (screen === 'bolinha') setRadarOn(v === 'ligado')
      setVariant(v)
    },
    [screen],
  )

  const toggleRadar = useCallback(() => setRadarOn((on) => !on), [])

  // Início e Bolinha espelham o mesmo interruptor global do radar.
  const shownVariant = (() => {
    if (screen === 'inicio' && (variant === 'ativa' || variant === 'desligada')) {
      return radarOn ? 'ativa' : 'desligada'
    }
    if (screen === 'bolinha' && variant !== 'aviso') {
      return radarOn ? 'ligado' : 'desligado'
    }
    return variant
  })()

  const retry = useCallback(() => {
    clearRetry()
    setVariant('carregando')
    retryTimer.current = setTimeout(
      () => setVariant(defaultVariant(screen, radarOn)),
      1400,
    )
  }, [screen, radarOn])

  const props: ScreenProps = {
    variant: shownVariant,
    setVariant: selectVariant,
    go,
    retry,
    radarOn,
    toggleRadar,
    themeMode,
    setThemeMode,
    resolvedTheme,
  }

  const renderScreen = () => {
    switch (screen) {
      case 'oferta':
        return <OfferScreen {...props} />
      case 'inicio':
        return <HomeScreen {...props} />
      case 'ajustes':
        return <SettingsScreen {...props} />
      case 'ganhos':
        return <EarningsScreen {...props} />
      case 'mapa':
        return <MapScreen {...props} />
      case 'bolinha':
        return <BubbleScreen {...props} />
      case 'avisos':
        return <AlertsScreen {...props} />
    }
  }

  const notes = NOTES[noteKey(screen)]

  return (
    <div
      className={cn(
        'min-h-dvh bg-stage text-foreground',
        resolvedTheme === 'dark' && 'dark',
      )}
    >
      <header className="flex min-h-16 items-center justify-between gap-3 border-b border-border bg-background px-4 lg:px-8">
        <div className="flex items-center gap-3">
          <span className="grid size-10 place-items-center rounded-full bg-primary text-xl font-bold text-primary-foreground">
            R
          </span>
          <div className="leading-tight">
            <h1 className="text-lg font-bold">Radar Corridas</h1>
            <p className="text-sm text-muted-foreground">
              Redesenho das telas · protótipo navegável
            </p>
          </div>
        </div>

        <div
          role="radiogroup"
          aria-label="Tema do protótipo"
          className="flex gap-1 rounded-full bg-surface-2 p-1"
        >
          {(
            [
              { id: 'dark', label: 'Escuro', icon: 'dark_mode' },
              { id: 'light', label: 'Claro', icon: 'light_mode' },
            ] as const
          ).map((t) => {
            const active = resolvedTheme === t.id && themeMode !== 'auto'
            return (
              <button
                key={t.id}
                type="button"
                role="radio"
                aria-checked={active}
                onClick={() => setThemeMode(t.id)}
                className={cn(
                  'inline-flex min-h-10 items-center gap-2 rounded-full px-4 text-base font-bold focus-visible:outline-2 focus-visible:outline-accent-text',
                  active
                    ? 'bg-primary text-primary-foreground'
                    : 'text-foreground',
                )}
              >
                <Icon name={t.icon} size={20} fill={active} />
                {t.label}
              </button>
            )
          })}
        </div>
      </header>

      <div className="mx-auto grid max-w-[1320px] gap-6 px-4 py-6 lg:grid-cols-[272px_auto_340px] lg:justify-center lg:gap-8 lg:px-8">
        <aside className="flex min-w-0 flex-col gap-6">
          <nav aria-label="Telas do protótipo">
            <h2 className="pb-2 text-base font-bold text-muted-foreground">
              Telas
            </h2>
            <ul className="flex gap-2 overflow-x-auto pb-1 lg:flex-col lg:overflow-visible">
              {SCREENS.map((s, i) => {
                const active = s.matches.includes(screen)
                return (
                  <li key={s.id} className="shrink-0 lg:shrink">
                    <button
                      type="button"
                      onClick={() => go(s.id)}
                      aria-current={active ? 'page' : undefined}
                      className={cn(
                        'flex min-h-14 w-full items-center gap-3 rounded-2xl border-2 px-3 text-left focus-visible:outline-2 focus-visible:outline-accent-text',
                        active
                          ? 'border-primary bg-card'
                          : 'border-transparent bg-background',
                      )}
                    >
                      <span
                        className={cn(
                          'grid size-9 shrink-0 place-items-center rounded-full',
                          active
                            ? 'bg-primary text-primary-foreground'
                            : 'bg-surface-2',
                        )}
                      >
                        <Icon name={s.icon} size={20} fill={active} />
                      </span>
                      <span className="leading-tight">
                        <span className="block text-base font-bold">
                          {i + 1}. {s.label}
                        </span>
                        <span className="hidden text-sm text-muted-foreground lg:block">
                          {s.hint}
                        </span>
                      </span>
                    </button>
                  </li>
                )
              })}
            </ul>
          </nav>

          <div>
            <h2 className="pb-2 text-base font-bold text-muted-foreground">
              Estado da tela
            </h2>
            <div
              role="group"
              aria-label="Estado da tela"
              className="flex flex-wrap gap-2"
            >
              {VARIANTS[screen].map((v) => {
                const shown = v.id === shownVariant
                return (
                  <button
                    key={v.id}
                    type="button"
                    aria-pressed={shown}
                    onClick={() => selectVariant(v.id)}
                    className={cn(
                      'inline-flex min-h-11 items-center gap-2 rounded-full border-2 px-4 text-base font-bold focus-visible:outline-2 focus-visible:outline-accent-text',
                      shown
                        ? 'border-primary bg-primary text-primary-foreground'
                        : 'border-border bg-background text-foreground',
                    )}
                  >
                    <Icon name={v.icon} size={20} fill={shown} />
                    {v.label}
                  </button>
                )
              })}
            </div>
            {screen === 'inicio' || screen === 'ajustes' ? (
              <p className="pt-3 text-sm text-muted-foreground">
                Use a barra inferior do celular para ir entre Início, Ganhos,
                Mapa e Ajustes.
              </p>
            ) : null}
          </div>
        </aside>

        <main className="flex justify-center">
          <PhoneFrame scale={scale}>
            <div className="flex min-h-0 flex-1 flex-col">{renderScreen()}</div>
            {IN_APP.includes(screen) && <BottomNav active={screen} go={go} />}
          </PhoneFrame>
        </main>

        <aside className="flex min-w-0 flex-col gap-6">
          <section
            aria-labelledby="notas-titulo"
            className="rounded-3xl border border-border bg-card p-5"
          >
            <p className="text-sm font-bold text-muted-foreground">
              Decisões de UX
            </p>
            <h2 id="notas-titulo" className="pb-3 text-xl font-bold">
              {notes.title}
            </h2>
            <ul className="flex flex-col gap-3">
              {notes.items.map((item) => (
                <li key={item} className="flex gap-3 text-base leading-relaxed">
                  <Icon
                    name="check_circle"
                    size={20}
                    fill
                    className="mt-0.5 text-accent-text"
                  />
                  <span className="text-pretty">{item}</span>
                </li>
              ))}
            </ul>
          </section>

          <section
            aria-labelledby="regras-titulo"
            className="rounded-3xl border border-border bg-background p-5"
          >
            <h2 id="regras-titulo" className="pb-3 text-lg font-bold">
              Regras em todas as telas
            </h2>
            <ul className="flex flex-col gap-2">
              {PRINCIPLES.map((p) => (
                <li key={p} className="text-base leading-relaxed text-muted-foreground">
                  {p}
                </li>
              ))}
            </ul>
          </section>
        </aside>
      </div>
    </div>
  )
}
