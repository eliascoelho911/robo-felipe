# Wayfinder Map: Robô Felipe Quadrúpede

## Destination

Um plano completo e decisão-ready para construir o Robô Felipe
quadrúpede — todas as decisões arquiteturais resolvidas (ADRs 001–014),
e as decisões restantes de implementação e produto ticketadas de forma
que alguém possa começar a construir sem precisar decidir nada primeiro.

## Notes

### Contexto pré-existente (ADRs aceitas — não ticketadas neste map)

- **ADRs 001–007** — arquitetura do bípede, agnósticas ao corpo (C/C++,
  FreeRTOS, relay smartphone, sem câmera/display [revertidas], KWS local,
  ASR/TTS nuvem, OTA assinado). Ver `docs/decisions/`.
- **ADR-008** — pivot bípede→quadrúpede, mantém WROOM-32E-N4.
- **ADR-009** — display OLED SSD1306 (reverte ADR-004).
- **ADR-010** — 4 servos, 1 por pata.
- **ADR-011** — alimentação 2P 18650 paralelo + capacitor de buffer.
- **ADR-012** — ESP32-CAM como nó de streaming (reverte ADR-003).
- **ADR-013** — componente `espfriends/servo_dog_ctrl` para locomoção.
- **ADR-014** — migrar toolchain para ESP-IDF puro.

### Skills que toda sessão deve consultar

- `/grilling` e `/domain-modeling` — para resolver tickets de grilling.
- `/research` — para tickets de research.
- `/prototype` — para tickets de prototype.

### Preferências do projeto

- **Sobrinho (8 anos)** é o usuário final — UX deve priorizar vocabulário
  simples, latência tolerante, robustez de manuseio.
- **PT-BR** é a língua de interação.
- **ESP32-WROOM-32E-N4** é o MCU principal (sem PSRAM, 4MB flash).
- **ESP32-CAM** é nó separado de streaming (decoupled do WROOM).
- **Hardware de áudio** já projetado: SPH0645LM4H + MAX98357A + speaker 8Ω.
- **Pinout consolidado** em `hardware/pinout-quadrupede.md`.

### Referências rápidas

- `CONTEXT.md` — glossário do domínio.
- `hardware/pinout-quadrupede.md` — pinout completo do cão.
- `hardware/BOM-audio.md` — BOM e specs do áudio.
- `hardware/esp32-wroom-32e-n4.md` — specs e diagnóstico do MCU.
- `hardware/esp32-cam-datasheet.md` — specs da CAM.

## Decisions so far

- [01 — Provedores de nuvem para ASR, NLP e TTS](./issues/01-cloud-providers-voice.md) — Deepgram Nova-3 (ASR streaming), regras+gpt-4o-mini (NLP), Azure Neural TTS (TTS); ~$0,10–0,60/mês
- [09 — Provedores de voz self-hosted (i5/8GB CPU)](./issues/09-selfhosted-voice-providers.md) — Viável: faster-whisper small int8 + regras+Qwen2.5-3B (Ollama) + Piper pt_BR-cadu-medium; ~2,8–3,6s; box na LAN via HTTP preserva ADR-002; custo ~$3–5/mês eletricidade (ou ~$0 se box já on); decisão é de valores (privacidade/offline), não de custo

## Not yet specified

### F0: Decisão de produto — cloud (01) vs self-hosted (09) vs híbrido

Pesquisas 01 e 09 estão fechadas; falta a **decisão de produto**: qual
stack adotar como primária. Gradua como ticket de grilling quando o autor
quiser decidir — é HITL (depende dos valores do autor: privacidade do
sobrinho, tolerância a custo, desejo de offline, gosto por hobby/setup).
Insight da pesquisa 09: **Opção A mantém ambos** (box em casa, cloud fora),
logo a decisão pode ser "híbrido" em vez de binária. Não bloqueia os
tickets 02–08 (eles são agnósticos ao provedor).

### F1: Serviço de backend para CV (diferido pelo autor)

O autor deferiu: "Falaremos melhor desse serviço depois." A arquitetura
do backend é completamente undefined — onde roda (cloud/local/edge),
como a CAM envia o stream (TLS direto da CAM — que tem PSRAM e pode fazer
TLS — ou via relay/app), o que o backend faz com o vídeo (face detect,
scene description, gesture, object tracking), e como retorna resultados
ao robô (via app→WROOM WebSocket, ou direto). Múltiplas tickets podem
graduar desta névoa quando a discussão começar.

### F2: App Android — breakdown de implementação

A arquitetura é decidida (WebSocket relay, TLS termination, ASR→NLP→TTS
orchestration — ver ADRs 002/006). Mas o breakdown de implementação é
névoa: WebSocket client, permissões, integração com providers, reprodução
de TTS, gestão de sessão. Gradua conforme os tickets de cloud providers
(01) e WebSocket protocol (03) são resolvidos.

### F3: Arquitetura de tasks FreeRTOS

Core affinity (Core 0: rede+áudio I2S; Core 1: servos+KWS+display+IMU —
ver ADR-001), task graph, prioridades, tamanhos de stack. Emergem durante
a implementação — não é decisão arquitetural, é design de implementação.
Pode graduasr como tickets específicas quando a implementação começar.

## Out of scope

- **Upgrade para 8 servos** — ADR-010 rejeitou para o MVP; upgrade path
  preservado. Revisitar só se o sobrinho quiser mais expressividade.
- **CV embarcada na CAM** — ADR-012 escolheu streaming-only; processamento
  fica no backend. Revisitar se o backend não der conta.
- **Troca de MCU para S3 com PSRAM** — ADR-008 manteve WROOM-32E-N4.
  Revisitar só se a constraint de RAM provar-se insuficiente na prática.
- **Tutorial ACEBOTT (bípede)** — ADR-008 marcou como referência morta.
  Sketches `.ino` permanecem no repo mas não são o firmware do cão.
- **WebUI do servo_dog_ctrl** — ADR-013 desabilitou (`CONFIG_ESP_HI_WEB_CONTROL_ENABLED=n`).
  Controle vem pelo app via WebSocket, não por HTTP local.
