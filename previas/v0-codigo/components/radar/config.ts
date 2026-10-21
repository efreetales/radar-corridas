export type ScreenId =
  | 'oferta'
  | 'inicio'
  | 'ajustes'
  | 'ganhos'
  | 'mapa'
  | 'bolinha'
  | 'avisos'

export type Verdict = 'bom' | 'medio' | 'ruim'
export type ThemeMode = 'dark' | 'light' | 'auto'

export type ScreenProps = {
  variant: string
  setVariant: (v: string) => void
  go: (s: ScreenId) => void
  retry: () => void
  radarOn: boolean
  toggleRadar: () => void
  themeMode: ThemeMode
  setThemeMode: (m: ThemeMode) => void
  resolvedTheme: 'dark' | 'light'
}

export type Variant = { id: string; label: string; icon: string }

export const SCREENS: {
  id: ScreenId
  matches: ScreenId[]
  label: string
  hint: string
  icon: string
}[] = [
  {
    id: 'oferta',
    matches: ['oferta'],
    label: 'Cartão da oferta',
    hint: 'Sobre o app da Uber',
    icon: 'request_quote',
  },
  {
    id: 'inicio',
    matches: ['inicio', 'ajustes'],
    label: 'Menu principal',
    hint: 'Início e Ajustes',
    icon: 'menu',
  },
  {
    id: 'ganhos',
    matches: ['ganhos'],
    label: 'Painel de ganhos',
    hint: 'Turnos, números e gastos',
    icon: 'payments',
  },
  {
    id: 'mapa',
    matches: ['mapa'],
    label: 'Mapa',
    hint: 'Valetas, radares e eventos',
    icon: 'map',
  },
  {
    id: 'bolinha',
    matches: ['bolinha'],
    label: 'Bolinha flutuante',
    hint: 'Liga e desliga o radar',
    icon: 'adjust',
  },
  {
    id: 'avisos',
    matches: ['avisos'],
    label: 'Avisos na rua',
    hint: 'Radar, valeta e versão',
    icon: 'notifications_active',
  },
]

const LOAD_ERR: Variant[] = [
  { id: 'carregando', label: 'Carregando', icon: 'hourglass_top' },
  { id: 'erro', label: 'Erro', icon: 'cloud_off' },
]

export const VARIANTS: Record<ScreenId, Variant[]> = {
  oferta: [
    { id: 'bom', label: 'Bom', icon: 'check_circle' },
    { id: 'medio', label: 'Médio', icon: 'error' },
    { id: 'ruim', label: 'Ruim', icon: 'cancel' },
  ],
  inicio: [
    { id: 'ativa', label: 'Ativa', icon: 'visibility' },
    { id: 'desligada', label: 'Desligada', icon: 'visibility_off' },
    ...LOAD_ERR,
  ],
  ajustes: [{ id: 'normal', label: 'Normal', icon: 'tune' }, ...LOAD_ERR],
  ganhos: [
    { id: 'normal', label: 'Com dados', icon: 'payments' },
    { id: 'vazio', label: 'Vazio', icon: 'inbox' },
    ...LOAD_ERR,
  ],
  mapa: [
    { id: 'normal', label: 'Com dados', icon: 'map' },
    { id: 'vazio', label: 'Vazio', icon: 'inbox' },
    ...LOAD_ERR,
  ],
  bolinha: [
    { id: 'ligado', label: 'Ligado', icon: 'power_settings_new' },
    { id: 'desligado', label: 'Desligado', icon: 'block' },
    { id: 'aviso', label: 'Com aviso', icon: 'warning' },
  ],
  avisos: [
    { id: 'radar', label: 'Radar', icon: 'speed' },
    { id: 'valeta', label: 'Valeta', icon: 'warning' },
    { id: 'versao', label: 'Nova versão', icon: 'system_update' },
  ],
}

export function defaultVariant(screen: ScreenId, radarOn: boolean): string {
  if (screen === 'inicio') return radarOn ? 'ativa' : 'desligada'
  if (screen === 'bolinha') return radarOn ? 'ligado' : 'desligado'
  return VARIANTS[screen][0].id
}

export const NOTES: Record<
  'oferta' | 'menu' | 'ganhos' | 'mapa' | 'bolinha' | 'avisos',
  { title: string; items: string[] }
