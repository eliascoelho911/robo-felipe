# 02: Core Batch endpoint + Plano de Ações

**What to build:** O Core ganha o endpoint `POST /batch` que recebe um
Batch (envelope versionado com Triggers, conforme `packages/contract/`) e
retorna um Plano de Ações (lista ordenada de Actions + snapshot de estado
do pet). O Batch é o contrato canônico Plataforma→Core (ADR-018): a
Plataforma detecta Triggers não-vozeados (sacudida, botão, manual) e os
envia em lote; o Core decide o comportamento e responde com Ações para a
Plataforma executar.

Para cada Trigger no Batch, o Core:
1. Roda `advanceStats` on-demand (decay por timestamp decorrido desde
   `last_tick`, em tiers: daily/weekly/monthly/very-slow, conforme ADR-023).
2. Mapeia o Trigger para um Plano de Ações (ex.: `shake` →
   `[get_dizzy]` ou `[express_emotion{scared}]`; `button` → `[speak{oi!}]`;
   `manual` → snapshot only).
3. Atualiza `last_tick` e persiste o estado.

O Plano de Ações segue o `packages/contract/` (`PlanoDeAcoes`: `version`,
`batchId`, `actions[]`, `state?`). As 5 Action types são: `speak`
({type, text}), `dance` ({type}), `express_emotion` ({type, emotion}),
`get_dizzy` ({type}), `sleep` ({type}). O `state` no Plano inclui o
snapshot atualizado (stage, mood, health, sickness, age_days, stats,
last_interaction).

O mood é derivado das stats (13 moods, prioridade top-down conforme
ADR-023: doente, dormindo, faminto, exausto, triste, sujo, tonto,
assustado, brincalhão, curioso, carinhoso, travesso, feliz).

**Blocked by:** 01 (Alinhar contract + refactor Core HTTP REST) — precisa
do contrato alinhado e das rotas HTTP base funcionando.

**Status:** ready-for-agent

- [ ] `POST /batch` implementado em `core/src/app.ts`, recebe Batch (Zod-validated via `packages/contract/`), retorna PlanoDeAções.
- [ ] `advanceStats(elapsedSeconds)` implementado: aplica decay por tier (daily ~-15/dia, weekly ~-10/semana, monthly ~-5/mês, very-slow ~-2/mês) para cada stat, atualiza `last_tick`.
- [ ] Trigger→Plano mapping implementado para os 3 kinds da Fase 1: `shake` → `[get_dizzy]` (ou `[express_emotion{scared}]` se courage baixo), `button` → `[speak{oi!}]`, `manual` → snapshot only (sem Actions).
- [ ] Mood derivation implementado (13 moods, prioridade top-down do ADR-023).
- [ ] Snapshot `state` incluído no Plano de Ações retornado.
- [ ] Testes Vitest cobrem: Batch com 1 Trigger, Batch com múltiplos Triggers, advanceStats com elapsed=0 (no-op), elapsed grande (decay aplicado).
- [ ] `just core-test` passa.
- [ ] Demoable: `curl -X POST localhost:3000/batch -d '{"version":1,"batchId":"...","platformId":"android-1","petId":"test","triggers":[{"id":"...","kind":"shake","timestamp":"2026-08-31T12:00:00Z"}]}'` retorna `{"version":1,"batchId":"...","actions":[{"type":"get_dizzy"}],"state":{...}}`.
