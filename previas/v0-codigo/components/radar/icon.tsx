import { cn } from '@/lib/utils'

type IconProps = {
  name: string
  size?: number
  fill?: boolean
  className?: string
}

/** Material Symbols (Rounded). Sempre decorativo: o significado vem do texto ao lado. */
export function Icon({ name, size = 24, fill = false, className }: IconProps) {
  return (
    <span
      aria-hidden="true"
      className={cn('msr', className)}
      style={{
        fontSize: size,
        fontVariationSettings: `'FILL' ${fill ? 1 : 0}, 'wght' 500, 'GRAD' 0, 'opsz' ${Math.min(48, Math.max(20, size))}`,
      }}
    >
      {name}
    </span>
  )
}
