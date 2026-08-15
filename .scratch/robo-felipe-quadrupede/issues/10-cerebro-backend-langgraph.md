# 10 — Arquitetura do "cérebro" backend (LangGraph + memória persistente)

## Type
grilling

## Status
closed

## Assignee
opencode (grilling com o autor)

## Blocked by
none

## Resolution

Decidido via grilling com o autor. O Robô Felipe ganha um **backend na
nuvem** como "cérebro" — orquestra ASR→LLM→TTS, mantém memória de sessão
e persistente, e emite tool calls (movimento + web search).

### Decisões

| # | Decisão | Escolha |
|---|---|---|
| 1 | Quem orquestra ASR→cérebro→TTS | **Backend** (relay vira tunnel inteligente + executor de tools) |
| 2 | Onde roda o backend | **VPS cru** (Hetzner CX22 ~$4/mês ou similar) |
| 3 | Runtime + harness | **Python + LangGraph** |
| 4 | Memória de sessão (curto prazo) | **PostgresSaver** (checkpointer do LangGraph) |
| 5 | Memória persistente (longo prazo) | **PostgresStore + pgvector** (retrieve semântico por turno) |
| 6 | Transporte relay↔backend | **WSS bidirecional** |
| 7 | Transporte relay↔ESP32 | **WebSocket sem TLS** (LAN, inalterado do ADR-002) |

### Topologia

```
ESP32 (WS, PCM 16k, sem TLS, LAN)
  │
  ▼
Relay smartphone ──── WSS (HTTPS, nuvem) ────► Backend (VPS, Python + LangGraph)
  • TLS termination                          │
  • tunnel PCM up/down                      ├─ ASR:  Deepgram Nova-3 (cloud)
  • executor local de tool calls            ├─ LLM:  gpt-4o-mini + function calling
    no ESP32 (LAN)                          ├─ TTS:  Azure Neural TTS (cloud)
  • passa áudio TTS ao ESP32               ├─ Web:  OpenAI web_search ($10/1k)
                                            ├─ Memória sessão: PostgresSaver
                                            └─ Memória longa:  PostgresStore + pgvector
                                              (extractor extrai fatos por turno)
```

### Papel do relay (refina ADR-002)

- TLS termination (WSS ao backend)
- Tunnel: PCM up (ESP32→backend), PCM down (backend→ESP32, TTS)
- **Executor local de tool calls** — quando o backend envia
  `{"tool":"move_dog","args":{...}}` via WSS, o relay repassa ao ESP32
  via WebSocket LAN e retorna a confirmação ao backend
- Repassa áudio TTS ao ESP32
- **Deixa de orquestrar** ASR/NLP/TTS — isso passa ao backend

### Papel do backend (novo)

- Recebe PCM do relay via WSS
- Chama ASR (Deepgram) → texto
- Antes de invocar o LLM: `store.search(("memories", sobrinho_id), query=texto)`
  → top-k fatos relevantes (pgvector) → injeta no system prompt
- Invoca LLM (gpt-4o-mini) com function calling (tools: `move_dog`,
  `web_search`, etc.)
- Se o LLM emite tool_call:
  - Envia ao relay via WSS, espera confirmação (para tools que afetam o
    raciocínio, ex. leitura de sensor) ou segue em paralelo (para
    comandos de movimento puros, já gerando TTS)
- Gera resposta em PT-BR → chama TTS (Azure) → streama PCM ao relay
- Extrai fatos novos do turno (LLM menor ou heurística) → `store.put`

### Custo consolidado

| Item | Custo/mês |
|---|---|
| VPS Hetzner CX22 | ~$4,50 |
| ASR Deepgram ($200 crédito) | $0 |
| LLM gpt-4o-mini | ~$0,40 |
| TTS Azure (500k free) | $0 |
| Web search OpenAI (~300) | ~$3,50 |
| Moderation API | $0 |
| Embeddings | ~$0,02 |
| **Total** | **~$8,40/mês** |

~$5/mês com API de busca externa free tier (Tavily/Brave).

### ADRs impactados

- **ADR-002** (relay): refinado — relay deixa de orquestrar, vira tunnel
  + executor de tools. Portabilidade mantida.
- **ADR-006** (ASR/TTS via relay): refinado — orquestração passa ao
  backend. PCM 16k/16-bit/mono inalterado.
- **ADR-015** (novo): arquitetura do cérebro — backend VPS + LangGraph
  + memória persistente.

### Névoa que graduou

- **F0** (cloud vs self-hosted) — resolvida: cloud. Self-hosting
  descartado pelo autor (interação rica requer LLM frontier, não cabível
  em i5/8GB).
- **F2** (app Android breakdown) — parcialmente esclarecida: o relay
  Android vira tunnel + tool executor, mas ainda precisa de WebSocket
  client WSS, integração com ESP32 LAN, e UI. Continua névoa, mas mais
  nítida.

### Névoa que surge

- **F4: Loop de tool calls — sincronismo e paralelismo** — quando o LLM
  emite `move_dog` + `web_search` no mesmo turno, o backend precisa
  paralelizar (mover o robô não bloqueia a busca) ou serializar?
  Decisão de implementação no design do grafo LangGraph.
- **F5: Cold-start do LLM no VPS** — se o backend cair, o robô fica mudo?
  Fallback ao relay (sem cérebro, só comandos fixos por regras)?
  Decisão de robustez a definir.

## Question

Como arquitetar o "cérebro" do Robô Felipe — uma aplicação backend com
harness de LLM, memória de sessão (curto-prazo), memória persistente
(longo-prazo), web search, e NLP/tool calls para emitir comandos ao
robô?

A interação desejada é complexa: não apenas comandos de movimento, mas
conversa, brincadeiras, respostas a perguntas, busca na internet, ajuda
com lição de casa. Isso inverte a posição do ticket 01 (LLM como
fallback) — o LLM vira o caminho primário.
