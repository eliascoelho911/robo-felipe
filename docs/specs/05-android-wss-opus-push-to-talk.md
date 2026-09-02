# Spec 05: Android WSS + Opus + push-to-talk

**Ticket:** [05 — Android WSS + Opus + push-to-talk](../tickets/05-android-wss-opus-push-to-talk.md)
**Status:** done
**Blocked by:** 03 (xiaozhi-server rodando), 04 (adapter Python com pet tools)

## Problem Statement

O app Android existente é um controle remoto de robô bípede (17 comandos
ACEBOTT via HTTP `/control?var=robot&val=N`, DPad, ActionGrid). O Tamagotchi
precisa de um cliente de voz: conectar ao xiaozhi-server via WSS, capturar
áudio do microfone, codificar em Opus 16kHz mono 60ms, enviar frames binários
(push-to-talk: toque inicia/para), e reproduzir TTS que volta como Opus
binário. Nenhuma dessas funcionalidades existe no app atual — não há
WebSocket, Opus, áudio, ou `RECORD_AUDIO`.

## Solution

O app é transformado em cliente de voz Tamagotchi. Os packages `audio/`
(OpusEncoder, OpusDecoder, OpusStreamPlayer, EnhancedAudioManager) e
`network/` (WebSocketManager com handshake xiaozhi) são portados do
`xiaoniu/xiaozhi-ai-android` (48★, Kotlin+Compose, OkHttp+Gson) como base e
adaptados ao app existente (reusar toolchain, tema, MainActivity, padrão
ViewModel/StateFlow). O código do bípede (RobotCommand, DataRepository,
MainScreen DPad/ActionGrid, NavigationKeys) é descartado. A UI de pet fica
como placeholder neste ticket — a UI completa vem no ticket 06.

## User Stories

1. As a Sobrinho, I want tocar um botão na tela para falar com o pet, so
   que eu possa conversar sem precisar de wake word (push-to-talk, ADR-021).
2. As a Sobrinho, I want soltar o botão para parar de falar, so that o
   pet saiba que terminei minha frase e possa responder.
3. As a Sobrinho, I want ouvir a resposta do pet no speaker, so that eu
   possa ouvir o que o pet diz (TTS via Opus → decode → AudioTrack).
4. As a Sobrinho, I want interromper o pet enquanto ele fala, so que eu
   possa dizer algo novo sem esperar ele terminar (abort: user_interrupt).
5. As a desenvolvedor, I want o app conectar ao xiaozhi-server via WSS
   com o handshake xiaozhi, so that o protocolo de voz funcione
   end-to-end.
6. As a desenvolvedor, I want o handshake enviar
   `{"type":"hello","version":1,"transport":"websocket","audio_params":
   {"format":"opus","sample_rate":16000,"channels":1,"frame_duration":60}}`,
   so that o servidor saiba o formato de áudio esperado.
7. As a desenvolvedor, I want o handshake receber
   `{"type":"hello","transport":"websocket","session_id":"..."}`, so that
   eu tenha o session_id para enviar mensagens de listen start/stop.
8. As a desenvolvedor, I want enviar
   `{"session_id":"...","type":"listen","state":"start","mode":"auto"}`,
   so que o servidor comece a processar áudio (ASR).
9. As a desenvolvedor, I want enviar
   `{"session_id":"...","type":"listen","state":"stop"}`, so que o
   servidor saiba que terminei de falar e dispare o LLM.
10. As a desenvolvedor, I want enviar
    `{"type":"abort","reason":"user_interrupt"}`, so that o servidor
    interrompa o TTS em andamento quando eu começar a falar de novo.
11. As a desenvolvedor, I want capturar áudio via AudioRecord (16kHz,
    16-bit, mono), so that o formato bata com o esperado pelo Opus
    encoder.
12. As a desenvolvedor, I want codificar PCM em Opus 16kHz mono 60ms
    frames, so that o áudio seja compactado para envio via WSS binário.
13. As a desenvolvedor, I want decodificar Opus binário recebido em PCM,
    so that eu possa streamar ao speaker via AudioTrack.
14. As a desenvolvedor, I want headers `Device-Id`, `Client-Id`,
    `Protocol-Version: 1` no handshake WSS, so that o servidor identifique
    o device e o protocolo.
