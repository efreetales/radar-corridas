import type { ReactNode } from 'react'
import { Icon } from './icon'

export const PHONE_W = 410
export const PHONE_H = 864

function StatusBar() {
  return (
    <div className="flex h-9 shrink-0 items-center justify-between px-6 text-base font-bold">
      <span>21:42</span>
      <span className="flex items-center gap-1.5">
        <Icon name="wifi" size={20} fill />
        <Icon name="signal_cellular_alt" size={20} fill />
        <Icon name="battery_5_bar" size={20} fill />
      </span>
    </div>
  )
}

/** Moldura Android de 390x844, escalada para caber na janela. */
export function PhoneFrame({
  scale,
  children,
}: {
  scale: number
  children: ReactNode
}) {
  return (
    <div style={{ width: PHONE_W * scale, height: PHONE_H * scale }}>
      <div
        style={{
          width: PHONE_W,
          height: PHONE_H,
          transform: `scale(${scale})`,
          transformOrigin: 'top left',
        }}
        className="rounded-[56px] bg-black p-[10px] shadow-2xl ring-1 ring-border"
      >
        <div className="relative flex h-[844px] w-[390px] flex-col overflow-hidden rounded-[46px] bg-background text-foreground">
          <StatusBar />
          {children}
          <div
            aria-hidden="true"
            className="pointer-events-none absolute bottom-2 left-1/2 h-1.5 w-32 -translate-x-1/2 rounded-full bg-foreground/70"
          />
        </div>
      </div>
    </div>
  )
}
