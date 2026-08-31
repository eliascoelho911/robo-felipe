# ADR-023: PET vivo — estado, stats, decay, estágios e catálogo de MCP tools

## Status

Accepted

## Date

2026-08-31

## Context

O [ADR-018](018-tamagotchi-comportamento-mora-no-core-typescript.md) decidiu
que o **comportamento** do Tamagotchi mora num Core em TypeScript
auto-hospedado, acessado pela Plataforma via HTTPS. Deixou explicitamente em
aberto, como tópico para a conversa "PET vivo":

> gerenciamento de estado e comportamento (stats, decay, estágios,
> persistência cloud vs NVS) e a visão do "PET vivo".

O [ADR-021](021-tamagotchi-firmware-xiaozhi-esp32-com-customizacoes.md)
decidiu a UI de pet (LVGL + camada de pet, sprites de olhos/boca, barras de
stats via `lv_bar`, animações via `lv_anim`) e remeteu o "design de
stats/decay do catode32" para esta ADR. O
[ADR-022](022-tamagotchi-nuvem-xiaozhi-server-com-provedores-pt-br.md)
estabeleceu o Core como **MCP tool provider** no xiaozhi-server e deixou o
catálogo de tools (`pet.dance()`, `pet.get_state()`, etc.) para esta ADR.

Esta ADR fecha todas essas pendências: define o modelo de estado do pet
(stats, estágios, decay, sickness, moods), onde o estado mora, como ele
avança no tempo, e o catálogo completo de MCP tools que o Core expõe ao LLM.

### Achados da inspeção dos motores de referência (2026-08-31)

O research [`../research/tamagotchi-pet-engine-ui.md`](../research/tamagotchi-pet-engine-ui.md)
recomenda `cifertech/TamaFi` (MIT, C++) como base e `moonbench/catode32`
(MIT code / CC-BY-NC-ND art, MicroPython) como referência de design. A
inspeção direta do código de ambos (via GitHub API + `webfetch` de
arquivos-fonte, 2026-08-31) confirma o gap decisivo já identificado pelo
research: **nenhum dos dois avança stats por tempo de relógio/RTC no wake**
— ambos tica em `millis()` (TamaFi) ou loop always-on com
`game_minutes_per_second` simulado (catode32). O `SLEEP_MODE = "deep"` do
catode32 está marcado como "not yet implemented".

**catode32** — modelo superior (18 stats, health derivado por weighted
average, asymptotic damping perto dos extremos, sickness tiers que bloqueiam
behaviors, 29 behaviors registrados com auto-seleção, persistência
`/save.json` + `/backup.json` versionada, adoption rotation = death/rebirth).
Código MIT reutilizável; arte CC-BY-NC-ND (não distribuível).

**TamaFi** — 3 stats (hunger/happiness/health) + 3 traits
(curiosity/activity/stress) + 7 moods + 4 estágios (BABY→TEEN→ADULT→ELDER) +
morte + rebirth + egg hatch. NVS via `Preferences`. AutoSave 30s. Timers em
`millis()`. Alimentação por WiFi scan. TFT_eSPI sprites + NeoPixel feedback.

## Decision

**O Robô Felipe tem um modelo de pet vivo com 18 stats (health derivado),
estágios sem morte (Filhote→Jovem→Adulto), decay em tiers mistos
(daily/weekly/monthly/very-slow), estado canônico no Core (cloud-primary,
sem fallback NVS no MVP), e um catálogo de MCP tools exposto ao LLM.** O
design é inspirado no `moonbench/catode32` (estrutura de stats, health
derivado, asymptotic damping, sickness tiers, decay tiers), adaptado para
um cachorro-robô pt-BR sem morte.

### 1. Modelo de stats — 18 stats com health derivado

O pet tem **17 stats editáveis + 1 health derivado**, organizados em 4
grupos. Todos os valores são floats 0-100 (exceto onde indicado):

#### Físicos (6)