15. As a desenvolvedor, I want permissão `RECORD_AUDIO` com runtime
    request em Compose, so que o app possa acessar o microfone (Android 6+).
16. As a desenvolvedor, I want OkHttp + Gson no version catalog, so que
    eu tenha WebSocket client e JSON parsing disponíveis.
17. As a desenvolvedor, I want o código do bípede removido (RobotCommand,
    DataRepository, MainScreen DPad, NavigationKeys), so que o app fique
    limpo e sem código morto.
18. As a desenvolvedor, I want `MainActivity` atualizado para renderizar
    a nova tela de voz (placeholder — botão push-to-talk + status de
    conexão), so que o app funcione como ponto de partida para o ticket 06.
19. As a desenvolvedor, I want `./gradlew test` passando, so que eu saiba
    que o código está íntegro.
20. As a desenvolvedor, I want `./gradlew assembleDebug` buildando, so
    that eu saiba que o app compila.
21. As a desenvolvedor, I want o app ser demoable: conecta ao
    xiaozhi-server, push-to-talk envia áudio, LLM responde, TTS toca no
    speaker, so that eu valide o fluxo de voz end-to-end.

## Implementation Decisions

### Handshake WSS (protocolo xiaozhi)

1. Conectar ao `ws://<server>:8000/xiaozhi/v1/` com headers `Device-Id`,
   `Client-Id`, `Protocol-Version: 1`.
2. Enviar `{"type":"hello","version":1,"transport":"websocket",
   "audio_params":{"format":"opus","sample_rate":16000,"channels":1,
   "frame_duration":60}}`.
3. Receber `{"type":"hello","transport":"websocket","session_id":"..."}` —
   armazenar `session_id`.
4. Pronto para push-to-talk.

### Push-to-talk (fluxo de captura)

1. Toque no botão → enviar `{"session_id":"...","type":"listen",
   "state":"start","mode":"auto"}`.
2. Iniciar AudioRecord (16kHz, 16-bit, mono).
3. Loop: ler PCM chunk → OpusEncoder.encode → enviar frame binário WSS.
4. Soltar botão → parar AudioRecord → enviar
   `{"session_id":"...","type":"listen","state":"stop"}`.
5. Servidor processa (ASR → LLM → TTS) → envia Opus binário de volta.

### TTS playback (fluxo de recepção)

1. Receber frame binário WSS → OpusDecoder.decode → PCM.
2. Stream PCM ao AudioTrack (16kHz, 16-bit, mono, streaming mode).
3. `OpusStreamPlayer` gerencia buffer e reprodução contínua.

### Interrupção (abort)

1. Se o Sobrinho toca o botão enquanto o pet está falando (TTS em
   andamento), enviar `{"type":"abort","reason":"user_interrupt"}`.
2. Parar o AudioTrack (TTS playback).
3. Iniciar novo fluxo de captura (listen start).

### Opus codec (portado do xiaoniu/xiaozhi-ai-android)

- `OpusEncoder`: PCM 16kHz mono → Opus 60ms frames. JNI wrapper sobre
  libopus (nativo).
- `OpusDecoder`: Opus frames → PCM 16kHz mono. JNI wrapper.
- `OpusStreamPlayer`: buffer de PCM decodificado → AudioTrack streaming.
- `EnhancedAudioManager`: orquestra AudioRecord (captura) + AudioTrack
  (playback) + Opus codec.

### WebSocketManager (portado do xiaoniu)

- OkHttp `WebSocket` client.
- handshake xiaozhi (hello → session_id).
- envio de frames binários (Opus) e textuais (JSON de controle).
- recepção de frames binários (TTS Opus) e textuais (pet_action, hello,
  tts messages).
- reconnect automático com backoff.
- callbacks: `onConnected`, `onMessage(text)`, `onMessage(bytes)`,
  `onDisconnected`, `onError`.

### Módulos a criar

- `audio/`: OpusEncoder, OpusDecoder, OpusStreamPlayer,
  EnhancedAudioManager.
- `network/`: WebSocketManager (OkHttp WebSocket + handshake xiaozhi +
  reconnect).
- `viewmodel`: VoiceViewModel (StateFlow de estado de conexão, captura,
  playback; orquestra WebSocketManager + AudioManager).

