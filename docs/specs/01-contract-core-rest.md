# Spec 01: Contract + Core REST

**Ticket:** [01 — Alinhar contract + refactor Core para HTTP REST](../tickets/01-align-contract-refactor-core-http-rest.md)
**Status:** ready-for-agent

## Problem Statement

O Core do Robô Felipe precisa expor o estado do pet e as tools de comportamento
para que a Plataforma (Android hoje, CoreS3 amanhã) e o adapter Python no
xiaozhi-server possam consultar e mutar o pet. Hoje o scaffold do Core usa o
MCP SDK, que não dá acesso ao `conn` do xiaozhi-server — o que impede o
adapter de enviar ações não-verbais ao device. O contrato Batch→Plano de
Ações (o artefato canônico que sobrevive à troca de host, ADR-018) ainda não
está alinhado com os 18 stats e 13 moods canônicos do ADR-023.

## Solution

O Core deixa de usar o MCP SDK e passa a expor estado e tools via **HTTP
REST** (Hono). O `packages/contract/` é alinhado ao ADR-023: 18 stats (17
editáveis + health derivado), 13 moods, 5 Action types, 4 Trigger kinds da
Fase 1. O adapter Python do xiaozhi-server chama o Core via HTTP e usa o
`conn` para enviar ações ao device — padrão que não funciona com MCP externo.

## User Stories

1. As a desenvolvedor do Core, I want o estado do pet exposto via HTTP REST,
   so that qualquer cliente (adapter Python, app Android, curl) possa
   consultar e mutar o pet sem SDK proprietário.
2. As a desenvolvedor do Core, I want 18 stats canônicos (17 editáveis +
   health derivado) no modelo, so that o pet tenha a depth de simulação
   definida no ADR-023.
3. As a desenvolvedor do Core, I want `health` derivado por weighted average,
   so que ninguém possa "curar" o pet editando health direto — só cuidando
   das stats contribuintes.
4. As a desenvolvedor do Core, I want asymptotic damping perto dos extremos
   nas mudanças de stat, so that o pet não atinja 0 ou 100 facilmente e a
   progressão seja nonlinear.
5. As a desenvolvedor do Core, I want 10 tools write (feed/play/rest/clean/
   cuddle/heal/train/dance/express_emotion/get_dizzy) expostas como POST,
   so that o adapter Python possa mutar o estado do pet via function calling.
6. As a desenvolvedor do Core, I want 2 tools read (get_state/get_mood)
   expostas como GET, so that o adapter e o app possam consultar estado e
   mood sem mutar.
7. As a desenvolvedor do adapter Python, I want chamar o Core via HTTP
   (httpx), so that eu possa usar `conn.websocket.send` para enviar ações
   ao device — impossível com MCP externo.
8. As a desenvolvedor do app Android, I want consultar o estado do pet via
   `GET /pet/:id/state`, so that eu possa renderizar a UI (face, barras de
   stats) com dados atuais.
9. As a desenvolvedor do app Android, I want o snapshot de estado incluir
   `stage`, `mood`, `health`, `sickness`, `ageDays`, `stats`,
   `lastInteraction`, so que eu possa renderizar tudo sem múltiplos round-trips.
10. As a desenvolvedor do contract, I want schemas Zod canônicos em
    `packages/contract/`, so que Core e Android consumam o mesmo contrato
    tipado.
11. As a desenvolvedor do contract, I want o enum `Emotion` com 13 moods
    alinhados ao ADR-023, so que não haja divergência entre contract e
    comportamento do pet.
12. As a desenvolvedor do contract, I want `TriggerKind` com os 4 tipos da
    Fase 1 (voice/shake/button/manual), so que o contrato seja extensível
    para Fase 2 (proximity/rtc_wake) sem bump de versão.
13. As a desenvolvedor do contract, I want `Action` como discriminated union
    com 5 types (speak/dance/express_emotion/get_dizzy/sleep), so que o Plano
    de Ações seja tipado e validável.
14. As a desenvolvedor do Core, I want o `PetState` interno não incluir
    `sickness` nem `ageDays` (só no snapshot de resposta), so que o estado
    persistido seja apenas stats + lastUpdatedMs + estágio.
15. As a desenvolvedor do Core, I want persistência em SQLite, so que o
    estado do pet sobreviva a restarts do Core.
16. As a desenvolvedor do Core, I want relógio injetável (`now: () => number`),
    so que os testes possam simular passagem de tempo sem `Date.now()` real.
17. As a desenvolvedor do Core, I want `mcp/` removido e `@modelcontextprotocol/sdk`
    removido de deps, so que o Core seja HTTP-only e mais leve.
18. As a desenvolvedor do Core, I want testes Vitest cobrindo stats, health,
    mood, tools e endpoints HTTP, so que regressões sejam pegadas no CI.
