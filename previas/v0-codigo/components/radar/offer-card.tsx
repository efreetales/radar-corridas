import { cn } from '@/lib/utils'
import { Icon } from './icon'
import { VERDICT, VerdictDot } from './ui'
import type { Verdict } from './config'

type Metric = { name: string; value: string; goal: string; verdict: Verdict }

const OFFERS: Record<
  Verdict,
  { price: string; time: string; km: string; metrics: Metric[] }
> = {
  bom: {
    price: 'R$ 18,90',
    time: '21 min',
    km: '6,8 km',
    metrics: [
      { name: 'R$/km', value: 'R$ 2,78', goal: 'meta R$ 2,20', verdict: 'bom' },
      { name: 'R$/hora', value: 'R$ 54,00', goal: 'meta R$ 40,00', verdict: 'bom' },
      { name: 'R$/min', value: 'R$ 0,90', goal: 'meta R$ 0,60', verdict: 'bom' },
      { name: 'Lucro líquido', value: 'R$ 13,60', goal: 'meta R$ 10,00', verdict: 'bom' },
    ],
  },
  medio: {
    price: 'R$ 14,50',
    time: '23 min',
    km: '7,2 km',
    metrics: [
      { name: 'R$/km', value: 'R$ 2,01', goal: 'meta R$ 2,20', verdict: 'medio' },
      { name: 'R$/hora', value: 'R$ 37,83', goal: 'meta R$ 40,00', verdict: 'medio' },
      { name: 'R$/min', value: 'R$ 0,63', goal: 'meta R$ 0,60', verdict: 'bom' },
      { name: 'Lucro líquido', value: 'R$ 8,88', goal: 'meta R$ 10,00', verdict: 'medio' },
    ],
  },
  ruim: {
    price: 'R$ 8,40',
    time: '31 min',
    km: '9,6 km',
    metrics: [
      { name: 'R$/km', value: 'R$ 0,88', goal: 'meta R$ 2,20', verdict: 'ruim' },
      { name: 'R$/hora', value: 'R$ 16,26', goal: 'meta R$ 40,00', verdict: 'ruim' },
      { name: 'R$/min', value: 'R$ 0,27', goal: 'meta R$ 0,60', verdict: 'ruim' },
      { name: 'Lucro líquido', value: 'R$ 0,91', goal: 'meta R$ 10,00', verdict: 'ruim' },
    ],
  },
}

/** Sempre escuro (classe "dark"), independente do tema do app. */
export function OfferCard({ verdict }: { verdict: Verdict }) {
  const v = VERDICT[verdict]
  const offer = OFFERS[verdict]

  return (
    <section
      aria-label={`Oferta de corrida: ${v.label}`}
      className={cn(
        'dark overflow-hidden rounded-[28px] border-4 bg-card text-foreground shadow-2xl',
        v.border,
      )}
    >
      <div className="flex items-center justify-between gap-3 px-4 pt-4">
        <span
          className={cn(
            'inline-flex min-h-11 items-center gap-2 rounded-full px-4 text-xl font-bold text-on-verdict',
            v.fill,
          )}
        >
          <Icon name={v.icon} size={26} fill />
          {v.label}
        </span>
        <span className="text-lg font-bold text-muted-foreground">
          UberX · Uber
        </span>
      </div>

      <div className="flex items-end justify-between gap-3 px-4 pb-4 pt-3">
        <p className="text-[52px] font-bold leading-none tabular-nums">
          {offer.price}
        </p>
        <div className="flex flex-col items-end gap-1 text-xl font-bold tabular-nums">
          <span className="inline-flex items-center gap-1.5">
            <Icon name="schedule" size={24} />
            {offer.time}
          </span>
          <span className="inline-flex items-center gap-1.5">
            <Icon name="route" size={24} />
            {offer.km}
          </span>
        </div>
      </div>

      <ul className="divide-y divide-border border-t border-border">
        {offer.metrics.map((m) => (
          <li
            key={m.name}
            className="flex min-h-16 items-center gap-3 px-4 py-2"
          >
            <VerdictDot verdict={m.verdict} size={32} />
            <span className="flex-1 text-xl font-bold">{m.name}</span>
            <span className="text-right">
              <span className="block text-2xl font-bold leading-tight tabular-nums">
                {m.value}
              </span>
              <span className="block text-base leading-tight text-muted-foreground">
                {m.goal}
              </span>
            </span>
          </li>
        ))}
      </ul>
    </section>
  )
}
