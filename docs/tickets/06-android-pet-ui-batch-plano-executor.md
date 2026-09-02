# 06: Android Pet UI + Batch + Plano executor

**What to build:** A UI do pet em Compose substitui o placeholder do
ticket 05. O app mostra a face do Robô Felipe (expressões por mood),
barras de stats (4-5 principais: saciedade, energia, felicidade, saúde +
sickness), e executa animações para as Ações do Plano (dance,
express_emotion, get_dizzy, sleep). Um `BatchClient` HTTPS envia Triggers
não-vozeados (button, shake — via sensores do celular no protótipo) ao
Core e recebe Planos de Ações. Um Plano executor executa cada Action do
Plano (speak → TTS nativo pt-BR se não-vozeado, ou já via WSS se vozeado;
dance/express_emotion/get_dizzy/sleep → animação UI).

O handler `pet_action` processa JSON custom do xiaozhi-server (enviado
pelo adapter Python do ticket 04): `{"type":"pet_action","action":
{"type":"dance"}}` → dispara a animação de dança na UI. Isto integra o
fluxo vozeado: Sobrinho fala "vamos brincar" → LLM chama `pet.play()` →
adapter envia `pet_action{dance}` ao device → app mostra dança + TTS fala
"yay!".

A face do pet é renderizada em Compose (Canvas ou ImageBitmap sprites por
mood). As 13 moods do ADR-023 mapeiam a expressões visuais (feliz, triste,
dormindo, faminto, exausto, sujo, tonto, assustado, brincalhão, curioso,
carinhoso, travesso, doente). As barras de stats usam `LinearProgressIndicator`
ou custom Compose. Animações via `AnimatedContent`/`rememberInfiniteTransition`.

O `BatchClient` (OkHttp HTTPS) envia:
`POST <CORE_URL>/batch` com Batch `{version, batchId, platformId, petId,
triggers:[...]}` e recebe PlanoDeAções `{version, batchId, actions[],
state}`. O `petId` é fixo no MVP (um pet). Os Triggers são gerados por
interações de UI (botão "brincar" → `manual` trigger) ou sensores (shake
do celular → `shake` trigger).

**Blocked by:** 02 (Core Batch endpoint + Plano de Ações — precisa do
`POST /batch` funcionando), 05 (Android WSS + Opus — precisa do app de
voz base rodando).

**Status:** in-review (PR #9)

- [ ] `android/app/src/main/java/com/example/robofelipe/ui/pet/` criado: `PetFace` (Canvas/ImageBitmap por mood), `StatBars` (4-5 stats principais), `PetScreen` (Scaffold com face + barras + botão push-to-talk).
- [ ] 13 moods do ADR-023 mapeados a expressões visuais (sprites ou Canvas drawing).
- [ ] Animações para as 5 Action types: `dance` (balanço/rotação), `express_emotion` (troca de face), `get_dizzy` (espirais/queda), `sleep` (Zzz), `speak` (boca se move / subtítulo).
- [ ] `BatchClient` HTTPS (OkHttp) implementado: `POST <CORE_URL>/batch` envia Batch, recebe PlanoDeAções.
- [ ] Plano executor implementado: itera `actions[]` do Plano, executa cada Action (speak→TTS nativo pt-BR se não veio via WSS; dance/express_emotion/get_dizzy/sleep→animação UI).
- [ ] `pet_action` handler implementado: processa JSON custom `{"type":"pet_action","action":{...}}` do xiaozhi-server e dispara a animação correspondente.
- [ ] Estado do pet consultado ao renderizar UI: `GET <CORE_URL>/pet/<petId>/state` no boot (após WiFi) e após cada Plano executado.
- [ ] Tela de configuração (URL do Core, URL do xiaozhi-server, petId) — substitui o campo de IP do bípede.
- [ ] `./gradlew test` passa.
- [ ] `./gradlew assembleDebug` builda.
- [ ] Demoable: fluxo completo — push-to-talk → LLM reconhece "vamos brincar" → chama `pet.play()` → Core muta stats → adapter envia `pet_action{dance}` → app mostra dança + TTS fala "yay!".
- [ ] Demoable: botão "alimentar" na UI → `BatchClient` envia `manual` trigger → Core retorna `[speak{que delícia!}]` → TTS nativo fala + stats atualizam na UI.
