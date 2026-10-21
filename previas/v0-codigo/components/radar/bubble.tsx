import { cn } from '@/lib/utils'
import { Icon } from './icon'

export type BubbleState = 'ligado' | 'desligado' | 'aviso'

export const BUBBLE_TEXT: Record<
  BubbleState,
  { short: string; long: string }
> = {
  ligado: { short: 'Radar ligado', long: 'Procurando ofertas' },
  desligado: { short: 'Radar desligado', long: 'Toque para ligar' },
  aviso: { short: 'Leitura desligada', long: 'Toque para ligar' },
}

const BADGE: Record<BubbleState, { icon: string; cls: string }> = {
  ligado: { icon: 'check', cls: 'bg-good text-on-verdict' },
  desligado: { icon: 'power_settings_new', cls: 'bg-foreground text-background' },
  aviso: { icon: 'warning', cls: 'bg-warn text-on-verdict' },
}

const BODY: Record<BubbleState, string> = {
  ligado: 'bg-primary text-primary-foreground ring-4 ring-primary/30',
  desligado: 'border-2 border-muted-foreground bg-surface-2 text-muted-foreground',
  aviso: 'border-4 border-warn bg-card text-foreground',
}

/** Bolinha "R": sempre escura/neutra para ficar legível sobre qualquer app. */
export function Bubble({
  state,
  size = 64,
  onClick,
}: {
  state: BubbleState
  size?: number
  onClick?: () => void
}) {
  const badge = BADGE[state]
  const text = BUBBLE_TEXT[state]
  const Tag = onClick ? 'button' : 'span'

  return (
    <Tag
      {...(onClick ? { type: 'button' as const, onClick } : {})}
      aria-label={`${text.short}. ${text.long}.`}
      className="dark relative inline-block rounded-full shadow-xl focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent-text"
      style={{ width: size, height: size }}
    >
      <span
        className={cn(
          'grid size-full place-items-center rounded-full font-bold',
          BODY[state],
        )}
        style={{ fontSize: size * 0.5 }}
      >
        R
      </span>
      <span
        className={cn(
          'absolute -bottom-1 -right-1 grid place-items-center rounded-full border-2 border-card',
          badge.cls,
        )}
        style={{ width: size * 0.4, height: size * 0.4 }}
      >
        <Icon name={badge.icon} size={size * 0.26} fill />
      </span>
    </Tag>
  )
}
