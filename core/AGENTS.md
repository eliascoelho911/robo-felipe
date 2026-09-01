# core/AGENTS.md

Regras do **Core** — o serviço TypeScript onde mora o comportamento do
Robô Felipe. Para regras globais e o contrato arquitetural, leia o
`AGENTS.md` da raiz.

## Papel

O Core recebe **Batches** (envelopes de Triggers vindos da Plataforma),
decide o que o pet faz, e responde com um **Plano de Ações**. Ele é o
único lugar onde **comportamento** vive — firmware e app só detectam e
executam. O Core pode chamar a **Nuvem** (ASR/LLM/TTS) e expõe **HTTP
endpoints** que um adapter Python no xiaozhi-server bridgeia ao LLM.

O contrato Batch→Plano de Ações é o que sobrevive à troca de host (Android
hoje → CoreS3 amanhã). Mudar a Plataforma nunca reescreve o Core.

## Estado do pet (ADR-023)

- **Cloud-primary.** O estado canônico do pet vive no SQLite do Core. No
  MVP **não há fallback NVS** no dispositivo — se a Plataforma perde
  conectividade, o pet pausa, não diverge.
- **18 stats**: 17 editáveis + `health` **derivado**. `health` nunca é
  editado direto — é calculado a partir dos outros. Nunca aceite um
  Batch que tente setar `health` diretamente.
- **Decay por timestamp**: o valor de uma stat é função pura de
  `(valor_base, last_updated, agora)`. Sem wall-clock com efeitos
  colaterais; sem timers que reescrevem o estado em segundo plano. Revalidar o
  estado é barato e determinístico.
- **Estágios Filhote→Jovem→Adulto** via **XState v5** — transições
  auditáveis, sem morte do pet. A máquina de estados é a fonte da
  verdade do estágio atual.

## Tools (HTTP endpoints)

O Core expõe um **catálogo de tools via HTTP REST** (`GET /pet/:id/state`,
`POST /pet/:id/feed`, etc.). Um **adapter Python** interno no
xiaozhi-server (`plugins_func/functions/pet_tools.py`) registra cada tool
como `ToolType.SYSTEM_CTL`, chama o Core via HTTP (`httpx`), e usa `conn`
para enviar `pet_action` JSON ao device. O LLM chama via function
calling; o adapter retorna `ActionResponse(Action.REQLLM)` para o LLM
gerar texto.

Por que não MCP? MCP servers externos não têm acesso ao `conn` do
xiaozhi-server — não conseguem enviar ações não-TTS ao device (ver
ADR-022 emenda). O adapter Python interno resolve isso. Adicionar uma
tool = registrar um endpoint HTTP no Core + registrar a function no
adapter; não enfiar chamada de rede solta no handler de Batch.

## Stack

- **Hono** — servidor HTTP (rotas REST para tools e Batch).
- **better-sqlite3** — persistência SQLite (arquivo local, zero-config,
  MVP PC→VPS).
- **Zod** — validação em runtime, mas os **schemas canônicos** do
  Batch/Plano de Ações vivem em `packages/contract/`. O Core importa de
  lá; não duplique schemas aqui.
- **Vitest** — test runner. Funções puras de decay e a máquina de
  estados devem ter testes determinísticos (sem depender de `Date.now()`
  real — injete o relógio).

## Estrutura (a preencher conforme o Core ganha código)

```
core/
├── AGENTS.md          # este arquivo
├── package.json
├── src/
│   ├── app.ts         # app Hono (rotas HTTP)
│   ├── main.ts        # bootstrap (@hono/node-server)
│   ├── pet/           # estado, stats, decay, máquina XState
│   └── routes/        # endpoints HTTP (tools, batch)
├── .env.example
└── *.db              # SQLite (gitignored)
```

## Convenções

- **Código e identificadores em inglês**; **comentários em pt-BR** (só o
  porquê). Docs e commits em pt-BR imperativo.
- Funções de domínio (decay, transições) **puras e testáveis**; efeitos
  (IO, DB, relógio) injetados ou isolados nas bordas.
- Prefira acesso direto de chave a `.get()`/`??` quando a ausência seria
  bug de contrato.
- Mudanças cirúrgicas; comentários só o porquê; nunca fabrique resultados.
- Antes de adicionar código, leia a implementação mais próxima que já
  existe e prefira a camada mais estreita.
