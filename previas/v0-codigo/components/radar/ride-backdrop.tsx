import type { ReactNode } from 'react'
import { Icon } from './icon'

/** Simulação genérica do app de corrida que fica por baixo dos overlays do Radar. */
export function RideBackdrop({
  children,
  showSheet = true,
}: {
  children?: ReactNode
  showSheet?: boolean
}) {
  return (
    <div
      className="relative min-h-0 flex-1 overflow-hidden bg-surface-2"
      style={{
        backgroundImage:
          'linear-gradient(var(--border) 1px, transparent 1px), linear-gradient(90deg, var(--border) 1px, transparent 1px)',
        backgroundSize: '56px 56px',
      }}
    >
      <div className="absolute inset-x-0 top-0 flex items-center justify-center px-4 py-3">
        <span className="inline-flex min-h-12 items-center gap-2 rounded-full border border-border bg-card px-5 text-lg font-bold">
          <Icon name="radio_button_checked" size={22} className="text-accent-text" />
          Você está online
        </span>
      </div>

      {showSheet && (
        <div className="absolute inset-x-0 bottom-0 rounded-t-3xl border-t border-border bg-card px-5 pb-8 pt-4">
          <p className="text-sm text-muted-foreground">
            App da corrida (simulação)
          </p>
          <div className="mt-1 flex items-center justify-between">
            <p className="text-2xl font-bold">Nova corrida</p>
            <p className="inline-flex items-center gap-1 text-lg font-bold">
              <Icon name="star" size={22} fill />
              4,87
            </p>
          </div>
          <p className="text-base text-muted-foreground">
            Passageiro a 6 min · 1,9 km de você
          </p>
          <div className="mt-3 grid grid-cols-[1fr_auto] gap-3">
            <div className="grid min-h-14 place-items-center rounded-2xl bg-foreground text-lg font-bold text-background">
              Aceitar
            </div>
            <div className="grid min-h-14 place-items-center rounded-2xl border-2 border-border px-5 text-lg font-bold">
              Recusar
            </div>
          </div>
        </div>
      )}

      {children}
    </div>
  )
}
