'use client'

import { useRef, useState } from 'react'
import { cn } from '@/lib/utils'
import { Icon } from '../icon'
import {
  ActionButton,
  ErrorState,
  LoadingBlock,
  ScreenHeader,
  StateMessage,
} from '../ui'
import type { ScreenProps } from '../config'

const SHIFTS = [
  { icon: 'light_mode', name: 'Manhã', time: '06:10 – 09:40', detail: '3 h 30 · 7 corridas', total: 'R$ 96,80' },
  { icon: 'restaurant', name: 'Almoço', time: '11:50 – 13:20', detail: '1 h 30 · 3 corridas', total: 'R$ 41,50' },
  { icon: 'dark_mode', name: 'Noite', time: '18:00 – 21:15', detail: '3 h 15 · 6 corridas', total: 'R$ 118,30' },
]

const CAR_COSTS = [
  { icon: 'local_gas_station', name: 'Combustível', value: 'R$ 612,90' },
  { icon: 'qr_code_2', name: 'Pix', value: 'R$ 120,00' },
  { icon: 'toll', name: 'Tag', value: 'R$ 86,40' },
]

const REAL_COST = [
  { name: 'Combustível', value: 'R$ 0,55', width: '70%', cls: 'bg-primary' },
  { name: 'Manutenção', value: 'R$ 0,15', width: '19%', cls: 'bg-foreground' },
  { name: 'Pedágio (tag)', value: 'R$ 0,08', width: '11%', cls: 'bg-muted-foreground' },
]

const ENTRIES = [
  { icon: 'payments', date: '21 out', name: 'Ganhos do dia', value: '+ R$ 256,60', income: true },
  { icon: 'local_gas_station', date: '21 out', name: 'Combustível', value: '− R$ 120,00', income: false },
  { icon: 'payments', date: '20 out', name: 'Ganhos do dia', value: '+ R$ 231,10', income: true },
  { icon: 'toll', date: '20 out', name: 'Tag', value: '− R$ 18,40', income: false },
  { icon: 'qr_code_2', date: '19 out', name: 'Pix · troca de óleo', value: '− R$ 120,00', income: false },
]

const PENDING = [
  { id: 'a', app: 'UberX', time: '18:42', price: 'R$ 12,30', km: '5,1 km', why: 'O valor mudou durante a corrida.' },
  { id: 'b', app: '99Pop', time: '19:20', price: 'R$ 9,80', km: '3,4 km', why: 'Ela pode ter sido cancelada.' },
  { id: 'c', app: 'UberX', time: '20:05', price: 'R$ 22,10', km: '11,2 km', why: 'O km ficou diferente da oferta.' },
]

function SectionTitle({ id, children }: { id: string; children: string }) {
  return (
    <h2 id={id} className="px-1 pb-3 text-2xl font-bold">
      {children}
    </h2>
  )
}

function Tile({
  label,
  value,
  highlight,
}: {
  label: string
  value: string
  highlight?: boolean
}) {
  return (
    <div
      className={cn(
        'rounded-2xl border bg-card p-4',
        highlight ? 'border-2 border-primary' : 'border-border',
      )}
    >
      <p className="text-lg text-muted-foreground">{label}</p>
      <p className="text-[28px] font-bold leading-tight tabular-nums">{value}</p>
    </div>
  )
}

