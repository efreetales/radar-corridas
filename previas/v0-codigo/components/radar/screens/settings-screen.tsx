'use client'

import { useEffect, useRef, useState } from 'react'
import { cn } from '@/lib/utils'
import { Icon } from '../icon'
import {
  ActionButton,
  ErrorState,
  Group,
  LoadingBlock,
  NavRow,
  ScreenHeader,
  Segmented,
  ToggleRow,
} from '../ui'
import type { ScreenProps, ThemeMode } from '../config'

export function SettingsScreen({
  variant,
  radarOn,
  toggleRadar,
  go,
  retry,
  themeMode,
  setThemeMode,
}: ScreenProps) {
  const [voice, setVoice] = useState({ board: true, arrive: true })
  const [mapOpts, setMapOpts] = useState({
    valetas: true,
    radares: true,
    bons: true,
    eventos: true,
  })
  const [diagnostic, setDiagnostic] = useState(false)
  const [position, setPosition] = useState<'cima' | 'meio' | 'baixo'>('cima')
  const [opacity, setOpacity] = useState(80)
  const [playing, setPlaying] = useState(false)
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => () => {
    if (timer.current) clearTimeout(timer.current)
  }, [])

  const playExample = () => {
    setPlaying(true)
    timer.current = setTimeout(() => setPlaying(false), 2500)
  }

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <ScreenHeader title="Ajustes" />
      <div className="min-h-0 flex-1 overflow-y-auto px-4 pb-8">
        {variant === 'carregando' && (
          <LoadingBlock label="Carregando ajustes…" rows={4} />
        )}
        {variant === 'erro' && (
          <ErrorState
            title="Não deu para carregar os ajustes"
            onRetry={retry}
          />
        )}
        {variant === 'normal' && (
          <>
            <section
              aria-label="Estado da leitura de ofertas"
              className={cn(
                'flex items-center gap-3 rounded-2xl border-[3px] bg-card p-3 pl-4',
                radarOn ? 'border-primary' : 'border-warn',
              )}
            >
              <span
                className={cn(
                  'grid size-12 shrink-0 place-items-center rounded-full',
                  radarOn
                    ? 'bg-primary text-primary-foreground'
                    : 'bg-warn text-on-verdict',
                )}
              >
                <Icon
                  name={radarOn ? 'check_circle' : 'visibility_off'}
                  size={28}
                  fill
                />
              </span>
              <p className="min-w-0 flex-1 text-lg font-bold leading-snug">
                {radarOn
                  ? 'Leitura de ofertas ativa'
                  : 'Leitura de ofertas desligada'}
              </p>
              <ActionButton
                variant={radarOn ? 'secondary' : 'primary'}
                onClick={toggleRadar}
                className="w-auto px-5"
              >
                {radarOn ? 'Desligar' : 'Ligar'}
              </ActionButton>
            </section>

            <button
              type="button"
              className="mt-3 flex min-h-16 w-full items-center gap-3 rounded-2xl bg-surface-2 px-4 py-3 text-left focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent-text"
            >
              <Icon name="system_update" size={28} className="text-accent-text" />
              <span className="flex-1 text-lg font-bold leading-snug">
                Versão 0.1.85 · toque para baixar e instalar
              </span>
            </button>

            <Group title="Cartão e ganhos">
              <NavRow icon="style" title="Estilo do cartão" value="Completo" />
              <NavRow icon="palette" title="Faixas de cor" value="Bom, médio e ruim" />
              <NavRow icon="preview" title="Prévia do cartão" />
              <NavRow
                icon="play_circle"
                title="Testar cartão na tela"
                onClick={() => go('oferta')}
              />
            </Group>

            <Group title="Voz com o passageiro">
              <ToggleRow
                icon="hail"
                title="Quando embarca"
                description="fala uma boas-vindas"
                checked={voice.board}
                onChange={(v) => setVoice((s) => ({ ...s, board: v }))}
              />
              <ToggleRow
                icon="flag"
                title="Quando chega ao destino"
                description="fala uma despedida"
                checked={voice.arrive}
                onChange={(v) => setVoice((s) => ({ ...s, arrive: v }))}
              />
              <NavRow icon="graphic_eq" title="Voz de estúdio" value="Camila" />
              <NavRow icon="queue_music" title="Mais vozes" />
              <NavRow
                icon={playing ? 'stop_circle' : 'volume_up'}
                title="Ouvir exemplo"
                value={playing ? 'Tocando…' : undefined}
                chevron={false}
                onClick={playExample}
              />
            </Group>

            <Group title="Mapa">
              <ToggleRow
                icon="warning"
                title="Valetas e buracos"
                checked={mapOpts.valetas}
                onChange={(v) => setMapOpts((s) => ({ ...s, valetas: v }))}
              />
              <ToggleRow
                icon="speed"
                title="Radares de velocidade"
                checked={mapOpts.radares}
                onChange={(v) => setMapOpts((s) => ({ ...s, radares: v }))}
              />
              <ToggleRow
                icon="thumb_up"
                title="Pontos bons"
                checked={mapOpts.bons}
                onChange={(v) => setMapOpts((s) => ({ ...s, bons: v }))}
              />
              <ToggleRow
                icon="event"
                title="Eventos de hoje"
                checked={mapOpts.eventos}
                onChange={(v) => setMapOpts((s) => ({ ...s, eventos: v }))}
              />
            </Group>

            <Group title="Custos e gastos">
              <NavRow icon="directions_car" title="Custos do carro" value="R$ 0,23 por km" />
              <NavRow icon="local_gas_station" title="Km por litro" value="11,2 km/l" />
              <NavRow icon="paid" title="Custo real por km" value="R$ 0,78 por km" />
            </Group>

            <Group title="Aparência">
              <div className="p-4">
                <p className="pb-2 text-lg font-bold">Tema</p>
                <Segmented<ThemeMode>
                  label="Tema do aplicativo"
                  value={themeMode}
                  onChange={setThemeMode}
                  options={[
                    { id: 'light', label: 'Claro', icon: 'light_mode' },
                    { id: 'dark', label: 'Escuro', icon: 'dark_mode' },
                    { id: 'auto', label: 'Automático', icon: 'brightness_auto' },
                  ]}
                />
              </div>
              <div className="p-4">
                <p className="pb-2 text-lg font-bold">Posição do card</p>
                <Segmented<'cima' | 'meio' | 'baixo'>
                  label="Posição do cartão na tela"
                  value={position}
                  onChange={setPosition}
                  options={[
                    { id: 'cima', label: 'Em cima', icon: 'vertical_align_top' },
                    { id: 'meio', label: 'No meio', icon: 'vertical_align_center' },
                    { id: 'baixo', label: 'Embaixo', icon: 'vertical_align_bottom' },
                  ]}
                />
              </div>
              <div className="p-4">
                <label
                  htmlFor="opacidade"
                  className="flex items-center justify-between text-lg font-bold"
                >
                  Opacidade da bolinha
                  <span className="tabular-nums">{opacity}%</span>
                </label>
                <input
                  id="opacidade"
                  type="range"
                  min={30}
                  max={100}
                  step={5}
                  value={opacity}
                  onChange={(e) => setOpacity(Number(e.target.value))}
                  className="big-range mt-1"
                />
              </div>
            </Group>

            <Group title="Dados">
              <NavRow icon="history" title="Histórico de ofertas" />
              <NavRow icon="upload_file" title="Importar correção" />
              <ToggleRow
                icon="bug_report"
                title="Modo diagnóstico"
                description="só para falar com o suporte"
                checked={diagnostic}
                onChange={setDiagnostic}
              />
            </Group>
          </>
        )}
      </div>
    </div>
  )
}
