# Spec 06: Android Pet UI + Batch + Plano executor

**Ticket:** [06 — Android Pet UI + Batch + Plano executor](../tickets/06-android-pet-ui-batch-plano-executor.md)
**Status:** ready-for-agent
**Blocked by:** 02 (Core Batch endpoint), 05 (Android WSS + Opus)

## Problem Statement

O app Android do ticket 05 tem voz funcionando (WSS + Opus + push-to-talk)
mas sem UI de pet — só um botão placeholder. O Sobrinho precisa ver a face
do Robô Felipe com expressões por mood, barras de stats, e animações das
Ações do Plano (dance, express_emotion, get_dizzy, sleep). Além disso, o
app precisa enviar Triggers não-vozeados (button, shake, manual) ao Core
via HTTPS e executar os Planos de Ações que voltam. E precisa processar
mensagens `pet_action` do xiaozhi-server (adapter Python) para executar
animações no fluxo vozeado.

## Solution

Uma UI de pet em Compose (Canvas/ImageBitmap por mood, StatBars,
animações) substitui o placeholder do ticket 05. Um `BatchClient` HTTPS
envia Batches ao Core e recebe Planos. Um Plano executor executa cada
Action do Plano. Um `pet_action` handler processa JSON custom do
xiaozhi-server e dispara animações. Uma tela de configuração substitui o
campo de IP do bípede.

## User Stories

1. As a Sobrinho, I want ver a face do Felipe com expressões que mudam
   conforme o mood, so that eu saiba como ele está se sentindo (feliz,
   faminto, dormindo, etc.).
2. As a Sobrinho, I want ver barras de stats (saciedade, energia,
   felicidade, saúde), so that eu saiba o que o pet precisa.
3. As a Sobrinho, I want ver o pet dançar quando eu peço para brincar,
   so that a interação seja divertida e visual.
4. As a Sobrinho, I want ver o pet ficar tonto quando eu sacudo o
   celular, so that a interação física tenha resposta visual.
5. As a Sobrinho, I want ver o pet dormir quando ele descansa, so that
   eu saiba que ele está recuperando energia.
6. As a Sobrinho, I want ouvir o pet falar frases de resposta (TTS), so
   que a conversa não-vozeada tenha áudio (botão "alimentar" → pet diz
   "que delícia!").
7. As a Sobrinho, I want um botão "alimentar" na UI, so that eu possa
   cuidar do pet sem falar.
8. As a Sobrinho, I want um botão "brincar" na UI, so that eu possa
   brincar com o pet sem falar.
9. As a Sobrinho, I want a configuração de URLs (Core, xiaozhi-server)
   e petId na tela, so that eu possa apontar o app para diferentes
   ambientes (localhost, VPS).
10. As a desenvolvedor, I want o `BatchClient` enviar
    `POST <CORE_URL>/batch` com Batch e receber PlanoDeAções, so that os
    triggers não-vozeados fluam pelo Core.
11. As a desenvolvedor, I want o Plano executor iterar `actions[]` e
    executar cada Action, so that `speak` vire TTS nativo, `dance` vire
    animação, etc.
12. As a desenvolvedor, I want o `pet_action` handler processar
    `{"type":"pet_action","action":{"type":"dance"}}` do xiaozhi-server,
    so that animações do fluxo vozeado funcionem.
13. As a desenvolvedor, I want o estado do pet consultado no boot
    (`GET /pet/:id/state`) e após cada Plano, so that a UI mostre dados
    atuais.
14. As a desenvolvedor, I want 13 moods mapeados a expressões visuais,
    so que cada mood tenha uma face distinta.
15. As a desenvolvedor, I want animações para as 5 Action types (dance,
    express_emotion, get_dizzy, sleep, speak), so que cada Action tenha
    resposta visual.
16. As a desenvolvedor, I want `./gradlew test` passando, so that eu
    saiba que o código está íntegro.
17. As a desenvolvedor, I want `./gradlew assembleDebug` buildando, so
    that o app compile.
18. As a desenvolvedor, I want o fluxo completo demoable: push-to-talk →
    LLM reconhece "vamos brincar" → `pet.play()` → adapter envia
    `pet_action{dance}` → app mostra dança + TTS fala "yay!", so that eu
    valide o fluxo end-to-end.
19. As a desenvolvedor, I want o fluxo não-vozeado demoable: botão
    "alimentar" → `BatchClient` envia `manual` trigger → Core retorna
    `[speak{que delícia!}]` → TTS nativo fala + stats atualizam, so that
    eu valide o fluxo Batch→Plano.

## Implementation Decisions

### PetFace (Canvas/ImageBitmap por mood)

A face do pet é renderizada em Compose. Duas abordagens possíveis
(decidir na implementação):