| Stat | pt-BR | Decay tier | Descrição |
|:--|:--|:--|:--|
| `fullness` | saciedade | daily | quão alimentado (0 = faminto) |
| `energy` | energia | daily | reserva de energia (0 = exausto) |
| `cleanliness` | higiene | weekly | quão limpo (0 = sujo) |
| `fitness` | disposição | weekly | condicionamento físico |
| `comfort` | conforto | weekly | bem-estar físico (temperatura, etc.) |
| `health` | saúde | **derivado** | nunca editado direto — ver abaixo |

#### Emocionais (5)

| Stat | pt-BR | Decay tier | Descrição |
|:--|:--|:--|:--|
| `happiness` | felicidade | daily | humor geral |
| `playfulness` | brincadeira | daily | vontade de brincar |
| `affection` | afeto | weekly | vínculo com o Sobrinho |
| `serenity` | serenidade | very-slow | calma interior (0 = ansioso) |
| `fulfillment` | realização | very-slow | satisfação de vida |

#### Sociais (2)

| Stat | pt-BR | Decay tier | Descrição |
|:--|:--|:--|:--|
| `sociability` | sociabilidade | weekly | vontade de interagir |
| `loyalty` | lealdade | monthly | devoção ao Sobrinho |

#### Mentais / Personalidade (5)

| Stat | pt-BR | Decay tier | Descrição |
|:--|:--|:--|:--|
| `curiosity` | curiosidade | monthly | interesse em explorar |
| `intelligence` | inteligência | monthly | aprendizado (sobe com `train`) |
| `maturity` | maturidade | monthly | **drive de evolução de estágio** |
| `courage` | coragem | monthly | tolerância a sustos |
| `mischievousness` | travessura | very-slow | tendência a fazer arte |

**Coins** e **minigame scores** do catode32 não são incluídos (não há
economia/store nem minigames no MVP).

### 2. Health derivado + asymptotic damping + sickness

**Health é derivado** (como catode32) — nunca é modificado direto por uma
tool. É recalculado após cada mudança de stat:

```
health = 0.25*fullness + 0.20*fitness + 0.20*energy
       + 0.15*cleanliness + 0.05*comfort + 0.05*affection
       + 0.025*fulfillment + 0.025*focus + 0.025*intelligence
       + 0.025*playfulness
```

(clamped 0-100). Isto significa que alimentar o pet sobe `fullness`, o que
indiretamente sobe `health` — mas não se pode "curar" o pet só encher a
barriga; é preciso manter todas as stats contribuintes.

**Asymptotic damping** (catode32): mudanças de stat perto dos extremos
resistem. Para um delta positivo: `delta_efetivo = delta * ((100 - current)
/ 100) ^ 0.7`. Para negativo: `delta_efetivo = delta * (current / 100) ^
0.7`. Isto evita que o pet atinja 0 ou 100 facilmente e faz progressão
nonlinear — mais recompensador nas faixas médias.

**Sickness** é um stat separado (0-10, não uma das 18 stats principais).
Sobe quando: `health < 30` por tempo prolongado, ou `cleanliness < 20`, ou
`fullness = 0` por muito tempo. Tiers:

| Tier | Faixa | Efeito |
|:--|:--|:--|
| saudável | 0-1.9 | sem efeito |
| mild | 2-4.9 | bloqueia behaviors ativos (zoomies, travessura, caça); bonus de stats ×0.8 |
| clear | 5-7.9 | bloqueia mais (lounging, self_grooming, pacing); bonus ×0.6 |
| severe | 8-10 | pet deitado, quase não reage; bonus ×0.4 |

**Recuperação**: `pet.heal()` reduz sickness diretamente. Cuidado contínuo
(saciedade/higiene/energia altas) reduz sickness gradualmente. **Nunca
morre** — severe sickness só deixa o pet muito triste/inativo; recupera com
carinho e cuidado.

### 3. Estágios — Filhote → Jovem → Adulto (sem morte)

O pet evolui por **maturidade** (stat monthly) + tempo de vida:

| Estágio | Maturidade | Comportamento |
|:--|:--|:--|
| **Filhote** | 0-30 | inicial; stats decaem 1.3× mais rápido; precisa mais cuidado; mais curioso/brincalhão |
| **Jovem** | 30-70 | estável; mais energia/playfulness; estágio padrão após algumas semanas |
| **Adulto** | 70-100 | estável; stats decaem 0.8× mais devagar; mais sereno/leal |

