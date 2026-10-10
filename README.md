# Radar Corridas

App Android pessoal que lê a oferta de corrida da Uber (e da 99) e mostra na hora, num cartão sobre a tela, se a corrida compensa: R$/hora, R$/min, R$/km, nota do passageiro e lucro, cada um em vermelho, amarelo ou verde conforme as faixas que você define.

O app **só lê** a tela. Ele nunca aceita, recusa ou toca em nada no app de corrida.

## Como funciona

- **Leitura da oferta:** um serviço de acessibilidade lê os textos da tela da Uber/99 (valor, "4 min (1.1 km)", "12 minutos (5.9 km)", nota). A busca e a viagem entram juntas na conta, como no GigU.
- **Cartão:** aparece sobre a tela usando a camada de acessibilidade, sem precisar da permissão "sobrepor a outros apps". A borda mostra o veredito geral.
- **Configuração:** faixas de ruim/bom para cada métrica, custos do carro, posição e opacidade do cartão, botão flutuante.
- **Dados:** cada oferta vista vai para `ofertas.csv` (exportável), com endereços de origem e destino. Base para uma nova versão do Zonas.
- **Diagnóstico:** registra os textos lidos para ajustar a leitura quando algo não for reconhecido.

## Pedido para o Claude Code subir o projeto

Descompacte o .zip e peça ao Claude Code, na pasta do projeto:

> Suba este projeto para o repositório efreetales/radar-corridas na branch main (pode substituir o conteúdo atual). Depois acompanhe o GitHub Actions "Gerar APK". Se o build falhar, leia o log de erro, corrija o código e envie de novo até o build passar.

## Instalar no celular

1. Quando o build passar, abra no celular: `https://github.com/efreetales/radar-corridas/releases/latest`
2. Baixe `RadarCorridas.apk` e abra. Permita instalar apps desta fonte quando o Android pedir.
3. Abra o Radar Corridas e toque em **Ativar leitura de ofertas**. Em Acessibilidade, ative o Radar Corridas.
4. Se a opção aparecer bloqueada ("configuração restrita"): Configurações → Apps → Radar Corridas → menu ⋮ → **Permitir configurações restritas**. Volte e ative.
5. Toque em **Testar cartão na tela** para ver o cartão funcionando.

Versões novas instalam por cima e mantêm as configurações (a chave de assinatura é fixa).

## Arquivos principais

| Arquivo | O que faz |
| --- | --- |
| `RadarService.kt` | Observa a tela da Uber/99, lê a oferta e mostra o cartão |
| `OfferParser.kt` | Transforma os textos da tela em valor, tempos, distâncias e nota |
| `Evaluator.kt` | Calcula as métricas e o veredito |
| `CardView.kt` | Desenho do cartão |
| `OverlayManager.kt` | Mostra o cartão e o botão flutuante sobre outros apps |
| `MainActivity.kt` | Tela de configuração |
| `OfferLog.kt` | Salva ofertas (CSV) e diagnóstico |