- **Canvas drawing**: desenhar olhos, boca, corpo com `DrawScope`
  (primitivas Compose). Leve, sem assets, mas menos expressivo.
- **ImageBitmap sprites**: carregar sprites RGB565 por mood (1 sprite por
  expressão). Mais expressivo, mas exige assets (desenhados originalmente
  — não reusar arte de terceiros, ADR-021).

MVP: Canvas drawing com olhos/boca que mudam por mood (sem sprites
externos). Sprites podem ser adicionados depois.

### 13 moods → expressões visuais (ADR-023)

Mapeamento mood → expressão (MVP usa 8 moods deriváveis do Core; 5
restantes podem aparecer se sickness/flags forem não-zero no snapshot):

| Mood | Expressão visual |
|:--|:--|
| happy | olhos arregalados, boca sorrindo |
| hungry | olhos pequenos, boca aberta (fome) |
| tired | olhos semi-fechados, boca reta |
| dirty | nuvens de poeira ao redor |
| playful | olhos piscando, boca aberta (empolgação) |
| curious | olhos grandes, cabeça inclinada |
| excited | olhos brilhantes, boca grande (carinho) |
| mischievous | olhos tortos, boca marota |
| sad | olhos caídos, boca invertida |
| sleepy | olhos fechados, Zzz |
| bored | olhos retos, boca reta |
| dizzy | espirais nos olhos, corpo torto |
| scared | olhos arregalados, boca tremendo |

### StatBars (4-5 stats principais)

Barras de progresso para as stats mais visíveis:

- Saciedade (fullness) — ícone comida.
- Energia (energy) — ícone bateria.
- Felicidade (happiness) — ícone coração.
- Saúde (health) — ícone cruz/curativo.
- Sickness (se não-zero) — ícone termômetro.

Usar `LinearProgressIndicator` ou custom Compose bar com cor por faixa
(verde > 60, amarelo 30-60, vermelho < 30). As 13 stats restantes são
implícitas no mood/comportamento (não mostradas como barras).

### Animações para 5 Action types

- `dance`: balanço/rotação do corpo via `rememberInfiniteTransition` (loop
  por `duration_ms`).
- `express_emotion`: troca de face (AnimatedContent) para a emotion
  especificada.
- `get_dizzy`: espirais nos olhos + corpo tombando (animação curta).
- `sleep`: olhos fechados + Zzz flutuando (animação contínua por
  `duration_ms`).
- `speak`: boca se move (abre/fecha) + subtítulo do texto na base da tela.

### BatchClient (HTTPS ao Core)

OkHttp HTTP client. `POST <CORE_URL>/batch` com body:

```json
{
  "version": 1,
  "batchId": "uuid",
  "platformId": "android-<device-id>",
  "petId": "<petId>",
  "triggers": [
    {"id": "uuid", "kind": "manual", "timestamp": 1234567890, "payload": {}}
  ]
}
```

Resposta: `PlanoDeAções` (`{version, batchId, actions[], state}`).
`batchId` gerado client-side (UUID). `petId` fixo no MVP (configurável na
tela de config).

### Plano executor

Itera `actions[]` do Plano e executa cada Action:

- `speak({text})` → se não veio via WSS (fluxo não-vozeado), usar TTS
  nativo Android pt-BR (`android.speech.tts`). Se veio via WSS, já foi
  reproduzido pelo OpusStreamPlayer (ignorar).
- `dance({duration_ms})` → disparar animação de dança por `duration_ms`.
- `express_emotion({emotion})` → trocar face para a emotion.
- `get_dizzy({intensity})` → disparar animação de tontura.
- `sleep({duration_ms})` → disparar animação de dormir por `duration_ms`.

Após executar todas as actions, atualizar a UI com o `state` do Plano
(PetStateSnapshot: stage, mood, health, stats, etc.).

### pet_action handler

O WebSocketManager (Spec 05) recebe mensagens textuais. O handler
processa `{"type":"pet_action","action":{...}}`:

- Extrai `action` do JSON.
- Dispara a animação correspondente (dance, express_emotion, get_dizzy,
  sleep) na UI.
- `speak` não vem via `pet_action` — no fluxo vozeado, `speak` é o TTS
  do LLM via Opus stream.

### Estado no boot e após Plano

- No boot (após WiFi conectar): `GET <CORE_URL>/pet/<petId>/state` →
  preencher UI (face, barras).
- Após cada Plano executado: usar o `state` incluído no Plano (sem
  round-trip extra).
- Após cada `pet_action` (fluxo vozeado): opcionalmente consultar
  `GET /pet/:id/state` para sincronizar stats (o adapter já mutou via
  Core HTTP).

### Tela de configuração