- **Evolução é unidirecional** — maturidade só sobe (com cuidado + tempo).
  O pet nunca regride de estágio.
- **Sem morte, sem rebirth.** Se extremamente negligenciado (sickness
  severe), o pet fica deitado e triste, mas recupera com cuidado. O
  Sobrinho nunca perde o pet permanentemente.
- **Egg hatch** (TamaFi) não adotado — o pet começa como Filhote direto
  (não como ovo), para reduzir fricção no 1º uso.
- **Idade** = dias desde a criação (relógio server-side no Core).

### 4. Estado canônico — Core-primary, sem fallback NVS no MVP

O estado canônico do pet (stats, estágio, sickness, maturidade, idade)
**mora no Core** (TypeScript, cloud-side, ADR-018). O Core é a source of
truth.

- **Sem snapshot NVS no device no MVP.** O dispositivo não guarda estado do
  pet localmente — consulta o Core (via HTTPS) para saber o mood/stats
  atuais ao renderizar a UI.
- **Sem `advanceStats` local no device.** O device não calcula decay — o
  Core faz (server-side, ver §5).
- **Pet "congela" se offline.** Sem internet, o device não consulta o Core
  e entra em **modo degradado offline** (ADR-016): mostra animação fixa
  ("dormindo" ou "esperando"), sem stats atualizadas. O Core continua
  rodando server-side e avançando stats independentemente — quando o device
  volta online, consulta o Core e vê o estado atualizado.
- **Fallback offline (NVS snapshot + `advanceStats` local no wake do RTC)**
  é **tópico futuro**, explicitamente fora do escopo do MVP. A
  complexidade de sincronização (dirty flags, merge, conflict resolution)
  não se justifica antes de validar o modelo cloud-primary.

**Persistência no Core**: o estado do pet é persistido na persistência do
próprio Core (decisão de implementação — JSON file, SQLite ou Postgres,
conforme a stack do Core evoluir). Cada pet tem um `pet_id` (gerado no
provisioning) e um documento de estado com `last_tick` (timestamp ISO 8601
do último `advanceStats`).

### 5. Decay em tiers mistos + advanceStats server-side

O decay das stats ocorre em **4 tiers** (inspirado no catode32), com
diferentes rates de decaimento por tempo decorrido:

| Tier | Stats | Taxa de decay (indicativa) |
|:--|:--|:--|
| **daily** | fullness, energy, happiness, playfulness | ~ -15/dia |
| **weekly** | cleanliness, fitness, comfort, affection, sociability | ~ -10/semana |
| **monthly** | maturity, curiosity, intelligence, courage, loyalty | ~ -5/mês |
| **very-slow** | serenity, fulfillment, focus, mischievousness | ~ -2/mês |

(Rates indicativas — tuning fino fica para a implementação. O Sobrinho
deve sentir o pet "com fome" dentro de algumas horas, mas não ver stats
despencarem em minutos.)

**`advanceStats(elapsedSeconds)`** roda **server-side no Core**, não no
device. É chamado **on-demand**: quando o Core recebe um Batch ou uma tool
call, ele calcula `elapsed = now - last_tick`, aplica decay por tier para
cada stat, atualiza `last_tick = now`, e persiste. Não há cron/scheduler
no MVP — o estado só avança quando alguém consulta ou interage.

Isto significa que se o device não interage por dias, as stats só avançam
na próxima consulta (o Core calcula retroativamente). O resultado é o
mesmo que um tick periódico, mas sem infra de scheduler.

### 6. Moods (derivado das stats)

O mood atual do pet é **derivado** das stats (não é armazenado), para a UI
e para o LLM (contexto de persona):

| Mood | Condição (prioridade top-down) |
|:--|:--|
| `doente` | sickness ≥ 5 |
| `dormindo` | device em deep-sleep / `rest()` ativo |
| `faminto` | fullness < 25 |
| `exausto` | energy < 20 |
| `triste` | happiness < 25 |
| `sujo` | cleanliness < 20 |
| `tonto` | flag temporária após `get_dizzy()` (sacudida) |
| `assustado` | flag temporária após susto (coragem baixa + evento) |
| `brincalhão` | playfulness > 60 e energy > 40 |
| `curioso` | curiosity > 60 |
| `carinhoso` | affection > 60 |
| `travesso` | mischievousness > 60 |
| `feliz` | default (nenhuma condição acima) |

