# 05: Android WSS + Opus + push-to-talk

**What to build:** O app Android é transformado de controle remoto de
robô bípede em cliente de voz do Tamagotchi. O app conecta ao
xiaozhi-server via WebSocket Secure (WSS), captura áudio do microfone,
encode em Opus 16kHz mono 60ms, e envia frames binários ao servidor
(push-to-talk: toque no botão inicia/para a captura). O TTS volta como
Opus binário e é decodificado/streamado para o speaker.

A referência de implementação é o `xiaoniu/xiaozhi-ai-android` (48★,
Kotlin+Compose, OkHttp+Gson): portar os packages `audio/` (OpusEncoder,
OpusDecoder, OpusStreamPlayer, EnhancedAudioManager) e `network/`
(WebSocketManager com handshake xiaozhi) como base. Adaptar ao app
existente (reusar toolchain Gradle 9.1/AGP 9/Kotlin 2.3.20, tema
Material3, MainActivity, padrão ViewModel/StateFlow).

O handshake WSS segue o protocolo xiaozhi: `{"type":"hello","version":1,
"transport":"websocket","audio_params":{"format":"opus","sample_rate":16000,
"channels":1,"frame_duration":60}}` → servidor responde `{"type":"hello",
"transport":"websocket","session_id":"..."}`. Push-to-talk envia
`{"session_id":"...","type":"listen","state":"start","mode":"auto"}` /
`"state":"stop"}`. Interrupção: `{"type":"abort","reason":
"user_interrupt"}`. Headers: `Device-Id`, `Client-Id`,
`Protocol-Version: 1`.

**Descartar** do app bípede: `RobotCommand` (17 comandos ACEBTT),
`DataRepository` (HTTP `/control?var=robot&val=N`), `MainScreen` (DPad +
ActionGrid), `NavigationKeys` (Nav3 órfão). **Adicionar** permissão
`RECORD_AUDIO` no AndroidManifest.

**Blocked by:** 03 (xiaozhi-server rodando para testar handshake WSS),
04 (adapter Python com pet tools para o fluxo completo de voz).

**Status:** done (PR #4)

- [x] `android/app/src/main/java/com/example/robofelipe/audio/` criado: `OpusEncoder`, `OpusDecoder`, `OpusStreamPlayer`, `EnhancedAudioManager` (portados/adaptados do xiaoniu).
- [x] `android/app/src/main/java/com/example/robofelipe/network/` criado: `WebSocketManager` (OkHttp WebSocket, handshake xiaozhi, envio/recepção de frames Opus binários + JSON textuais).
- [x] Permissão `RECORD_AUDIO` adicionada ao `AndroidManifest.xml` (com runtime request em Compose).
- [x] OkHttp + Gson adicionados a `libs.versions.toml` e `build.gradle.kts`.
- [x] Push-to-talk funcional: toque inicia captura → encode Opus → send WSS; toque para → `listen stop`.
- [x] TTS playback: Opus binário recebido → decode → stream ao speaker.
- [x] `RobotCommand`, `DataRepository`, `MainScreen` (DPad/ActionGrid), `NavigationKeys` removidos.
- [x] `MainActivity` atualizado para renderizar a nova tela de voz (placeholder — UI de pet vem no ticket 06).
- [x] `./gradlew test` passa (27 testes unitários).
- [x] `./gradlew assembleDebug` builda sem erros (NDK arm64-v8a + x86_64, CMake, prefab opus).
- [x] Demoable: app conecta ao xiaozhi-server (WSS), push-to-talk envia áudio, LLM responde, TTS toca no speaker (requer hardware + xiaozhi-server rodando).