19. As a desenvolvedor, I want `just core-test` passando, so que eu saiba
    que o Core está íntegro antes de prosseguir.
20. As a desenvolvedor, I want o Core ser demoable via curl, so que eu possa
    validar o fluxo sem Android ou xiaozhi-server.

## Implementation Decisions

### Modelo de stats (18 stats canônicos do ADR-023)

17 stats editáveis + `health` derivado. Organizados em 4 grupos:

- **Físicos (6):** fullness (saciedade, daily), energy (energia, daily),
  cleanliness (higiene, weekly), fitness (disposição, weekly), comfort
  (conforto, weekly), health (saúde, **derivado**).
- **Emocionais (5):** happiness (felicidade, daily), playfulness (brincadeira,
  daily), affection (afeto, weekly), serenity (serenidade, very-slow),
  fulfillment (realização, very-slow).
- **Sociais (2):** sociability (sociabilidade, weekly), loyalty (lealdade,
  monthly).
- **Mentais/Personalidade (5):** curiosity (curiosidade, monthly),
  intelligence (inteligência, monthly), maturity (maturidade, monthly),
  courage (coragem, monthly), mischievousness (travessura, very-slow),
  focus (foco, very-slow).

**Correção do scaffold:** o scaffold removeu `happiness` e adicionou `focus`.
O ADR-023 lista `happiness` como stat E `focus` na fórmula de health. O
scaffold deve ter ambos (18 editáveis + health). `focus` está na fórmula de
health; `happiness` é stat emotional daily (NÃO na fórmula de health, é
contribuinte indireto via playfulness).

### Health derivado (weighted average, ADR-023)

```
health = 0.25*fullness + 0.20*fitness + 0.20*energy
       + 0.15*cleanliness + 0.05*comfort + 0.05*affection
       + 0.025*fulfillment + 0.025*focus + 0.025*intelligence
       + 0.025*playfulness
```

Clamped 0-100. `health` nunca é editado direto — recalculado após cada
mudança de stat.

### Asymptotic damping (ADR-023)

Para delta positivo: `delta_efetivo = delta * ((100 - current) / 100) ^ 0.7`.
Para delta negativo: `delta_efetivo = delta * (current / 100) ^ 0.7`.

### Estágios (ADR-023, sem morte)

`Filhote` (maturidade 0-30, decay 1.3×) → `Jovem` (30-70) → `Adulto`
(70-100, decay 0.8×). Evolução unidirecional. Sem morte, sem rebirth.

### Moods (13, ADR-023)

Derivados das stats por prioridade top-down: doente, dormindo, faminto,
exausto, triste, sujo, tonto, assustado, brincalhão, curioso, carinhoso,
travesso, feliz.

**MVP:** 8 moods são deriváveis de stats persistidas (hungry, tired, dirty,
playful, curious, excited, mischievous, happy). Os 5 restantes (sad, sleepy,
bored, dizzy, scared) precisam de sickness ou flags temporárias, que não
estão no MVP do Core interno — mas aparecem no snapshot de resposta se
sickness for não-zero.

### Emotion enum (contract, alinhado ao ADR-023)

13 valores: `happy, sad, sleepy, bored, excited, hungry, tired, dirty, dizzy,
scared, playful, curious, mischievous`. Mapeamento pt-BR→en no contract para
que o Core use nomes canônicos em inglês (código em inglês, ADR-023 usa pt-BR
para documentação).

### PetState interno vs snapshot de resposta

**Interno (persistido em SQLite):** `{petId, stats, lastUpdatedMs, estagio}`.
Não inclui `sickness` nem `ageDays` — sickness é derivado de health baixo
(não persistido no MVP); ageDays é calculado na resposta (now -
createdAt / dia).

**Snapshot de resposta (PetStateSnapshot no contract):** `{stage, mood,
health, sickness, ageDays, stats, lastInteraction}`. Inclui tudo que a UI
precisa.

### Schemas Zod canônicos (`packages/contract/`)

- `TriggerKind`: enum `['voice', 'shake', 'button', 'manual']` (Fase 1).
- `Trigger`: `{id: uuid, kind: TriggerKind, timestamp: epoch_ms, payload:
  record}`.
- `Batch`: `{version: literal(1), batchId: uuid, platformId: string, petId:
  string, triggers: array min 1}`.
- `Emotion`: enum 13 valores (alinhado ao ADR-023).
- `Action`: discriminated union por `kind` — `speak({kind, text})`,
  `dance({kind, durationMs?})`, `express_emotion({kind, emotion})`,
  `get_dizzy({kind, intensity?})`, `sleep({kind, durationMs?})`.
- `Stage`: enum `['Filhote', 'Jovem', 'Adulto']`.
- `PetStateSnapshot`: `{stage, mood: string, health: number, sickness:
  number, ageDays: number, stats: record, lastInteraction: string}`.