A UI renderiza a expressão facial correspondente ao mood (sprites de
olhos/boca via `lv_image`, ADR-021). O LLM recebe o mood como contexto
(`pet.get_state()` inclui `mood`) para moldar o tom das respostas.

### 7. Catálogo de MCP tools

O Core expõe tools ao LLM via MCP endpoint do xiaozhi-server
(`ws://host:8004/mcp_endpoint/`, ADR-022). O LLM chama via function calling
quando reconhece intents de ação na fala do Sobrinho.

#### Read (consulta, não muta estado)

| Tool | Retorna | Uso |
|:--|:--|:--|
| `pet.get_state()` | `{stage, mood, health, stats{...}, age_days, last_interaction, sickness_tier}` | LLM consulta para moldar resposta ("como você está?") |
| `pet.get_mood()` | `string` (mood atual) | atalho para contexto rápido |

#### Write (muta estado, retorna Plano de Ações)

| Tool | Efeito em stats | Plano de Ações retornado |
|:--|:--|:--|
| `pet.feed(food?)` | +fullness, +happiness, +affection | `expressar_emocao{felicidade}` + `falar{texto}` |
| `pet.play(game?)` | +playfulness, +happiness, +sociability, -energy | `dançar` + `falar{texto}` |
| `pet.rest()` | +energy, +serenity | `dormir` (animação + som) |
| `pet.clean()` | +cleanliness, +comfort, +health(indireto) | `expressar_emocao{conforto}` + `falar{texto}` |
| `pet.cuddle()` | +affection, +comfort, +happiness | `expressar_emocao{carinho}` + `falar{texto}` |
| `pet.heal()` | -sickness, +health(indireto) | `expressar_emocao{cura}` + `falar{texto}` |
| `pet.train(skill?)` | +intelligence, +focus, +maturity, -energy | `falar{texto}` (aprendeu) |
| `pet.dance()` | +happiness, +playfulness, -energy | `dançar` (Ação direta) |
| `pet.express_emotion(emotion)` | — (só animação) | `expressar_emocao{emotion}` |
| `pet.get_dizzy()` | -focus (temporário) | `ficar_tonto` (Ação direta) |

As Ações (`falar`, `dançar`, `expressar_emocao`, `ficar_tonto`, `dormir`)
são as definidas em `CONTEXT.md` e ADR-018. O `falar{texto}` retorna texto
que a Plataforma renderiza como TTS (Android = TTS nativo pt-BR; CoreS3 =
protocolo xiaozhi, ADR-018/022).

O LLM decide quando chamar uma tool: se o Sobrinho diz "vamos brincar", o
LLM reconhece a intent e chama `pet.play()`; o Core atualiza stats e
retorna o Plano (`dançar` + `falar{yay!}`); o device executa. Se o Sobrinho
pergunta "como você está?", o LLM chama `pet.get_state()` e usa o resultado
para compor a resposta textual.

#### Triggers não-vozeados (Batch → Core, não MCP)

Triggers detectados pelo device sem fala (sacudida, botão, proximidade, RTC
wake) não passam pelo LLM — vão direto ao Core via HTTPS/Batch (ADR-018):

| Trigger | Batch payload | Core retorna (Plano) |
|:--|:--|:--|
| `sacudida` (IMU BMI270) | `{type: "shake", ts}` | `ficar_tonto` ou `expressar_emocao{assustado}` |
| `botão` (toque rápido) | `{type: "button", ts}` | `falar{oi!}` ou `feed` (configurável) |
| `proximidade` (LTR-553) | `{type: "proximity", ts}` | `expressar_emocao{curioso}` + `falar{olá!}` |
| `rtc_wake` (timer horário) | `{type: "rtc_wake", ts}` | `falar{bom dia!}` + estado atualizado (advanceStats) |