### Módulos a descartar

- `data/RobotCommand.kt` (17 comandos ACEBOTT).
- `data/DataRepository.kt` (HTTP `/control`).
- `ui/main/MainScreen.kt` (DPad + ActionGrid).
- `NavigationKeys.kt` (Nav3 órfão).
- `MainScreenViewModel.kt` (lógica do bípede).

### Módulos a reusar

- `MainActivity.kt` (atualizar setContent).
- `theme/` (Material3, dark/light).
- `build.gradle.kts` + `libs.versions.toml` (toolchain).
- `MainScreenViewModel.kt` padrão MVVM+StateFlow (reusar padrão, reescrever
  conteúdo).

### Dependências a adicionar

- OkHttp (WebSocket client + HTTP).
- Gson (JSON parsing).

Em `libs.versions.toml` + `app/build.gradle.kts`.

### Permissão RECORD_AUDIO

- Adicionar `<uses-permission android:name="android.permission.RECORD_AUDIO"/>`
  no AndroidManifest.
- Runtime request em Compose (`rememberLauncherForActivityResult` com
  `ActivityContracts.RequestPermission`).

### Tela placeholder (ticket 06 traz UI de pet)

- Botão grande "Falar" (push-to-talk).
- Status de conexão (conectado/desconectado).
- Status de captura (ouvindo/parado).
- Sem UI de pet (face, stats) — vem no ticket 06.

## Testing Decisions

### O que faz um bom teste

Testar comportamento externo (sem rede real, sem áudio real). WebSocket
mockado; Opus round-trip testável sem dispositivo.

### Módulos testados

- **Opus round-trip** (unit test): PCM chunk → OpusEncoder.encode →
  OpusDecoder.decode → PCM. Verifica que o round-trip preserva o sinal
  (aproximadamente — Opus é lossy, mas a forma de onda deve ser
  reconhecível). Pode usar um tom senoide de teste.
- **WebSocketManager** (unit test com mock OkHttp): testa handshake
  (envia hello, recebe session_id), envio de listen start/stop, recebimento
  de frames binários (callback onMessage(bytes)), reconnect com backoff.
- **VoiceViewModel** (unit test): testa estado (conectado/desconectado,
  ouvindo/parado) em resposta a eventos do WebSocketManager mockado.

### Prior art

- `MainScreenViewModelTest.kt` já existe (JUnit4 + `runTest`, padrão
  ViewModel+StateFlow).
- `MainScreenTest.kt` já existe (Compose test instrumentado).
- xiaoniu/xiaozhi-ai-android tem tests de Opus e WebSocket como
  referência.

## Out of Scope

- UI de pet (PetFace, StatBars, animações) — Spec 06.
- BatchClient (HTTPS ao Core para triggers não-vozeados) — Spec 06.
- Plano executor — Spec 06.
- pet_action handler — Spec 06.
- Tela de configuração — Spec 06.
- Wake word "Felipe" (Gap #1) — MVP usa push-to-talk.

## Further Notes

- **Referência de implementação:** `xiaoniu/xiaozhi-ai-android` (48★,
  Kotlin+Compose, OkHttp+Gson). Portar `audio/` e `network/` como base
  self-contained, adaptar ao app existente.
- **Opus JNI:** o Opus encoder/decoder usa JNI sobre libopus nativa. O
  xiaoniu já empacota a libopus para ARM/ARM64. Verificar compatibilidade
  com o toolchain do app (Kotlin 2.3.20, AGP 9).
- **speak dual-path:** No fluxo de voz (este ticket), o `speak` vem do
  LLM → EdgeTTS → Opus → device (stream de áudio via WSS). O `speak` do
  Plano de Ações (não-vozeado, Spec 06) usa TTS nativo Android pt-BR. Esta
  spec só cobre o fluxo vozeado.
- **pet_action handler:** O WebSocketManager recebe mensagens textuais
  com `{"type":"pet_action","action":{...}}` do adapter Python (Spec 04).
  Este ticket pode ignorar pet_action (só logar) — o handler real vem no
  Spec 06.
- **Referências:** ADR-021 (firmware push-to-talk, AEC), ADR-022 (Nuvem,
  protocolo WSS), ticket 05.