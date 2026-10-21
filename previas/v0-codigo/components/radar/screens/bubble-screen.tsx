import { cn } from '@/lib/utils'
import { Bubble, BUBBLE_TEXT, type BubbleState } from '../bubble'
import { Icon } from '../icon'
import { RideBackdrop } from '../ride-backdrop'
import type { ScreenProps } from '../config'

const ORDER: BubbleState[] = ['ligado', 'desligado', 'aviso']

export function BubbleScreen({ variant, setVariant }: ScreenProps) {
  const state = variant as BubbleState
  const text = BUBBLE_TEXT[state]

  const handleTap = () => setVariant(state === 'ligado' ? 'desligado' : 'ligado')

  return (
    <RideBackdrop showSheet={false}>
      <div className="absolute right-3 top-28 flex items-center gap-3">
        <div
          role="status"
          className="dark rounded-2xl border-2 border-border bg-card px-4 py-2 text-foreground shadow-xl"
        >
          <p className="flex items-center gap-2 text-lg font-bold">
            {state === 'aviso' && <Icon name="warning" size={22} fill />}
            {text.short}
          </p>
          <p className="text-base text-muted-foreground">{text.long}</p>
        </div>
        <Bubble state={state} size={72} onClick={handleTap} />
      </div>

      <div className="absolute inset-x-4 bottom-8 rounded-3xl border border-border bg-card p-4 shadow-xl">
        <h2 className="text-xl font-bold">Estados da bolinha</h2>
        <p className="text-base text-muted-foreground">
          Toque na bolinha para ligar ou desligar.
        </p>
        <ul className="mt-3 grid grid-cols-3 gap-2">
          {ORDER.map((s) => (
            <li key={s}>
              <button
                type="button"
                onClick={() => setVariant(s)}
                aria-pressed={s === state}
                className={cn(
                  'flex min-h-28 w-full flex-col items-center justify-center gap-2 rounded-2xl border-2 px-1 py-3 text-center text-base font-bold leading-tight focus-visible:outline-2 focus-visible:outline-accent-text',
                  s === state
                    ? 'border-primary bg-surface-2'
                    : 'border-transparent bg-surface-2',
                )}
              >
                <Bubble state={s} size={52} />
                {BUBBLE_TEXT[s].short}
              </button>
            </li>
          ))}
        </ul>
      </div>
    </RideBackdrop>
  )
}
