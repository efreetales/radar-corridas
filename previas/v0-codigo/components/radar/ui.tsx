import type { ButtonHTMLAttributes, ReactNode } from 'react'
import { cn } from '@/lib/utils'
import { Icon } from './icon'
import type { Verdict } from './config'

export const VERDICT: Record<
  Verdict,
  {
    label: string
    short: string
    icon: string
    dotIcon: string
    fill: string
    border: string
  }
> = {
  bom: {
    label: 'Corrida boa',
    short: 'Bom',
    icon: 'check_circle',
    dotIcon: 'check',
    fill: 'bg-good',
    border: 'border-good',
  },
  medio: {
    label: 'Corrida média',
    short: 'Médio',
    icon: 'error',
    dotIcon: 'remove',
    fill: 'bg-warn',
    border: 'border-warn',
  },
  ruim: {
    label: 'Corrida ruim',
    short: 'Ruim',
    icon: 'cancel',
    dotIcon: 'close',
    fill: 'bg-bad',
    border: 'border-bad',
  },
}

/** Bolinha do veredito: cor + ícone dentro + texto para leitor de tela. */
export function VerdictDot({
  verdict,
  size = 32,
}: {
  verdict: Verdict
  size?: number
}) {
  const v = VERDICT[verdict]
  return (
    <span
      className={cn(
        'grid shrink-0 place-items-center rounded-full text-on-verdict',
        v.fill,
      )}
      style={{ width: size, height: size }}
    >
      <Icon name={v.dotIcon} size={size * 0.68} />
      <span className="sr-only">{v.short}</span>
    </span>
  )
}

type ActionButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'secondary' | 'ghost'
  size?: 'md' | 'lg'
  icon?: string
}

const BUTTON_VARIANTS = {
  primary: 'bg-primary text-primary-foreground',
  secondary: 'border-2 border-border bg-surface-2 text-foreground',
  ghost: 'text-foreground',
}

export function ActionButton({
  variant = 'primary',
  size = 'md',
  icon,
  className,
  children,
  ...rest
}: ActionButtonProps) {
  return (
    <button
      type="button"
      className={cn(
        'inline-flex w-full items-center justify-center gap-2 rounded-2xl px-4 text-lg font-bold transition-transform focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent-text active:scale-[0.98] disabled:opacity-60',
        size === 'lg' ? 'min-h-16 text-xl' : 'min-h-14',
        BUTTON_VARIANTS[variant],
        className,
      )}
      {...rest}
    >
      {icon && <Icon name={icon} size={26} />}
      {children}
    </button>
  )
}

export function SwitchVisual({ checked }: { checked: boolean }) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        'relative h-9 w-16 shrink-0 rounded-full border-2 transition-colors',
        checked
          ? 'border-primary bg-primary'
          : 'border-muted-foreground bg-surface-2',
      )}
    >
      <span
        className={cn(
          'absolute top-0.5 grid size-7 place-items-center rounded-full transition-all',
          checked
            ? 'left-[30px] bg-primary-foreground text-accent-text'
            : 'left-0.5 bg-muted-foreground text-card',
        )}
      >
        <Icon name={checked ? 'check' : 'close'} size={18} />
      </span>
    </span>
  )
}

const ROW =
  'flex min-h-16 w-full items-center gap-4 px-4 py-3 text-left focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent-text active:bg-surface-2'

function RowIcon({ name }: { name: string }) {
  return (
    <span className="grid size-11 shrink-0 place-items-center rounded-full bg-surface-2 text-foreground">
      <Icon name={name} size={24} />
    </span>
  )
}

export function NavRow({
  icon,
  title,
  value,
  onClick,
  chevron = true,
}: {
  icon: string
  title: string
  value?: string
  onClick?: () => void
  chevron?: boolean
}) {
  return (
    <button type="button" onClick={onClick} className={ROW}>
      <RowIcon name={icon} />
      <span className="min-w-0 flex-1">
        <span className="block text-lg font-bold leading-snug">{title}</span>
        {value && (
          <span className="block text-base text-muted-foreground">{value}</span>
        )}
      </span>
      {chevron && (
        <Icon name="chevron_right" size={28} className="text-muted-foreground" />
      )}
    </button>
  )
}

export function ToggleRow({
  icon,
  title,
  description,
  checked,
  onChange,
}: {
  icon: string
  title: string
  description?: string
  checked: boolean
  onChange: (v: boolean) => void
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      onClick={() => onChange(!checked)}
      className={ROW}
    >
      <RowIcon name={icon} />
      <span className="min-w-0 flex-1">
        <span className="block text-lg font-bold leading-snug">{title}</span>
        <span className="block text-base text-muted-foreground">
          {checked ? 'Ligado' : 'Desligado'}
          {description ? ` · ${description}` : ''}
        </span>
      </span>
      <SwitchVisual checked={checked} />
    </button>
  )
}