O `rtc_wake` é especial: o device acorda do deep-sleep pelo timer BM8563
(ADR-021), envia um Batch ao Core, o Core roda `advanceStats` (decay por
tempo decorrido), e retorna um Plano de Ações (saudação + estado atualizado
para a UI). Se offline, o device mostra "dormindo" e volta a dormir (sem
estado atualizado — ver §4).

### 8. Integração com a UI do firmware (ADR-021)

O firmware (xiaozhi-esp32 + customizações, ADR-021) **não detém o estado**
— consulta o Core para mood/stats ao renderizar. A camada de pet em LVGL:

- **Face do pet**: sprites de olhos/boca selecionados pelo mood (recebido
  do Core via `pet.get_state()` ou incluso no Plano de Ações).
- **Barras de stats**: `lv_bar` com as 4-5 stats principais (saciedade,
  energia, felicidade, saúde + sickness). As 13 stats restantes acessíveis
  via menu (ou implícitas no comportamento — o LLM as usa, a UI não mostra
  todas).
- **Animações**: `lv_anim` para executar as Ações do Plano (dançar, ficar
  tonto, expressar emoção).
- **Estado no boot**: o device conecta WiFi → consulta `pet.get_state()` →
  renderiza face + barras. Se offline, mostra face "dormindo" + barras
  cinza (sem dados).

### 9. Integração com o system prompt (ADR-022)

O `prompt` field do xiaozhi-server (ADR-022) inclui o estado do pet como
contexto dinâmico. O Core, ao registrar tools no MCP endpoint, pode
injetar o estado atual no `{{dynamic_context}}` do template
`agent-base-prompt.txt`:

```
Estado atual do Felipe: estágio=Jovem, mood=brincalhão,
health=72, saciedade=45 (com fome leve), felicidade=80.
Última interação: 2h atrás.
```