export function EarningsScreen({ variant, retry, go, toggleRadar }: ScreenProps) {
  const [pending, setPending] = useState(PENDING)
  const [lastAnswer, setLastAnswer] = useState<string | null>(null)
  const confirmRef = useRef<HTMLElement>(null)

  const answer = (id: string, yes: boolean) => {
    setPending((list) => list.filter((p) => p.id !== id))
    setLastAnswer(yes ? 'Corrida confirmada.' : 'Corrida removida dos ganhos.')
  }

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <ScreenHeader title="Radar" subtitle="Seus ganhos · quarta, 21 de outubro" />
      <div className="min-h-0 flex-1 overflow-y-auto px-4 pb-8">
        {variant === 'carregando' && (
          <LoadingBlock label="Carregando seus ganhos…" rows={4} />
        )}
        {variant === 'erro' && (
          <ErrorState title="Não deu para carregar seus ganhos" onRetry={retry} />
        )}
        {variant === 'vazio' && (
          <div className="pt-2">
            <StateMessage
              icon="inbox"
              title="Nenhum ganho hoje ainda"
              text="Ligue a leitura e aceite uma corrida. Ela aparece aqui sozinha."
              action={
                <ActionButton
                  icon="power_settings_new"
                  size="lg"
                  onClick={() => {
                    toggleRadar()
                    go('inicio')
                  }}
                >
                  Ligar leitura
                </ActionButton>
              }
            />
          </div>
        )}

        {variant === 'normal' && (
          <div className="flex flex-col gap-8">
            {pending.length > 0 && (
              <button
                type="button"
                onClick={() =>
                  confirmRef.current?.scrollIntoView({ behavior: 'smooth' })
                }
                className="flex min-h-16 items-center gap-3 rounded-2xl border-[3px] border-warn bg-card px-4 text-left focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent-text"
              >
                <span className="grid size-11 shrink-0 place-items-center rounded-full bg-warn text-on-verdict">
                  <Icon name="help" size={28} fill />
                </span>
                <span className="flex-1 text-lg font-bold">
                  {pending.length}{' '}
                  {pending.length === 1 ? 'corrida' : 'corridas'} para confirmar
                </span>
                <Icon name="arrow_downward" size={26} />
              </button>
            )}

            <section aria-labelledby="turnos">
              <SectionTitle id="turnos">Por turno</SectionTitle>
              <div className="overflow-hidden rounded-2xl border border-border bg-card">
                <div className="grid grid-cols-2 gap-3 border-b border-border bg-surface-2 p-4">
                  <div>
                    <p className="text-lg text-muted-foreground">Total do dia</p>
                    <p className="text-[32px] font-bold leading-tight tabular-nums">
                      R$ 256,60
                    </p>
                  </div>
                  <div>
                    <p className="text-lg text-muted-foreground">Tempo online</p>
                    <p className="text-[32px] font-bold leading-tight tabular-nums">
                      8 h 15
                    </p>
                  </div>
                </div>
                <ul className="divide-y divide-border">
                  {SHIFTS.map((s) => (
                    <li key={s.name} className="flex min-h-[72px] items-center gap-3 px-4 py-3">
                      <span className="grid size-11 shrink-0 place-items-center rounded-full bg-surface-2">
                        <Icon name={s.icon} size={24} />
                      </span>
                      <span className="min-w-0 flex-1">
                        <span className="block text-lg font-bold leading-snug">
                          {s.name} · {s.time}
                        </span>
                        <span className="block text-base text-muted-foreground">
                          {s.detail}
                        </span>
                      </span>
                      <span className="text-xl font-bold tabular-nums">{s.total}</span>
                    </li>
                  ))}
                </ul>
              </div>
            </section>

            <section aria-labelledby="numeros">
              <SectionTitle id="numeros">Seus números</SectionTitle>
              <div className="grid grid-cols-2 gap-3">
                <Tile label="Ganho bruto" value="R$ 256,60" />
                <Tile label="Líquido" value="R$ 173,50" highlight />
                <Tile label="R$/km" value="R$ 2,41" />
                <Tile label="R$/hora" value="R$ 31,10" />
              </div>
            </section>

            <section aria-labelledby="carro">
              <SectionTitle id="carro">Seu carro e seus gastos</SectionTitle>
              <div className="overflow-hidden rounded-2xl border border-border bg-card">
                <ul className="divide-y divide-border">
                  {CAR_COSTS.map((c) => (
                    <li key={c.name} className="flex min-h-16 items-center gap-3 px-4 py-3">
                      <span className="grid size-11 shrink-0 place-items-center rounded-full bg-surface-2">
                        <Icon name={c.icon} size={24} />
                      </span>
                      <span className="flex-1 text-lg font-bold">{c.name}</span>
                      <span className="text-xl font-bold tabular-nums">{c.value}</span>
                    </li>
                  ))}
                </ul>
                <p className="flex items-center justify-between border-t border-border bg-surface-2 px-4 py-3 text-lg">
                  <span className="text-muted-foreground">Total no mês</span>
                  <span className="text-xl font-bold tabular-nums">R$ 819,30</span>
                </p>
              </div>
            </section>

            <section aria-labelledby="custo">
              <SectionTitle id="custo">Seu custo real por km</SectionTitle>
              <div className="rounded-2xl border border-border bg-card p-4">
                <p className="text-[44px] font-bold leading-none tabular-nums">
                  R$ 0,78
                  <span className="ml-2 text-xl font-normal text-muted-foreground">
                    por km rodado
                  </span>
                </p>
                <div
                  role="img"
                  aria-label="Divisão do custo: combustível 70%, manutenção 19%, pedágio 11%"
                  className="mt-4 flex h-4 gap-1 overflow-hidden rounded-full"
                >
                  {REAL_COST.map((r) => (
                    <span key={r.name} className={cn('h-full', r.cls)} style={{ width: r.width }} />
                  ))}
                </div>
                <ul className="mt-3 flex flex-col gap-2">
                  {REAL_COST.map((r) => (
                    <li key={r.name} className="flex items-center gap-3 text-lg">
                      <span className={cn('size-4 shrink-0 rounded-full', r.cls)} />
                      <span className="flex-1">{r.name}</span>
                      <span className="font-bold tabular-nums">{r.value}</span>
                    </li>
                  ))}
                </ul>
              </div>
            </section>

            <section aria-labelledby="lancamentos">
              <SectionTitle id="lancamentos">Lançamentos do mês</SectionTitle>
              <ul className="divide-y divide-border overflow-hidden rounded-2xl border border-border bg-card">
                {ENTRIES.map((e, i) => (
                  <li key={i} className="flex min-h-16 items-center gap-3 px-4 py-3">
                    <span className="grid size-11 shrink-0 place-items-center rounded-full bg-surface-2">
                      <Icon name={e.icon} size={24} />
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block text-lg font-bold leading-snug">{e.name}</span>
                      <span className="block text-base text-muted-foreground">{e.date}</span>
                    </span>
                    <span className="text-xl font-bold tabular-nums">{e.value}</span>
                  </li>
                ))}
              </ul>
              <ActionButton variant="secondary" icon="receipt_long" className="mt-3">
                Ver todos os lançamentos
              </ActionButton>
            </section>

            <section ref={confirmRef} aria-labelledby="confirmar" className="scroll-mt-2">
              <SectionTitle id="confirmar">Para confirmar</SectionTitle>
              {lastAnswer && (
                <p
                  role="status"
                  className="mb-3 flex items-center gap-2 rounded-2xl bg-surface-2 px-4 py-3 text-lg font-bold"
                >
                  <Icon name="check_circle" size={24} fill className="text-accent-text" />
                  {lastAnswer}
                </p>
              )}
              {pending.length === 0 ? (
                <StateMessage
                  icon="task_alt"
                  title="Tudo confirmado"
                  text="Nenhuma corrida com dúvida por enquanto."
                />
              ) : (
                <ul className="flex flex-col gap-3">
                  {pending.map((p) => (
                    <li key={p.id} className="rounded-2xl border-2 border-warn bg-card p-4">
                      <div className="flex items-start justify-between gap-3">
                        <div>
                          <p className="text-lg font-bold">
                            {p.app} · {p.time}
                          </p>
                          <p className="text-base text-muted-foreground">{p.km}</p>
                        </div>
                        <p className="text-2xl font-bold tabular-nums">{p.price}</p>
                      </div>
                      <p className="mt-2 flex items-center gap-2 text-lg">
                        <Icon name="help" size={22} fill />
                        {p.why}
                      </p>
                      <p className="mt-2 text-lg font-bold">Você fez essa corrida?</p>
                      <div className="mt-2 grid grid-cols-2 gap-3">
                        <ActionButton icon="check" onClick={() => answer(p.id, true)}>
                          Sim, fiz
                        </ActionButton>
                        <ActionButton
                          variant="secondary"
                          icon="close"
                          onClick={() => answer(p.id, false)}
                        >
                          Não fiz
                        </ActionButton>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </div>
        )}
      </div>
    </div>
  )
}