export function Group({
  title,
  children,
}: {
  title: string
  children: ReactNode
}) {
  return (
    <section className="mt-7">
      <h2 className="px-1 pb-2 text-lg font-bold text-muted-foreground">
        {title}
      </h2>
      <div className="divide-y divide-border overflow-hidden rounded-2xl border border-border bg-card">
        {children}
      </div>
    </section>
  )
}

export function Segmented<T extends string>({
  value,
  onChange,
  options,
  label,
}: {
  value: T
  onChange: (v: T) => void
  options: { id: T; label: string; icon: string }[]
  label: string
}) {
  return (
    <div
      role="radiogroup"
      aria-label={label}
      className="grid gap-1 rounded-2xl bg-surface-2 p-1"
      style={{ gridTemplateColumns: `repeat(${options.length}, minmax(0, 1fr))` }}
    >
      {options.map((o) => {
        const active = o.id === value
        return (
          <button
            key={o.id}
            type="button"
            role="radio"
            aria-checked={active}
            onClick={() => onChange(o.id)}
            className={cn(
              'flex min-h-16 flex-col items-center justify-center gap-0.5 rounded-xl px-1 text-base font-bold focus-visible:outline-2 focus-visible:outline-accent-text',
              active
                ? 'bg-primary text-primary-foreground'
                : 'text-foreground',
            )}
          >
            <Icon name={o.icon} size={24} fill={active} />
            {o.label}
          </button>
        )
      })}
    </div>
  )
}

export function Skeleton({ className }: { className?: string }) {
  return (
    <div
      aria-hidden="true"
      className={cn('animate-pulse rounded-2xl bg-surface-2', className)}
    />
  )
}

export function LoadingBlock({
  label,
  rows = 3,
}: {
  label: string
  rows?: number
}) {
  return (
    <div role="status" aria-live="polite" className="flex flex-col gap-4 px-4 pt-4">
      <div className="flex items-center gap-3 text-lg font-bold">
        <Icon name="progress_activity" size={32} className="animate-spin text-accent-text" />
        {label}
      </div>
      {Array.from({ length: rows }).map((_, i) => (
        <Skeleton key={i} className="h-24" />
      ))}
    </div>
  )
}

export function StateMessage({
  icon,
  title,
  text,
  action,
  tone = 'neutral',
  className,
}: {
  icon: string
  title: string
  text?: string
  action?: ReactNode
  tone?: 'neutral' | 'error' | 'warn'
  className?: string
}) {
  return (
    <div
      role={tone === 'error' ? 'alert' : 'status'}
      className={cn(
        'flex flex-col items-center gap-3 rounded-3xl border border-border bg-card px-6 py-8 text-center',
        className,
      )}
    >
      <span
        className={cn(
          'grid size-20 place-items-center rounded-full',
          tone === 'error' && 'bg-bad text-on-verdict',
          tone === 'warn' && 'bg-warn text-on-verdict',
          tone === 'neutral' && 'bg-surface-2 text-foreground',
        )}
      >
        <Icon name={icon} size={44} />
      </span>
      <h2 className="text-balance text-2xl font-bold leading-tight">{title}</h2>
      {text && (
        <p className="text-pretty text-lg leading-relaxed text-muted-foreground">
          {text}
        </p>
      )}
      {action && <div className="mt-2 w-full">{action}</div>}
    </div>
  )
}

export function ErrorState({
  title,
  text,
  onRetry,
}: {
  title: string
  text?: string
  onRetry: () => void
}) {
  return (
    <div className="px-4 pt-4">
      <StateMessage
        tone="error"
        icon="cloud_off"
        title={title}
        text={text ?? 'Confira a internet e tente de novo.'}
        action={
          <ActionButton icon="refresh" onClick={onRetry}>
            Tentar de novo
          </ActionButton>
        }
      />
    </div>
  )
}

export function ScreenHeader({
  title,
  subtitle,
  right,
}: {
  title: string
  subtitle?: string
  right?: ReactNode
}) {
  return (
    <header className="flex items-start justify-between gap-3 px-5 pb-3 pt-3">
      <div className="min-w-0">
        <h1 className="text-[32px] font-bold leading-tight">{title}</h1>
        {subtitle && (
          <p className="text-base text-muted-foreground">{subtitle}</p>
        )}
      </div>
      {right}
    </header>
  )
}
