# 01: Alinhar contract + refactor Core para HTTP REST

**What to build:** O Core deixa de usar o MCP SDK e passa a expor o estado
do pet via rotas HTTP REST (Hono). As 17 stats editáveis em
`core/src/pet/stats.ts` são alinhadas aos 18 nomes canônicos do ADR-023
(`fullness`, `energy`, `comfort`, `playfulness`, `focus`, `fulfillment`,
`cleanliness`, `curiosity`, `sociability`, `intelligence`, `maturity`,
`affection`, `fitness`, `serenity`, `courage`, `loyalty`,
`mischievousness` + `health` derivado). O `packages/contract/` é alinhado
ao ADR-023 (Emotion enum com os 13 moods, TriggerKind com os kinds da Fase
1: `button`, `shake`, `manual`; Fase 2 adiciona `proximity`, `rtc_wake`).
O diretório `src/mcp/` é removido (o Core não é mais um MCP server — a
emenda ADR-022/023 moveu a integração para um adapter Python interno no
xiaozhi-server, ver ticket 04).

O health permanece derivado (nunca editado direto):
`health = 0.25*fullness + 0.20*fitness + 0.20*energy + 0.15*cleanliness +
0.05*comfort + 0.05*affection + 0.025*fulfillment + 0.025*focus +
0.025*intelligence + 0.025*playfulness`.

Rotas REST a expor: `GET /pet/:id/state`, `GET /pet/:id/mood`, e uma rota
`POST /pet/:id/<tool>` para cada uma das 10 tools write do ADR-023
(`feed`, `play`, `rest`, `clean`, `cuddle`, `heal`, `train`, `dance`,
`express_emotion`, `get_dizzy`). As tools read (`get_state`, `get_mood`)
viram `GET`. Cada tool write aplica delta de stats, recalcula health,
persiste e retorna o snapshot de estado.

**Blocked by:** None (can start immediately).

**Status:** done

- [x] `core/src/pet/stats.ts` refatorado: 17 stats com nomes do ADR-023, `health` derivado pela fórmula acima, decay function pura por timestamp preservada.
- [x] `packages/contract/src/index.ts` alinhado: `Emotion` enum com 13 moods do ADR-023 (`happy`, `sad`, `sleepy`, `bored`, `excited`, `hungry`, `tired`, `dirty`, `dizzy`, `scared`, `playful`, `curious`, `mischievous`); `TriggerKind` com `voice`, `shake`, `button`, `manual` (Fase 1); `version` field mantido (não `schema_version`).
- [x] `core/src/app.ts` refatorado: remove `WebStandardStreamableHTTPServerTransport`/MCP SDK, adiciona rotas HTTP REST (Hono) para as 12 tools + `GET /pet/:id/state`.
- [x] `core/src/mcp/` removido (incluindo `tools.ts`).
- [x] `@modelcontextprotocol/sdk` removido de `core/package.json` dependencies.
- [x] Testes Vitest em `core/src/pet/stats.test.ts` atualizados para os novos nomes de stats e fórmula de health.
- [x] `just core-test` passa.
- [x] Demoable: `curl localhost:3000/pet/test/state` retorna JSON com as 18 stats do ADR-023.
- [x] Demoable: `curl -X POST localhost:3000/pet/test/feed` muta `fullness` e retorna snapshot com `health` recalculado.
