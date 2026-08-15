# ADR-015: Cérebro backend com LangGraph, memória persistente e tool calls

## Status
Accepted

## Date
2026-08-15

## Context

ADR-002 escolheu o smartphone como relay local: termina TLS, converte
PCM, e orquestra ASR→NLP→TTS chamando os provedores de nuvem. ADR-006
fixou o formato de áudio (PCM 16 kHz/16-bit/mono) e o WebSocket como
transporte ESP32↔relay. ADR-005 manteve VAD + KWS locais no ESP32. O
ticket 01 (research) decidiu a stack cloud: Deepgram Nova-3 + regras +
gpt-4o-mini + Azure Neural TTS.

O autor quer que o Robô Felipe seja mais que um receptor de comandos de
voz. A interação desejada com o sobrinho (8 anos) inclui:

- **Comandos de movimento** — "anda", "pula", "dá a pata" (18 ações do
  `servo_dog_ctrl`, ADR-013).
- **Conversa e brincadeira** — o robô inicia e mantém diálogo, brinca,
  tem personalidade (persona "Felipe", o cachorro-robô).
- **Responde perguntas** — conhecimento geral, curiosidades.
- **Busca na internet** — "o que está acontecendo hoje?", fatos
  recentes, atualizações.
- **Ajuda com lição de casa** — explica conceitos de forma adequada à
  idade, com qualidade pedagógica.

Esses requisitos **invertem a posição do LLM** na arquitetura do ticket
01: o LLM deixa de ser *fallback para chit-chat* e vira o **caminho
primário** da interação. A maioria dos turnos não é "anda" — é conversa
aberta, perguntas, lição de casa. Regras continuam como *fast-path* para
os ~18 comandos de movimento (evita round-trip ao LLM para comandos
óbvios), mas o LLM é quem sustenta a riqueza da interação.

Isso exige:

1. **Um harness de agente** — loop de reasoning com function calling,
   histórico de conversa, e capacidade de chamar tools (movimento,
   busca na internet, leitura de sensores).
2. **Memória de sessão (curto-prazo)** — histórico de mensagens do
   diálogo atual, para o robô ter contexto ("do que estávamos falando?").
3. **Memória persistente (longo-prazo)** — fatos sobre o sobrinho
   ("gosta de dinossauros", "tem prova de matemática terça"), episódios
   passados, preferências. O robô "lembra" entre sessões.
4. **Web search** — capacidade de buscar fatos atuais na internet.

Essas funcionalidades não cabem no relay Android (sem persistência
durável, sem harness, complexidade alta) nem no ESP32 (RAM escassa).
Exigem um **backend separado** com estado persistente — um "cérebro"
dedicado ao Robô Felipe.

## Decision

**Adotar um backend na nuvem como "cérebro" do Robô Felipe: uma
aplicação Python + LangGraph rodando em VPS, com Postgres para
memória de sessão e persistente (pgvector), que orquestra ASR →
LLM → TTS, emite tool calls (movimento, web search), e se comunica
com o relay smartphone via WSS bidirecional.**

### Topologia resultante

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

### Decisões específicas

#### 1. Backend orquestra tudo

O backend na nuvem chama ASR (Deepgram), invoca o LLM (LangGraph
harness), chama TTS (Azure), e emite tool calls. O relay deixa de
orquestrar — vira **tunnel inteligente + executor local de tools**.

- Relay envia PCM up ao backend (via WSS) e recebe TTS PCM down +
  tool calls (via WSS).
- Quando o backend emite `move_dog(action)`, o relay repassa ao
  ESP32 via WebSocket LAN e retorna a confirmação ao backend.
- O ESP32 não muda — continua falando WebSocket sem TLS na LAN
  (ADR-002/006 preservados no link ESP32↔relay).

#### 2. VPS cru

Backend roda em VPS (Hetzner CX22 ~$4/mês, 2 vCPU/4GB; ou
DigitalOcean $6/mês). Sempre on, Docker compose (backend + Postgres),
ou binário + SQLite se quiser zerar dependências. Região próxima ao
autor para menor latência WAN.

#### 3. Python + LangGraph

LangGraph dá:

- **State** tipado que carrega `messages` + fatos persistidos +
  tools pendentes entre nós.
- **Checkpointer** (`PostgresSaver`) — grava estado do grafo a cada
  step, retoma após restart do backend. Memória de sessão.
- **Store** (`PostgresStore` + pgvector) — memória persistente com
  retrieve semântico (`store.search(("memories", user_id), query=...)`).
- **ToolNode** — executa tools em paralelo, retorna resultados ao LLM.
- **Streaming de tokens** — backend streama tokens do LLM, pode
  iniciar TTS antes do raciocínio terminar (latência otimizada).
- **Human-in-the-loop interrupts** — se um dia quiser confirmação
  para web search (custo), dá para pausar o grafo.

#### 4. Memória de sessão (curto-prazo)

