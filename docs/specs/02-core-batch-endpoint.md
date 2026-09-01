# Spec 02: Core Batch endpoint + Plano de Ações

**Ticket:** [02 — Core Batch endpoint + Plano de Ações](../tickets/02-core-batch-endpoint-plano-de-acoes.md)
**Blocked by:** [Spec 01](01-contract-core-rest.md)
**Status:** ready-for-agent

## Problem Statement

A Plataforma (app Android hoje, CoreS3 amanhã) precisa enviar Triggers
não-vozeados (button, shake, manual) ao Core e receber de volta um Plano de
Ações ordenado para executar. Este é o **contrato canônico** que sobrevive à
troca de host (ADR-018) — o artefato que torna a Plataforma trocável sem
reescrever o Core. Hoje o scaffold do Core não tem `POST /batch`.

## Solution

O Core expõe `POST /batch` que recebe um Batch (envelope versionado com
Triggers), roda `advanceStats` on-demand (decay por tempo decorrido, tiers
daily/weekly/monthly/very-slow), mapeia cada Trigger para um conjunto de
Ações, e retorna um Plano de Ações com o snapshot do estado atualizado.

## User Stories

1. As a Plataforma (app Android), I want enviar um Batch com Triggers
   não-vozeados ao Core via `POST /batch`, so that eu receba um Plano de
   Ações para executar.
2. As a Plataforma, I want o Plano incluir o snapshot do estado atualizado,
   so that eu possa renderizar a UI (face, barras) sem um round-trip extra
   ao `GET /pet/:id/state`.
3. As a Core, I want `advanceStats(elapsedSeconds)` rodar on-demand ao
   receber um Batch, so that as stats decaiam por tempo decorrido desde o
   último tick — não por wall-clock com efeitos colaterais.
4. As a Core, I want o decay em 4 tiers (daily/weekly/monthly/very-slow)
   com rates distintos, so that o pet sinta "realista" (fome em horas,
   higiene em dias, maturidade em semanas).
5. As a Core, I want o `last_tick` atualizado e persistido após cada Batch,
   so que o próximo Batch calcule o decay apenas do tempo decorrido desde o
   último.
6. As a Plataforma, I want o Trigger `shake` mapear para `[get_dizzy]` ou
   `[express_emotion{scared}]`, so that o pet reaja a ser sacudido.
7. As a Plataforma, I want o Trigger `button` mapear para `[speak{oi!}]`,
   so that o pet saude ao toque.
8. As a Plataforma, I want o Trigger `manual` mapear para snapshot only
   (sem Ações), so que eu possa consultar estado sem disparar
   comportamento.
9. As a Core, I want o mood derivado das stats por prioridade top-down
   (13 moods ADR-023), so que o snapshot no Plano reflita o humor atual.
10. As a Core, I want o Batch com múltiplos Triggers processado em ordem,
    so que cada Trigger contribua para o Plano final.
11. As a desenvolvedor, I want o Batch validado pelo schema Zod do
    contract, so que Batches malformados sejam rejeitados com 400.
12. As a desenvolvedor, I want o Plano de Ações validado pelo schema Zod,
    so que a resposta seja sempre um Plano válido.
13. As a desenvolvedor, I want testes cobrindo Batch com 1 Trigger e
    múltiplos Triggers, so que eu saiba que o processamento está correto.
14. As a desenvolvedor, I want testes cobrindo advanceStats com elapsed=0
    (sem decay) e elapsed grande (muito decay), so que eu saiba que o
    decay funciona nos extremos.
15. As a desenvolvedor, I want o Core ser demoable via curl com um POST
    /batch, so that eu possa validar o fluxo sem Android.

## Implementation Decisions

### POST /batch

Recebe `Batch` (validado pelo schema Zod do `packages/contract/`):
`{version: 1, batchId: uuid, platformId: string, petId: string, triggers:
Trigger[]}`.

Retorna `PlanoDeAcoes`:
`{version: 1, batchId: uuid, actions: Action[], state: PetStateSnapshot}`.

### advanceStats on-demand (ADR-023)

Ao receber um Batch, o Core:
1. Carrega o estado persistido (stats, lastUpdatedMs, estágio).
2. Calcula `elapsed = now - lastUpdatedMs` (em segundos).
3. Aplica `decay(stats, elapsed)` — cada stat decai conforme seu tier:
   - daily: ~-15/dia (fullness, energy, happiness, playfulness)
   - weekly: ~-10/semana (cleanliness, fitness, comfort, affection, sociability)
   - monthly: ~-5/mês (maturity, curiosity, intelligence, courage, loyalty)
   - very-slow: ~-2/mês (serenity, fulfillment, focus, mischievousness)
4. Atualiza `lastUpdatedMs = now`.
5. Recalcula health (weighted average).
6. Persiste o novo estado.

`decay` é uma **função pura** (testável isoladamente): recebe stats +
elapsed, retorna stats decaídas. Não tem efeitos colaterais.

### Mapeamento Trigger → Ações

| Trigger kind | Ações geradas | Notas |
|:--|:--|:--|
| `shake` | `[get_dizzy]` ou `[express_emotion{scared}]` | Se courage baixo, scared; senão, dizzy |
| `button` | `[speak{text: "Oi! Que bom te ver!"}]` | Saudação fixa pt-BR |
| `manual` | `[]` (sem Ações, só snapshot) | Consulta de estado |
| `voice` | `[]` (sem Ações no Plano) | Voz passa pelo xiaozhi-server; o Batch voice só atualiza estado |

