Redesenhe as telas de um app Android chamado "Radar Corridas", usado por motoristas de aplicativo (Uber/99) enquanto DIRIGEM. Gere um protótipo navegável em React/Tailwind, em português do Brasil, com uma moldura de celular (390x844) e uma barra para trocar entre as telas e entre tema escuro e claro.

REGRAS DE DESIGN (obrigatórias)
- Usado dirigindo: textos grandes (mínimo 16px no corpo, 20px+ em valores), alvos de toque grandes (mínimo 56px de altura), muito respiro. Só textos explicativos podem ser pequenos.
- Ícones: somente Material Symbols/Material Icons. NUNCA use caracteres ou emojis como ícone (✓ ✕ ★ ⚠ ›).
- Contraste alto (WCAG AA), sem depender só de cor: o veredito vermelho/amarelo/verde sempre vem com palavra e ícone.
- Paleta atual a respeitar: escuro fundo #0D1117, superfície #161B22 e #21262D, texto #E6EDF3, texto secundário #8B949E, destaque #2FD3A6, linhas #30363D. Claro fundo #F4F6F8, superfície #FFFFFF e #EEF1F4, texto #14181D, secundário #5F6B76, destaque #0E9F7A, linhas #DDE2E7. Veredito: vermelho #E5484D, amarelo #F5C518, verde #3DD68C.
- O público não é técnico: rótulos curtos e diretos.

TELAS A RECRIAR (conteúdo real do app)
1. CARTÃO DA OFERTA (aparece sobre o app da Uber, sempre escuro): borda de 4dp na cor do veredito geral (Ruim vermelho, Médio amarelo, Bom verde). Linha de cima configurável: valor (R$ 14,50), tempo (23 min), distância (7,2 km), categoria (UberX), app. Abaixo, linhas de métricas, cada uma com bolinha colorida do veredito, nome, valor e limite: R$/km, R$/hora, R$/min, lucro líquido. Mostrar 3 estados: Bom, Médio, Ruim. Deve ser legível em 1 segundo.
2. MENU PRINCIPAL / CONFIGURAÇÕES: status no topo ("Leitura de ofertas ativa" / "Leitura de ofertas desligada" com botão para religar), aviso de versão nova ("Versão 0.1.85 · toque para baixar e instalar"), e grupos: Cartão e ganhos (Estilo do cartão, Faixas de cor, Prévia do cartão, Testar cartão na tela), Voz com o passageiro (quando embarca, quando chega ao destino, voz de estúdio, mais vozes, ouvir exemplo), Mapa (valetas e buracos, radares de velocidade, pontos bons, eventos de hoje), Custos e gastos (custos do carro, km por litro, custo real por km), Aparência (tema claro/escuro/automático, posição do card, opacidade da bolinha), Dados (histórico de ofertas, importar correção, modo diagnóstico).
3. PAINEL DE GANHOS: título Radar; seções "Por turno" (várias sessões, total do dia, tempo online), "Seus números" (ganho bruto, líquido, R$/km, R$/hora), "Seu carro e seus gastos" (combustível, pix, tag), "Seu custo real por km", "Lançamentos do mês", e "Para confirmar" (corridas com dúvida para o motorista confirmar com 1 toque).
4. MAPA: mapa com marcadores de valetas, radares, pontos bons e eventos; botões grandes "Ir com Waze" e "Ir com Uber"; lista "Eventos de hoje" (saídas de shows e jogos, com horário e local).
5. BOLINHA FLUTUANTE "R" (liga/desliga o radar sobre a Uber) em estados: ligado, desligado, e com aviso de leitura desligada.
6. AVISOS NA RUA: alerta de radar de velocidade, aviso de valeta, "Nova versão do Radar".

O QUE PEÇO
- Hierarquia clara, uma ação principal por tela, navegação inferior simples (Início, Ganhos, Mapa, Ajustes).
- Estados vazios ("Nenhuma valeta marcada ainda"), de carregando e de erro com botão "Tentar de novo".
- Entregue as 6 telas nos dois temas, consistentes entre si, e comente brevemente as decisões de UX.