Substitui o campo de IP do bípede. Campos:

- URL do Core (`http://<host>:3000`).
- URL do xiaozhi-server (`ws://<host>:8000/xiaozhi/v1/`).
- petId (fixo no MVP, mas editável para testes).

Persistir em `SharedPreferences` ou `DataStore`.

### Triggers de UI e sensores

- Botão "alimentar" na UI → trigger `manual` (payload `{action: "feed"}`).
- Botão "brincar" → trigger `manual` (payload `{action: "play"}`).
- Shake do celular (SensorManager) → trigger `shake` (payload
  `{intensity: 0.8}`). Opcional no MVP — pode ser apenas botão.

### Módulos a criar

- `ui/pet/`: PetFace, StatBars, PetScreen (Scaffold com face + barras +
  botões), PetAnimations (dance, dizzy, sleep, speak).
- `data/`: ContractTypes (kotlinx.serialization dos schemas do
  packages/contract), BatchClient, PetRepository (consulta estado + envia
  Batch).
- `viewmodel`: PetViewModel (StateFlow de estado do pet, orquestra
  BatchClient + pet_action handler + TTS).

### TTS nativo Android (speak não-vozeado)

`android.speech.tts.TextToSpeech` com `Locale("pt", "BR")`. Usado quando
`speak` vem do Plano de Ações (fluxo não-vozeado), não do WSS (fluxo
vozeado). O TTS nativo é síncrono e de qualidade razoável para frases
curtas.

## Testing Decisions

### O que faz um bom teste

Testar comportamento externo da UI (Compose test) e lógica do
Plano executor/BatchClient (unit test com mocks).

### Módulos testados

- **PetFace** (Compose test, instrumentado): renderiza com mood "happy" →
  verifica elementos visuais esperados (olhos, boca). Renderiza com
  mood "hungry" → verifica expressão diferente.
- **StatBars** (Compose test): renderiza com stats {fullness: 80, energy:
  20} → verifica barras com valores e cores corretas.
- **Plano executor** (unit test): dado Plano com
  `[speak{oi}, dance{3000}]` → verifica que TTS é chamado com "oi" e que
  animação de dança é disparada. Mock TTS e animation callbacks.
- **BatchClient** (unit test com mock OkHttp): dado Batch válido →
  verifica que POST é feito com body correto e resposta Plano é parseada.
- **pet_action handler** (unit test): dado
  `{"type":"pet_action","action":{"type":"dance"}}` → verifica que
  animação de dança é disparada.

### Prior art

- `MainScreenTest.kt` já existe (Compose test instrumentado, verifica
  existência de botões).
- `MainScreenViewModelTest.kt` já existe (JUnit4 + `runTest`).
- kotlinx.serialization já habilitado no projeto (plugin aplicado).

## Out of Scope

- WSS + Opus + push-to-talk — Spec 05.
- Core HTTP REST + Batch endpoint — Specs 01, 02.
- Adapter Python — Spec 04.
- xiaozhi-server config — Spec 03.
- Sprites RGB565 externos (MVP usa Canvas drawing).
- Shake via SensorManager (opcional — MVP pode usar só botões).
- Minigames (fora do escopo, ADR-023).
- Fase 2 (CoreS3) — o app Android é temporário, substituído pelo CoreS3.

## Further Notes

- **speak dual-path (converge aqui):** No fluxo vozeado (WSS), `speak` é o
  TTS do LLM → Opus → device (já reproduzido pelo OpusStreamPlayer do
  Spec 05). No fluxo não-vozeado (Batch), `speak` do Plano usa TTS nativo
  Android pt-BR. O Plano executor distingue: se o `speak` veio de um
  Batch (não-vozeado), usa TTS nativo; se veio via WSS (vozeado), ignora
  (já foi reproduzido). Na Fase 2 (CoreS3), ambos convergem para o
  protocolo xiaozhi (Opus).
- **Contrato consumido:** O app consome `packages/contract` (schemas Zod)
  para tipos do Batch e Plano. Como o Android é Kotlin, usar
  `kotlinx.serialization` com classes de dados espelhando os schemas, ou
  gerar via quicktype a partir do JSON Schema. Decidir na implementação.
- **pet_action vs Plano:** `pet_action` vem do xiaozhi-server (fluxo
  vozeado, adapter Python). Plano vem do Core (fluxo não-vozeado,
  BatchClient). Ambos disparam animações na UI, mas por caminhos
  diferentes. O `pet_action` não tem `state` (só a animação); o Plano tem
  `state` (snapshot para atualizar UI).
- **Referências:** ADR-018 (contrato Batch→Plano), ADR-023 (18 stats, 13
  moods, 5 Action types), ADR-022 (speak dual-path), ticket 06.