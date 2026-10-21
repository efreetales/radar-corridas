import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'
import { Icon } from '../icon'
import { RideBackdrop } from '../ride-backdrop'
import { Bubble } from '../bubble'
import { ActionButton } from '../ui'
import type { ScreenProps } from '../config'

function AlertShell({
  tone,
  icon,
  kicker,
  title,
  children,
  showTimer = true,
}: {
  tone: 'bad' | 'warn' | 'primary'
  icon: string
  kicker: string
  title: string
  children: ReactNode
  showTimer?: boolean
}) {
  const border = { bad: 'border-bad', warn: 'border-warn', primary: 'border-primary' }[tone]
  const fill = {
    bad: 'bg-bad text-on-verdict',
    warn: 'bg-warn text-on-verdict',
    primary: 'bg-primary text-primary-foreground',
  }[tone]
  const bar = { bad: 'bg-bad', warn: 'bg-warn', primary: 'bg-primary' }[tone]

  return (
    <section
      role="alert"
      className={cn(
        'dark overflow-hidden rounded-[28px] border-4 bg-card text-foreground shadow-2xl',
        border,
      )}
    >
      <div className="flex items-center gap-4 p-4">
        <span
          className={cn('grid size-20 shrink-0 place-items-center rounded-2xl', fill)}
        >
          <Icon name={icon} size={48} fill />
        </span>
        <div className="min-w-0">
          <p className="text-lg font-bold text-muted-foreground">{kicker}</p>
          <h2 className="text-balance text-[30px] font-bold leading-tight">
            {title}
          </h2>
        </div>
      </div>
      <div className="px-4 pb-4">{children}</div>
      {showTimer && (
        <div className="h-2 bg-surface-2">
          <div key={title} className={cn('animate-shrink h-full', bar)} />
        </div>
      )}
    </section>
  )
}

function Stat({
  label,
  value,
  alert,
}: {
  label: string
  value: string
  alert?: boolean
}) {
  return (
    <div
      className={cn(
        'rounded-2xl bg-surface-2 px-4 py-3',
        alert && 'border-2 border-bad',
      )}
    >
      <p className="flex items-center gap-1.5 text-base text-muted-foreground">
        {alert && <Icon name="error" size={20} fill />}
        {label}
      </p>
      <p className="text-3xl font-bold tabular-nums leading-tight">{value}</p>
    </div>
  )
}

export function AlertsScreen({ variant }: ScreenProps) {
  return (
    <RideBackdrop>
      <div className="absolute inset-x-3 top-3">
        {variant === 'radar' && (
          <AlertShell
            tone="bad"
            icon="speed"
            kicker="Radar de velocidade"
            title="Radar a 300 m"
          >
            <div className="grid grid-cols-2 gap-3">
              <Stat label="Limite" value="60 km/h" />
              <Stat label="Você" value="72 km/h" alert />
            </div>
            <p className="mt-3 text-xl font-bold">
              Reduza 12 km/h para passar sem multa.
            </p>
            <ActionButton
              variant="secondary"
              icon="notifications_off"
              className="mt-3"
            >
              Silenciar este radar
            </ActionButton>
          </AlertShell>
        )}

        {variant === 'valeta' && (
          <AlertShell
            tone="warn"
            icon="warning"
            kicker="Valeta à frente"
            title="Valeta a 150 m"
          >
            <p className="text-xl font-bold">Reduza a velocidade.</p>
            <p className="text-lg text-muted-foreground">
              12 motoristas avisaram nos últimos 7 dias.
            </p>
            <p className="mt-3 text-lg font-bold">Ela ainda está lá?</p>
            <div className="mt-2 grid grid-cols-2 gap-3">
              <ActionButton icon="check" size="lg" className="px-2">
                Ainda está
              </ActionButton>
              <ActionButton
                variant="secondary"
                icon="close"
                size="lg"
                className="px-2"
              >
                Não está
              </ActionButton>
            </div>
          </AlertShell>
        )}

        {variant === 'versao' && (
          <AlertShell
            tone="primary"
            icon="system_update"
            kicker="Atualização"
            title="Nova versão do Radar"
            showTimer={false}
          >
            <p className="text-xl font-bold">Versão 0.1.85 pronta.</p>
            <p className="text-lg text-muted-foreground">
              Leva cerca de 1 minuto. Instale quando estiver parado.
            </p>
            <div className="mt-3 grid gap-3">
              <ActionButton icon="download" size="lg">
                Baixar e instalar
              </ActionButton>
              <ActionButton variant="secondary" size="lg">
                Depois
              </ActionButton>
            </div>
          </AlertShell>
        )}
      </div>

      <div className="absolute right-3 top-[540px]">
        <Bubble state="ligado" size={56} />
      </div>
    </RideBackdrop>
  )
}