> = {
  oferta: {
    title: 'Cartão da oferta',
    items: [
      'A resposta vem antes dos números: uma etiqueta grande com palavra e ícone (Corrida boa, média ou ruim) e o valor em 48px. Dá para ler em 1 segundo.',
      'A borda de 4dp usa a cor do veredito geral. Cada métrica tem uma bolinha com ícone dentro (check, traço, X), então ninguém depende só de cor.',
      'Cada linha mostra o valor e a meta ao lado. O motorista vê o quanto passou ou faltou sem fazer conta.',
      'O cartão é sempre escuro, mesmo com o app no tema claro, porque fica sobre o mapa da Uber e precisa do mesmo contraste à noite e de dia.',
    ],
  },
  menu: {
    title: 'Menu principal e ajustes',
    items: [
      'Início responde só uma pergunta: o radar está lendo as ofertas? O estado é um cartão grande, com a ação principal logo abaixo (Ligar leitura ou Abrir a Uber).',
      'Se a leitura estiver desligada, o cartão fica amarelo, com ícone de olho cortado e a palavra desligada. O botão para religar é o maior da tela.',
      'Em Ajustes, os grupos seguem a ordem de uso: cartão, voz, mapa, custos, aparência, dados. Cada linha tem pelo menos 64px de altura.',
      'Os interruptores mostram um ícone (check ou X) dentro do botão. O estado não depende só da cor.',
    ],
  },
  ganhos: {
    title: 'Painel de ganhos',
    items: [
      'O número do dia (total e tempo online) fica no topo. Os detalhes vêm depois, do mais usado para o menos usado.',
      'A única tarefa da tela é Para confirmar. Por isso há um aviso no topo com atalho, e a confirmação leva 1 toque (Sim ou Não).',
      'O custo real por km tem um número grande e uma barra com legenda em texto. O motorista entende onde o dinheiro vai.',
      'Entradas aparecem com + e saídas com −, além do ícone. O tom de cor sozinho não diz se o dinheiro entrou ou saiu.',
    ],
  },
  mapa: {
    title: 'Mapa',
    items: [
      'A barra de ação fica fixa embaixo: destino escolhido e dois botões de 64px, Ir com Waze (principal) e Ir com Uber. Não é preciso rolar para achar.',
      'Cada tipo de marcador tem cor e ícone próprios (valeta, radar, ponto bom, evento), e a legenda usa as mesmas formas.',
      'A lista Eventos de hoje funciona como seletor de destino: tocar num evento move o mapa e atualiza os botões.',
      'Sem dados, a tela diz o que falta e oferece a ação certa: Nenhuma valeta marcada ainda, com o botão Marcar valeta aqui.',
    ],
  },
  bolinha: {
    title: 'Bolinha flutuante',
    items: [
      'Três estados que diferem pela forma, não só pela cor: verde com check (ligado), cinza com símbolo de desligar (desligado) e contorno amarelo com triângulo de alerta (leitura desligada).',
      'Ao lado da bolinha, uma etiqueta de texto diz o estado em palavras. Ela sai sozinha depois de alguns segundos para não tapar a Uber.',
      'Um toque liga ou desliga. No estado de aviso, o toque religa a leitura direto, sem abrir o app.',
      'Alvo de 64px, bem maior que o mínimo, porque ela é tocada com o carro em movimento.',
    ],
  },
  avisos: {
    title: 'Avisos na rua',
    items: [
      'Cada aviso tem um ícone grande num quadrado colorido, uma frase curta com distância e uma ação. Palavra, ícone e cor sempre aparecem juntos.',
      'Radar mostra o limite e a velocidade atual lado a lado. A barra de tempo no rodapé mostra que o aviso some sozinho.',
      'Valeta pede uma confirmação rápida (Ainda está lá / Não está mais). É o motorista quem mantém o mapa em dia.',
      'A nova versão é o aviso menos urgente: tem botão Depois no mesmo tamanho do principal e nunca cobre um radar.',
    ],
  },
}

export const PRINCIPLES = [
  'Corpo de texto a partir de 16px, valores a partir de 20px. Só explicações curtas usam texto menor.',
  'Alvos de toque de 56px ou mais, com o botão principal em 64px.',
  'Verdes, amarelos e vermelhos são usados em fundos, bordas e bolinhas, nunca como cor de texto. Assim o contraste passa no tema claro também.',
  'Todos os ícones são Material Symbols. Nenhum caractere ou emoji faz papel de ícone.',
]