`PostgresSaver` como checkpointer do LangGraph. Histórico de
mensagens da conversa atual persiste entre turnos e sobrevive a
restart do backend. Cada sessão tem `thread_id` (uma por "ligação"
do robô); ao iniciar nova conversa, nova `thread_id`.

#### 5. Memória persistente (longo-prazo)

`PostgresStore` + pgvector. Padrão "memantle":

- A cada turno, **antes** de invocar o LLM:
  `store.search(("memories", sobrinho_id), query=texto, k=5-10)`
  → top-k fatos relevantes por similaridade semântica ao prompt atual.
- Injeta os fatos no system prompt ("Coisas que você sabe sobre o
  sobrinho: ...").
- **Depois** do turno, um extractor (LLM menor — gpt-5-nano $0,05/$0,40
  — ou heurística) extrai novos fatos do diálogo e faz
  `store.put(("memories", sobrinho_id), uid, {"data": fato})`.
- Embeddings via `text-embedding-3-small` ($0,02/1M tokens — ~$0,02/mês).
- Namespace `("memories", sobrinho_id)` — isolado por usuário (se um
  dia houver outros usuários: irmão, amigos).

#### 6. Transporte relay↔backend: WSS bidirecional

Backend expõe um único endpoint WSS público. Relay conecta, envia PCM
up (binário), recebe TTS PCM down (binário) + tool calls (JSON) +
status. Tool calls do LLM chegam como mensagens JSON no mesmo canal.
TTS streaming via mensagens binárias.

#### 7. Transporte relay↔ESP32: WebSocket sem TLS (inalterado)

Inalterado do ADR-002: ESP32 fala WebSocket sem TLS na LAN com o
relay. Nenhuma mudança no firmware do ESP32.

### Loop de tool calls

Quando o LLM emite `move_dog(FORWARD)`:

1. Backend recebe tool_call do LLM (LangGraph ToolNode).
2. Backend envia `{"tool":"move_dog","args":{"action":"forward"}}` ao
   relay via WSS.
3. Relay repassa `DOG_STATE_FORWARD` ao ESP32 via WebSocket LAN.
4. ESP32 confirma → relay → backend.
5. Backend retoma o raciocínio (ToolNode retorna resultado ao LLM).

**Otimização**: para comandos de movimento puros, o backend pode não
esperar a confirmação — envia o tool_call e já segue gerando TTS ("Ok,
andando!"). Para tools que afetam o raciocínio (web search, leitura
de sensor), precisa esperar.

### Custos

| Item | Custo/mês |
|---|---|
| VPS Hetzner CX22 | ~$4,50 |
| ASR Deepgram ($200 crédito free) | $0 |
| LLM gpt-4o-mini (~225k in + 90k out + memória) | ~$0,40 |
| TTS Azure (500k chars free) | $0 |
| Web search OpenAI (~300 buscas) | ~$3,50 |
| Moderation API | $0 |
| Embeddings (text-embedding-3-small) | ~$0,02 |
| **Total** | **~$8,40/mês** |

~$5/mês com API de busca externa free tier (Tavily/Brave) em vez do
built-in da OpenAI. Continua essencialmente hobby.

## Alternatives Considered

### Relay continua orquestrando, backend é só "consultor LLM"

- Prós: menor mudança ao ADR-006; relay mantém orquestração.
- Contras: tool calls do LLM precisam voltar ao relay para execução
  (loop com 2 hops por call); relógio de orquestração dividido entre
  relay e backend; duplicação de lógica de sessão.
- **Rejeitada**: o backend orquestrando é mais coeso — o "cérebro"
  controla o fluxo completo, o relay é tunnel + executor. Menos
  estados distribuídos.

### Serverless (Cloudflare Workers, Vercel Functions)

- Prós: barato/escalável, zero ops.
- Contras: stateless e cold-start matam a memória de sessão; WebSocket
  persistente é difícil/limitado; precisa de Postgres externo.
- **Rejeitada**: não combina com "sempre on" + WebSocket persistente +
  estado de grafo LangGraph. VPS cru é mais simples.

### Harness custom (~500 linhas) em Go/Rust/Python puro

- Prós: máximo controle, mínimas dependências, melhor latência.
- Contras: precisa reescrever graph state, checkpointer, ToolNode,
  streaming — tudo que LangGraph dá pronto. Para hobby, velocidade de
  desenvolvimento pesa mais que latência de framework.
- **Rejeitada**: LangGraph é maduro, bem documentado, e o integrador
  com Postgres é first-class. Custom só se LangGraph não der conta.

### Memória persistente como profile textual no system prompt

- Prós: simples, sem pgvector, sem retrieve.
- Contras: esquece fatos antigos quando o profile cresce; sem
  relevância semântica (todos os fatos sempre no prompt, custa tokens
  e degrada qualidade); não escala para "episódios" ricos.
- **Rejeitada para o design primário**: pgvector + retrieve top-k é o
  padrão "memantle" e escala bem. Profile textual pode ser um MVP
  rápido antes de introduzir pgvector, mas não a arquitetura final.

## Consequences

### Positivas

- **Interação rica** — conversa, perguntas, lição de casa, web search,
  brincadeiras com personalidade. O robô é um companheiro, não só um
  receptor de comandos.
- **Memória persistente** — o robô "lembra" do sobrinho entre sessões
  (gostos, eventos, episódios). Constrói relacionamento de longo prazo.
- **Memória de sessão** — contexto dentro da conversa atual ("do que
  estávamos falando?").
- **Function calling** — LLM decide quando mover o robô, quando buscar
  na internet, quando responder do conhecimento. Comportamento flexível.
- **Streaming de TTS** — backend pode iniciar TTS antes do raciocínio
  terminar, reduzindo latência percebida.
- **Relay simplificado** — deixa de orquestrar, vira tunnel + executor.
  Menos lógica no app Android.
- **Custo controlado** — ~$8,40/mês (ou ~$5 com busca externa free).
- **Escalabilidade** — adicionar tools (sensores, agenda, e-mail,
  home automation) é só registrar um novo tool node no grafo.

### Negativas

- **Mais um nó** — backend VPS a manter (deploy, updates, monitoramento,
  backups do Postgres). Não é trivial, mas é hobby.
- **Latência extra de um hop** — relay→backend→relay adiciona ~100-300ms
  WAN RTT por turno. Para conversa, aceitável; para comando de voz
  direto ("anda!"), o fast-path de regras no relay ou no ESP32 evita o
  hop.
- **Dependência de internet** — sem internet, o robô perde o cérebro.
  KWS local (ADR-005) continua detectando a palavra-chave, e comandos
  de movimento por regras ainda funcionam no relay (sem LLM), mas
  conversa/perguntas/lição de casa caem.
- **Segurança de conteúdo** — conversa aberta com criança de 8 anos
  exige moderação. `omni-moderation-latest` (grátis) + system prompt
  rígido. Não tem o "out-of-the-box" safety do Claude/Gemini, mas é
  suficiente com disciplina de prompt.
- **Custo recorrente** — VPS é ~$4-6/mês fixo (vs zero do relay-only).
  Para hobby, aceitável.
- **Complexidade de deploy** — Docker compose com backend + Postgres,
  migrations, secrets (API keys), TLS no VPS (Let's Encrypt ou Caddy).
  Mais setup que um app Android alone.

### Notas

- **ADR-002 (relay) refinado, não substituído**: o relay continua
  indispensável — TLS termination, WebSocket LAN com o ESP32, executor
  de tools locais, portabilidade (4G/5G). Apenas deixa de orquestrar
  ASR/NLP/TTS.
- **ADR-006 (ASR/TTS via relay) refinado**: a orquestração passa ao
  backend. PCM 16k/16-bit/mono entre ESP32 e relay permanece; relay
  faz proxy WSS ao backend. O formato de áudio no link ESP32↔relay
  não muda.
- **F0 resolvida**: a névoa "cloud vs self-hosted" do mapa está
  decidida — cloud. Self-hosting (ticket 09) descartado pelo autor
  porque a interação rica requer LLM frontier (gpt-4o-mini ou melhor),
  que não é viável em i5/8GB CPU-only com qualidade pedagógica.
- **F2 (app Android) parcialmente esclarecida**: o relay Android vira
  tunnel + tool executor, mas ainda precisa de WebSocket client WSS,
  integração com ESP32 LAN, e UI. Continua névoa, mais nítida.
- **F4 (nova névoa)**: sincronismo de tool calls (paralelo vs serial
  quando o LLM emite múltiplos tools no mesmo turno) — decisão de
  design do grafo LangGraph na implementação.
- **F5 (nova névoa)**: robustez — se o backend cair, o robô fica mudo?
  Fallback ao relay (regras fixas, sem cérebro)? Decisão de resiliência
  a definir.
- **Fast-path de regras para comandos de movimento** permanece
  recomendado: regras no relay (ou no ESP32) resolvem "anda", "pula",
  "dá a pata" em <10ms, evitando o hop ao backend. O LLM só é chamado
  para comandos ambíguos, compostos, ou chit-chat. Isso não conflita
  com o LLM primário — é uma otimização de latência para o caso óbvio.
- **Custo de web search** é o componente mais caro da stack
  (~$3,50/mês via OpenAI). Para reduzir, usar Tavily/Brave Search API
  (free tier) com function calling em vez do built-in da OpenAI.
- **VPS em região próxima ao autor** minimiza WAN RTT. Hetzner
  (Alemanha/EUA) é mais barato, OVH São Paulo é alternativa BR com
  menor latência. Testar latência real antes de amarrar.
- **O extractor de fatos** (que popula a memória persistente) pode ser
  o próprio gpt-4o-mini, um modelo menor (gpt-5-nano), ou heurística
  ("lembrar" detectado no texto). Decisão de implementação.
- **O loop de tool calls ao ESP32** (backend→relay→ESP32) exige que
  o relay mantenha o WebSocket LAN aberto com o ESP32 **e** o WSS ao
  backend simultaneamente. O relay é o "pivô" entre os dois canais.