O Plano concatena as Ações de todos os Triggers em ordem. Se nenhum Trigger
gera Ações (ex: só `manual`), o Plano tem `actions: []` mas inclui `state`.

### Snapshot no Plano (state field)

O Plano sempre inclui `state: PetStateSnapshot`:
`{stage, mood, health, sickness, ageDays, stats, lastInteraction}`.
- `stage`: Filhote/Jovem/Adulto (derivado de maturity).
- `mood`: string (13 moods, prioridade top-down ADR-023).
- `health`: number (weighted average).
- `sickness`: number (0 se health > 30; escala se health baixo).
- `ageDays`: number (now - createdAt / dia).
- `stats`: record (17 stats editáveis).
- `lastInteraction`: string ISO 8601 (timestamp do Batch).

### MVP de moods (8 deriváveis)

`moodOf(stats)` deriva 8 moods de stats persistidas: hungry (fullness < 25),
tired (energy < 20), dirty (cleanliness < 20), playful (playfulness > 60),
curious (curiosity > 60), excited (happiness > 60), mischievous
(mischievousness > 60), happy (default). Os 5 restantes (sad, sleepy, bored,
dizzy, scared) precisam de sickness ou flags temporárias — não no MVP do Core
interno, mas o snapshot pode retorná-los se sickness > 0.

### Batch validation

O Batch é validado pelo `BatchSchema` do `packages/contract/` (Zod). Se
inválido, retorna 400 com erro de validação. `triggers` deve ter pelo menos
1 elemento. `petId` deve corresponder ao pet configurado no Core
(`CORE_PET_ID`).

### Persistência

Após processar o Batch, o Core persiste o estado atualizado (stats,
lastUpdatedMs) em SQLite. O `batchId` é idempotente — se o mesmo batchId for
recebido novamente, o Core retorna o Plano cached sem reprocessar (evita
duplo decay se a Plataforma reenviar).

## Testing Decisions

### O que faz um bom teste

Testar comportamento externo: dado um Batch com Triggers específicos, o
Plano de Ações retornado tem as Ações esperadas e o state reflete o decay.
`advanceStats`/`decay` é testada como função pura isoladamente.

### Seams de teste

1. **`core/src/pet/stats.test.ts`** (existe no scaffold): adicionar testes
   para `decay(stats, elapsed)` com elapsed=0 (sem decay), elapsed=3600
   (1h, decay daily), elapsed=86400 (1 dia, decay significativo), elapsed
   muito grande (30 dias, stats próximas de 0). Testar que cada tier decai
   na rate correta.
2. **`core/src/app.test.ts`** (novo ou existe): `POST /batch` via
   `app.request()` com `AppDeps` em memória. Testar:
   - Batch com 1 Trigger `button` → Plano com `[speak{oi!}]` + state.
   - Batch com 1 Trigger `shake` → Plano com `[get_dizzy]` ou
     `[express_emotion{scared}]` + state.
   - Batch com 1 Trigger `manual` → Plano com `[]` + state (snapshot only).
   - Batch com múltiplos Triggers → Plano com Ações concatenadas.
   - Batch inválido (sem triggers) → 400.
   - Batch com petId errado → 404 ou 400.
   - advanceStats: Batch após tempo decorrido → stats decaídas no state.
   - Idempotência: mesmo batchId → mesmo Plano (sem duplo decay).

### Prior art

- `core/src/pet/stats.test.ts` já existe (funções puras de stats).
- Hono testing: `app.request('POST /batch', { json: batch })`.

## Out of Scope

- Trigger `voice` processando áudio (voz passa pelo xiaozhi-server; o
  Batch voice só atualiza lastInteraction e estado).
- Trigger `proximity` e `rtc_wake` (Fase 2, CoreS3).
- Sickness persistido como stat separado (MVP deriva do health baixo).
- Flags temporárias para moods dizzy/scared (não no MVP do Core).
- Scheduler/cron para advanceStats periódico (MVP é on-demand).
- Fallback NVS no device (tópico futuro, ADR-023).

## Further Notes

- **Depende de Spec 01** — usa o contract (Batch, PlanoDeAcoes, Action,
  PetStateSnapshot) e as funções de stats (decay, healthOf, moodOf) definidas
  lá.
- **speak dual-path:** No fluxo de voz, o LLM gera o texto (natural,
  contextual) → EdgeTTS. O `speak` no Plano é usado só no fluxo não-vozeado
  (Core gera texto fixo, App renderiza TTS nativo Android pt-BR). Converge
  na Fase 2 (CoreS3 usa protocolo xiaozhi para TTS).
- **Idempotência por batchId:** se a Plataforma reenviar o mesmo Batch
  (ex: retry de rede), o Core não deve aplicar decay duas vezes. O Plano
  cached é retornado.
- **advanceStats on-demand** significa que se ninguém consulta o Core por
  dias, as stats só avançam na próxima consulta (decay retroativo). O
  resultado é correto, mas o Core não "sabe" o estado entre consultas.
- **Referências:** ADR-018 (contrato canônico), ADR-023 (decay tiers, moods,
  Trigger→Plano), ticket 02.