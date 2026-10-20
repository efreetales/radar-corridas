# Radar Corridas

O dono do projeto (Tales) é designer e não programa: explique em português simples e diga o que muda para quem usa o app.

## Economia de uso

- O modelo padrão deste projeto é `opusplan`: Opus para planejar, Sonnet para executar.
- Delegue o trabalho braçal aos ajudantes em Haiku (`.claude/agents/`), em vez de fazer na conversa principal:
  - `buscador`: localizar algo no código antes de editar
  - `verificador-build`: acompanhar o GitHub Actions "Gerar APK" após cada push e trazer só o erro
  - `resumidor`: explicar mudanças ou condensar logs e textos longos
- Tarefas simples não precisam de plano: vá direto à execução.

## Design

- O app é usado dirigindo: textos grandes, alvos de toque grandes, bastante respiro; só textos explicativos podem ser pequenos.
- Ícones: Material Icons/Symbols neste projeto. Nunca use caracteres/emojis (✓ ✕ ★ ⚠ ›) como ícone.
