import { cn } from '@/lib/utils'
import { Icon } from './icon'
import type { ScreenId } from './config'

const ITEMS: { id: ScreenId; label: string; icon: string }[] = [
  { id: 'inicio', label: 'Início', icon: 'home' },
  { id: 'ganhos', label: 'Ganhos', icon: 'payments' },
  { id: 'mapa', label: 'Mapa', icon: 'map' },
  { id: 'ajustes', label: 'Ajustes', icon: 'settings' },
]

export function BottomNav({
  active,
  go,
}: {
  active: ScreenId
  go: (s: ScreenId) => void
}) {
  return (
    <nav
      aria-label="Navegação principal"
      className="grid shrink-0 grid-cols-4 border-t border-border bg-card px-2 pb-6 pt-2"
    >
      {ITEMS.map((item) => {
        const isActive = item.id === active
        return (
          <button
            key={item.id}
            type="button"
            onClick={() => go(item.id)}
            aria-current={isActive ? 'page' : undefined}
            className="flex min-h-16 flex-col items-center justify-center gap-1 rounded-2xl focus-visible:outline-2 focus-visible:outline-accent-text"
          >
            <span
              className={cn(
                'grid h-8 w-16 place-items-center rounded-full transition-colors',
                isActive
                  ? 'bg-primary text-primary-foreground'
                  : 'text-foreground',
              )}
            >
              <Icon name={item.icon} size={26} fill={isActive} />
            </span>
            <span
              className={cn(
                'text-base leading-none',
                isActive ? 'font-bold' : 'text-muted-foreground',
              )}
            >
              {item.label}
            </span>
          </button>
        )
      })}
    </nav>
  )
}
