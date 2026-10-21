import { cn } from '@/lib/utils'
import { Icon } from '../icon'
import {
  ActionButton,
  ErrorState,
  LoadingBlock,
  ScreenHeader,
} from '../ui'
import type { ScreenProps } from '../config'

export function HomeScreen({
  variant,
  radarOn,
  toggleRadar,
  go,
  retry,
}: ScreenProps) {
  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <ScreenHeader
        title="Radar Corridas"
        subtitle="Quarta-feira, 21 de outubro"
      />
      <div className="min-h-0 flex-1 overflow-y-auto pb-6">
        {variant === 'carregando' && (
          <LoadingBlock label="Verificando a leitura…" rows={3} />
        )}
        {variant === 'erro' && (
          <ErrorState
            title="Não deu para verificar a leitura"
            text="Sem isso, o cartão pode não aparecer nas ofertas."
            onRetry={retry}
          />
        )}
        {variant !== 'carregando' && variant !== 'erro' && (
          <div className="flex flex-col gap-4 px-4">
            <section
              aria-label="Estado da leitura de ofertas"
              className={cn(
                'rounded-3xl border-[3px] bg-card p-5',
                radarOn ? 'border-primary' : 'border-warn',
              )}
            >
              <div className="flex items-center gap-4">
                <span
                  className={cn(
                    'grid size-16 shrink-0 place-items-center rounded-2xl',
                    radarOn
                      ? 'bg-primary text-primary-foreground'
                      : 'bg-warn text-on-verdict',
                  )}
                >
                  <Icon
                    name={radarOn ? 'check_circle' : 'visibility_off'}
                    size={40}
                    fill
                  />
                </span>
                <div>
                  <h2 className="text-balance text-2xl font-bold leading-tight">
                    {radarOn
                      ? 'Leitura de ofertas ativa'
                      : 'Leitura de ofertas desligada'}
                  </h2>
                  <p className="mt-1 text-lg leading-snug text-muted-foreground">
                    {radarOn
                      ? 'O cartão aparece quando chega uma corrida.'
                      : 'Você não vai ver o cartão das ofertas.'}
                  </p>
                </div>
              </div>
              {radarOn ? (
                <div className="mt-4 grid gap-3">
                  <ActionButton icon="open_in_new" size="lg">
                    Abrir a Uber
                  </ActionButton>
                  <ActionButton
                    variant="secondary"
                    icon="power_settings_new"
                    onClick={toggleRadar}
                  >
                    Desligar leitura
                  </ActionButton>
                </div>
              ) : (
                <ActionButton
                  icon="power_settings_new"
                  size="lg"
                  className="mt-4"
                  onClick={toggleRadar}
                >
                  Ligar leitura
                </ActionButton>
              )}
            </section>

            <button
              type="button"
              className="flex min-h-[72px] items-center gap-4 rounded-2xl border-2 border-primary bg-card px-4 py-3 text-left focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent-text"
            >
              <span className="grid size-12 shrink-0 place-items-center rounded-full bg-surface-2 text-accent-text">
                <Icon name="system_update" size={28} />
              </span>
              <span className="min-w-0 flex-1">
                <span className="block text-lg font-bold leading-snug">
                  Versão 0.1.85 disponível
                </span>
                <span className="block text-base text-muted-foreground">
                  Toque para baixar e instalar
                </span>
              </span>
              <Icon name="download" size={28} className="text-accent-text" />
            </button>

            <button
              type="button"
              onClick={() => go('ganhos')}
              className="rounded-2xl border border-border bg-card p-4 text-left focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent-text"
            >
              <span className="flex items-center justify-between text-lg font-bold text-muted-foreground">
                Hoje, líquido
                <Icon name="chevron_right" size={28} />
              </span>
              <span className="block text-[40px] font-bold leading-tight tabular-nums">
                R$ 173,50
              </span>
              <span className="mt-1 flex flex-wrap gap-x-5 gap-y-1 text-lg">
                <span className="inline-flex items-center gap-1.5">
                  <Icon name="schedule" size={22} />8 h 15 online
                </span>
                <span className="inline-flex items-center gap-1.5">
                  <Icon name="route" size={22} />
                  106 km
                </span>
              </span>
            </button>

            <div className="grid grid-cols-2 gap-3">
              <button
                type="button"
                onClick={() => go('oferta')}
                className="flex min-h-28 flex-col items-start justify-between rounded-2xl border border-border bg-card p-4 text-left focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent-text"
              >
                <Icon name="play_circle" size={32} className="text-accent-text" />
                <span className="text-lg font-bold leading-snug">
                  Testar cartão na tela
                </span>
              </button>
              <button
                type="button"
                onClick={() => go('mapa')}
                className="flex min-h-28 flex-col items-start justify-between rounded-2xl border border-border bg-card p-4 text-left focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent-text"
              >
                <Icon name="event" size={32} className="text-accent-text" />
                <span className="text-lg font-bold leading-snug">
                  Eventos de hoje
                  <span className="block text-base font-normal text-muted-foreground">
                    3 saídas perto de você
                  </span>
                </span>
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
