AJUSTE IMPORTANTE: contexto real de como os avisos aparecem no celular. Os avisos NÃO são telas do app: são pequenas camadas flutuantes desenhadas POR CIMA do app da Uber Driver (aberto em tela cheia, com mapa). O motorista olha de relance, dirigindo. Os avisos precisam ser COMPACTOS e deixar o mapa e os botões da Uber visíveis. Refaça o cartão da oferta, o aviso de radar, o aviso "indo para" e a bolinha seguindo estas medidas reais (celular de ~390dp de largura; 1dp = 1px no protótipo 390x844).

PLANO DE FUNDO DAS TELAS DE AVISO
Mostre cada aviso sobre uma simulação simples do app da Uber Driver: mapa em tela cheia (cinza/claro de dia e escuro à noite), barra de status do Android no topo (~36px), no topo a faixa de navegação da Uber, e embaixo o painel da Uber (botão grande de aceitar/ficar online). Não invente detalhes de marca; use formas neutras. Os avisos ficam por cima, sem esconder o botão de aceitar da Uber.

MEDIDAS REAIS (em dp)
1) CARTÃO DA OFERTA
- Largura: SÓ o necessário para o conteúdo (wrap), centralizado na horizontal, no máximo ~85% da largura. NÃO ocupa a tela toda.
- Posição vertical escolhida pelo motorista: topo (120 abaixo do topo), meio (centro) ou embaixo (140 acima da base). Padrão: topo.
- Fundo preto quase opaco (#000000 a 95%), cantos 18, borda 4 na cor do veredito, padding 16 horizontal e 12 vertical.
- Linha de cima: 20sp, negrito, separada por " · " (ex.: R$ 14,50 · 23 min · 7,2 km · UberX).
- Linhas de métricas: bolinha/barra colorida 5x28, nome 15sp, valor 26sp. No máximo 3 a 4 linhas.
- Linha final de nota: 17sp. O cartão inteiro tem ~110 a 150 de altura, nunca mais que ~20% da tela.
- NÃO recebe toque (o toque passa direto para a Uber). Aparece enquanto a oferta da Uber está na tela e some sozinho quando ela some. Sem botões, sem animação chamativa.

2) AVISO DE RADAR DE VELOCIDADE (e de valeta/ponto bom)
- Faixa no TOPO, largura = tela menos 24 (12 de cada lado), altura ~76 a 96, a 36 do topo da tela, cantos 18, sombra pequena.
- Esquerda: placa redonda 54x54 com o limite (20 a 26sp, borda 5 vermelha ou laranja). Centro: título 24sp (ex.: "Radar 60") e linha 13sp (ex.: "a 300 m · você: 72 km/h"). Velocidade atual 38sp (ou 24 se houver ação).
- Coluna de ação à direita, 78 de largura: dois botões pequenos "Waze" e "Uber" (14sp) separados por linha fina.
- Cor de fundo conforme a velocidade: verde escuro (dentro do limite), amarelo (acima do limite), vermelho (acima da tolerância, 7 km/h até 100 km/h ou 7% acima).
- Gestos (mostrar só como dica em texto pequeno fora do aviso): arrastar 80 para o lado dispensa; segurar 1 s marca "radar errado". Depois de passar, mostra o resultado por 3 s e some.

3) AVISO "INDO PARA" (deslocamento a evento/ponto)
- Pílula no topo, largura = tela menos 48, a 36 do topo, texto 13sp numa linha, padding 14 e 8, cantos 20, cor sólida roxa (#5232B8) ou vinho (#9C1B4A). Altura ~36. Pequeno e discreto.

4) BOLINHA "R"
- 56x56, letra R 22sp, arrastável pelo motorista, transparência ajustável (20% a 100%), cadeado 20x20 no canto inferior direito quando travada. Toque longo (0,6 s) abre um menu em pílula (fundo #161B22 a 90%, cantos 36) com botões de 56 e rótulo 12sp.

REGRAS
- Os avisos são pequenos de propósito: o motorista dirige e a Uber continua visível. Nunca cubra mais de ~20% da altura da tela com um aviso, e nunca use tela cheia, modal ou fundo escurecido.
- Mantenha as regras anteriores (Material Symbols, sem emojis, contraste alto, tema escuro e claro nas telas do app; os avisos são sempre escuros).

PENDENTE DA RODADA ANTERIOR: crie também a tela MAPA (marcadores de valetas, radares, pontos bons e eventos; botões grandes "Ir com Waze" e "Ir com Uber"; lista "Eventos de hoje") e a página principal (app/page.tsx) com o seletor de telas e de tema, para a prévia abrir funcionando.