- `PlanoDeAcoes`: `{version: literal(1), batchId: uuid, actions: array min
  1, state: PetStateSnapshot optional}`.

### Endpoints HTTP REST (Hono)

- `GET /health` — healthcheck do Core.
- `GET /pet/:id/state` — retorna PetStateSnapshot (get_state tool read).
- `GET /pet/:id/mood` — retorna `{mood: string}` (get_mood tool read).
- `POST /pet/:id/:tool` — executa uma das 10 tools write (feed/play/rest/
  clean/cuddle/heal/train/dance/express_emotion/get_dizzy). `express_emotion`
  valida body com `EmotionSchema`.

### AppDeps (injeção de dependência para testes)

`AppDeps: {store: PetStore, petId: string, corsOrigin: string, now: () =>
number}`. Relógio injetável permite testes com tempo simulado. Store
injetável permite em memória nos testes.

### TOOL_DELTAS (10 tools write)

Cada tool mapeia para deltas nas stats (ex: `feed` → +fullness, +happiness,
+affection; `play` → +playfulness, +happiness, +sociability, -energy).
Aplicados com `applyDeltas()` que usa asymptotic damping e recalcula health.

### Persistência (SQLite)

Tabela: `pet_id, state_json, last_tick, created_at`. Store injetável via
`AppDeps`. `better-sqlite3` como driver.

### CORS

Configurado em `/pet` e `/pet/*` com origem configurável (`CORE_CORS_ORIGIN`).

### Remoções do scaffold

- `core/src/mcp/` removido.
- `@modelcontextprotocol/sdk` removido de `core/package.json`.

## Testing Decisions

### O que faz um bom teste

Testar comportamento externo (output dado input), não detalhes de
implementação. Funções puras são testadas isoladamente; endpoints HTTP são
testados via `app.request()` da Hono com `AppDeps` em memória.

### Módulos testados

- **`packages/contract/src/index.test.ts`**: Zod parse de Batch, Plano,
  Trigger, Action, Emotion, PetStateSnapshot. Casos válidos e inválidos.
- **`core/src/pet/stats.test.ts`**: funções puras — `initialStats()`,
  `healthOf(stats)`, `decay(stats, lastUpdatedMs, nowMs)`, `applyStat()`,
  `applyDeltas()`, `moodOf(stats)`. Testa asymptotic damping, clamping,
  tiers de decay, moods por prioridade.
- **`core/src/app.test.ts`** (novo): endpoints HTTP via `app.request()`.
  `GET /health`, `GET /pet/:id/state`, `GET /pet/:id/mood`, `POST /pet/:id/:tool`
  para cada tool. Store em memória. Relógio controlado. Testa que `health` é
  derivado, que `express_emotion` valida Emotion, que CORS funciona.

### Prior art

- `core/src/pet/stats.test.ts` já existe no scaffold (padrão Vitest).
- `packages/contract/src/index.test.ts` já existe (padrão Zod parse).
- Hono testing: `app.request(path, options)` é o padrão da Hono para testar
  rotas sem subir servidor.

## Out of Scope

- `POST /batch` (Batch endpoint) — Spec 02.
- advanceStats on-demand por timestamp — Spec 02.
- Adapter Python (pet_tools.py) — Spec 04.
- xiaozhi-server config — Spec 03.
- App Android (WSS, Opus, UI, BatchClient) — Specs 05, 06.
- Sickness persistido como stat separado (não no MVP do Core interno; aparece
  no snapshot se health baixo).
- Flags temporárias para moods dizzy/scared (não no MVP).
- Fallback NVS no device (tópico futuro, ADR-023).

## Further Notes

- **ADR-023 é autoridade.** Divergências do scaffold (happiness removido,
  focus adicionado, Emotion enum, 8 moods deriváveis) são bugs a corrigir no
  scaffold, não na ADR.
- **18 stats canônicos:** o scaffold tem 17 (removeu happiness, adicionou
  focus). Corrigir para 18 editáveis + health = 19 campos no total. `focus`
  está na fórmula de health; `happiness` NÃO está (é stat emotional daily
  que contribui indiretamente via playfulness).
- **MVP de moods:** 8 moods são deriváveis de stats persistidas. Os 5
  restantes precisam de sickness ou flags — não implementados no MVP do Core
  interno. O snapshot de resposta ainda retorna 13 valores possíveis, mas
  `moodOf` só produz 8 no MVP.
- **Runtime:** Bun (TypeScript nativo, SQLite built-in via `bun:sql` ou
  `better-sqlite3`). Framework: Hono.
- **Referências:** ADR-018 (Core TS, contrato canônico), ADR-023 (18 stats,
  health derivado, moods, tools), ticket 01.