Isto permite ao LLM moldar respostas ao estado ("estou com um pouquinho
de fome, você tem um lanche?"). O LLM também pode chamar `pet.get_state()`
para estado fresco a qualquer momento.

## Alternatives Considered

### Modelo de stats minimal (4-5 stats, como TamaFi)

- **Prós:** simples de entender para 8 anos; fácil de balancear; UI mostra
  todas o tempo todo; menos NVS/decay logic.
- **Contras:** menos depth de personalidade; o pet não sente "diferente"
  com diferentes combinações de cuidado;sicness/estágios ficam arbitrários.
- **Rejeitado pelo usuário:** o usuário escolheu o modelo rico (15-18
  stats) para máxima depth de simulação, inspirado no catode32.

### Morte + rebirth (TamaFi/catode32)

- **Prós:** ensina responsabilidade; consequência real de negligência;
  ciclo de vida completo; catode32 tem "adoption rotation".
- **Contras:** morte do pet pode ser **traumática para uma criança de 8
  anos**; rebirth reseta progresso (frustrante); o Sobrinho pode perder o
  pet que criou por semanas.
- **Rejeitado pelo usuário:** estágios sem morte. O pet fica doente/triste
  se negligenciado, mas recupera com cuidado — nunca morre.

### Estado NVS-primary, Core stateless

- **Prós:** pet sempre vivo (NVS); funciona offline; Core mais simples
  (stateless).
- **Contras:** contraria o espírito do ADR-018 (iterar comportamento sem
  reflash); mudar decay rates / adicionar stats exige OTA de firmware; o
  estado do pet não é consultável server-side (LLM não sabe o mood sem
  perguntar ao device).
- **Rejeitado pelo usuário:** Core-primary sem fallback no MVP. O estado
  mora no Core para iterar sem reflash; fallback NVS fica para depois.

### Decay em minutos (demo acelerada, como TamaFi)

- **Prós:** gratificação rápida; o Sobrinho vê evolução em uma sessão.
- **Contras:** pet envelhece/decai rápido — pode ficar doente enquanto o
  Sobrinho está na escola; exige interação constante; não combina com
  deep-sleep horário (pet "morre" durante a noite).
- **Rejeitado pelo usuário:** tiers mistos (daily/weekly/monthly/very-slow)
  alinhados ao deep-sleep horário. O pet sente "realista" — fome em horas,
  evolução em dias/semanas.

### Catode32 como base de código (portar MicroPython → TypeScript)

- **Prós:** 18 stats + 29 behaviors + sickness + decay tiers já modelados;
  código MIT reutilizável.
- **Contras:** catode32 é **MicroPython** (não TypeScript); portar para TS
  é reescrever; o modelo de catode32 é para gato (behaviors como
  `kneading`, `chattering`, `zoomies` são felinos); arte é CC-BY-NC-ND.
- **Rejeitado para implementação:** o catode32 é **referência de design**
  (estrutura de stats, health derivado, damping, sickness tiers, decay
  tiers), não base de código. O Core implementa o modelo em TypeScript do
  zero, inspirado no design.

### TamaFi como base de código (C++ no firmware)

- **Prós:** MIT, ESP32-S3, NVS-persistente, C++; já modela estágios +
  morte + rebirth.
- **Contras:** ADR-018 moveu o estado para o Core (cloud, TS) — não faz
  sentido ter um engine de pet em C++ no firmware se o estado mora no Core.
  TamaFi tica em `millis()` (uptime), sem RTC catch-up. Alimentação por
  WiFi scan é inadequada.
- **Rejeitado:** o estado mora no Core (ADR-018); TamaFi como engine
  on-device é incompatível com essa arquitetura. O design de stats/estágios
  do TamaFi (BABY→TEEN→ADULT→ELDER + morte) inspira o modelo, mas sem morte
  e adaptado para cachorro.

## Consequences

### Positivas

- **Modelo rico de pet** — 18 stats com health derivado, asymptotic
  damping, sickness tiers e decay em tiers dá depth de simulação sem
  morte. O pet sente "vivo" e dinâmico, diferente de um modelo de 3 stats.
- **Iteração sem reflash** — stats, decay rates, sickness thresholds e
  estágios são código TypeScript no Core. Tunar o pet é editar o Core e
  reiniciar o processo, não OTA de firmware.
- **LLM informado pelo estado** — o LLM recebe mood + stats como contexto
  (`pet.get_state()` ou `{{dynamic_context}}`) e molda respostas ao estado
  atual. O pet "sabe" que está com fome e pede comida.
- **Separação limpa** — o Core detém estado e decide comportamento; o
  firmware só renderiza UI e transporta áudio; o LLM é a junção via
  function calling. Cada componente tem uma responsabilidade.
- **Sem morte = seguro para 8 anos** — o Sobrinho nunca perde o pet
  permanentemente. Negligência tem consequência (doente/triste), mas
  recuperável.
- **Catálogo de tools definido** — fecha a pendência do ADR-022. O LLM
  sabe quais tools pode chamar e o que cada uma faz.
- **MCP nativo** — o xiaozhi-server já tem MCP endpoint (ADR-022); o Core
  só registra as tools. Sem glue de protocolo custom.

### Negativas

- **Pet "congela" se offline** — sem fallback NVS no MVP, o device não
  mostra stats atualizadas sem internet. O modo degradado offline (ADR-016)
  mostra "dormindo". Isto é **aceitável para o MVP** (validar cloud-primary
  primeiro), mas é uma limitação UX real para um pet de bolso que o
  Sobrinho leva a qualquer lugar. Fallback NVS é tópico futuro explícito.
- **Dependência do Core estar no ar** — se o Core cai, o pet não funciona
  (não consulta estado, não recebe Planos). O Core precisa ser always-on
  (PC do autor → VPS, ADR-022). Sem HA no MVP.
- **18 stats é complexo de balancear** — tuning de decay rates, sickness
  thresholds, asymptotic damping, e interações entre stats exige
  experimentação. O modelo é rico mas pode precisar de várias iterações
  para "sentir certo".
- **UI mostra só 4-5 stats** — 18 stats é muito para o display 320×240.
  A UI mostra as principais (saciedade, energia, felicidade, saúde +
  sickness); o resto é implícito no mood/comportamento. O Sobrinho não vê
  "lealdade" ou "serenidade" como barras, mas o pet age diferente conforme
  esses valores.
- **`advanceStats` on-demand não é real-time** — se ninguém consulta o
  Core por dias, as stats só avançam na próxima consulta. O resultado é
  correto (decay retroativo), mas o Core não "sabe" o estado entre
  consultas. Para um cron de telemetria/monitoramento, seria preciso um
  scheduler — fora do escopo do MVP.
- **Sickness severe sem morte** — o pet em estado severe (deitado, quase
  não reage) pode confundir o Sobrinho ("meu pet morreu?"). A UI deve
  indicar claramente "estou doente, preciso de cuidado" (ícone de
  curativo, não face de morte).
- **Tools write mutam estado sem transação** — se o LLM chama `pet.feed()`
  e `pet.play()` em rápida sucessão, o Core precisa handle de concorrência
  (race condition em stats). Mitigação: tools são síncronas no Core (uma
  por vez) ou usam lock por `pet_id`.
- **Catode32 como referência de design, não código** — o modelo é
  reescrito em TypeScript do zero. Se catode32 evolui (novos behaviors,
  stats), não há merge automático — é acompanhamento manual.

## Notas

- **Supersede parcial do research `tamagotchi-pet-engine-ui.md`**:
  - O research recomendava TamaFi como **base C++ on-device** + catode32
    como referência de design. Esta ADR move o estado para o Core (cloud,
    TS, ADR-018) — TamaFi como engine on-device é descartado. O **design
    de stats/decay/sickness do catode32** é confirmado como referência e
    adotado (adaptado).
  - O research recomendava M5GFX + TFT_eSPI como UI. ADR-021 já decidiu
    LVGL + camada de pet — esta ADR confirma (a UI consulta o Core para
    mood/stats).
  - O gap de **RTC catch-up** (todos tica em millis()) é dissolvido: o
    estado mora no Core (cloud), que tem relógio real; `advanceStats` é
    server-side on-demand. O device não precisa de RTC catch-up no MVP
    (sem estado local). O RTC do BM8563 é usado para wake timer
    (deep-sleep) e timestamp de Batches, não para advanceStats local.
- **Fecha a pendência do ADR-018** ("PET vivo" — estado/stats/decay/
  estágios/persistência): estado no Core (cloud-primary), 18 stats, decay
  em tiers, estágios sem morte, sem fallback NVS no MVP.
- **Fecha a pendência do ADR-022** (catálogo de MCP tools): 2 read + 10
  write tools definidas, + 4 triggers não-vozeados via Batch.
- **Consistente com ADR-021** (firmware): a camada de pet em LVGL consulta
  o Core para mood/stats; não detém estado. As Ações (`falar`, `dançar`,
  `expressar_emocao`, `ficar_tonto`, `dormir`) são as que o firmware
  executa via `lv_anim`/`lv_image`.
- **Consistente com ADR-016** (sem relay): o Core é um serviço nosso na
  rede, não um smartphone. O device termina TLS direto com o Core.
- **Fallback offline (NVS + advanceStats local no wake RTC)** é tópico
  futuro explícito. Se a UX cloud-primary for insuficiente (pet "congela"
  demais), a próxima ADR pode adicionar snapshot NVS + decay local +
  sync dirty-quando-online.
- **Minigames / store / economia** (catode32 tem): fora do escopo do MVP.
  `pet.play(game?)` aceita um parâmetro de jogo, mas os minigames são
  implementação futura.
- **ESP-NOW / BLE playdates** (catode32 tem): fora do escopo. O Robô Felipe
  é um pet único (não há multi-pet por enquanto).
- **Arte/sprites**: originais a desenhar (não reusar arte CC-BY-NC-ND do
  catode32 nem GPL do Sablina, ver research gap de licença). ADR-021 já
  registrou esta decisão.
- **Referências**: research
  [`../research/tamagotchi-pet-engine-ui.md`](../research/tamagotchi-pet-engine-ui.md);
  código do `moonbench/catode32` (branch `master`, `src/context.py`,
  `src/behavior_manager.py`, `src/time_system.py`, `src/config.py`,
  `src/entities/`) e `cifertech/TamaFi` (`TamaFi/TamaFi.ino`) verificados
  em 2026-08-31 via GitHub API.
