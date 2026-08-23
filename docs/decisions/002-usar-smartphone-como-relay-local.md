# ADR-002: Usar smartphone como relay local para TLS e roteamento de áudio

## Status
Accepted

## Date
2026-07-12

## Context

O robô-felipe precisa processar áudio remotamente: ASR (speech-to-text) e
TTS (text-to-speech) rodam em nuvem (Google, Azure, OpenAI, Whisper,
etc.). Toda comunicação com provedores de voz na internet exige **HTTPS
(TLS)**.

O firmware roda no **ESP32-WROOM-32E-N4** (sem PSRAM, ~333 KB de SRAM
livre no boot — ver ADR-001). O stack TLS do `esp-tls`/mbedTLS consome
**~40–50 KB de RAM** por sessão (handshake + buffers de cifra +
certificados). Em cima disso, o WiFi já consome ~70–80 KB quando ativo.

Restrições concretas:
1. **RAM:** TLS direto no ESP32 reduziria a folga de ~215 KB para
   ~115–125 KB — apertado para KWS + I2S bidirecional + tasks simultâneos.
2. **AP+STA simultâneo:** se o ESP32 mesmo fizesse TLS para a nuvem, ele
   ainda precisaria manter o AP ativo para o app de controle (como no
   tutorial de origem do kit bípede). AP+STA concorrentes compartilham o
   rádio e causam glitches de áudio (underruns no ring buffer de playback)
   em rajadas de tráfego.
3. **Manutenção de certificados:** atualizar CA bundles no ESP32 a cada
   rotação de certificado do provedor é fricção operacional alta.
4. **O usuário já carrega um smartphone** — o app mobile de controle
   (estendido do app do kit de origem ou próprio) já é parte do produto
   nas variantes bípede/quadrúpede.

## Decision

**Usar o smartphone (app mobile) como relay local.** O ESP32 fala
HTTP/WebSocket **puro** (sem TLS) na LAN com o app no celular; o app
fala **HTTPS** com o provedor de voz na nuvem, usando o stack TLS nativo
do SO mobile.

Topologia resultante:

```
                 Internet (nuvem: ASR / NLP / TTS)
                              │ HTTPS
                     ┌────────┴────────┐
                     │   Smartphone     │  ← app único:
                     │  (relay + UI)    │     • TLS termination
                     │                  │     • relay de áudio
                     │                  │     • UI de controle
                     └────────┬────────┘  ← WiFi casa ou 4G/5G
                              │ HTTP / WebSocket (LAN, sem TLS)
                      ┌────────┴────────────────────┐
                      │  ESP32-WROOM-32E-N4         │
                      │  • STA no router de casa    │
                      │  • KWS local (TinyML)       │
                      │  • Mic I2S → stream up      │
                      │  • Speaker I2S ← stream down│
                      │  • Atuadores + sensores     │
                      └─────────────────────────────┘
```

Consequências arquiteturais diretas:
- **ESP32 vira apenas STA** (cliente do router de casa). AP+STA
  simultâneo deixa de ser necessário — o app substitui o AP de controle.
- **TLS sai do ESP32 inteiramente** — +40–50 KB de RAM livre.
- **Uma única rede lógica** — o usuário conecta o celular ao WiFi de
  casa (ou usa 4G); o robô está no WiFi de casa; o app descobre o robô
  via mDNS ou IP fixo na LAN.

## Alternatives Considered

### ESP32 faz TLS diretamente para a nuvem (sem relay)

- **Prós:**
  - Sem hardware/app extra — robô fala direto com a nuvem.
  - Arquitetura de um só nó.
- **Contras:**
  - **TLS consome ~40–50 KB de RAM** no ESP32 — folga cai para ~115 KB.
  - **AP+STA obrigatório** (AP para controle + STA para internet) —
    rádio compartilhado, glitches de áudio em rajadas.
  - **Manutenção de CA bundle** no firmware a cada rotação de cert.
  - **Sem fallback offline** — se a internet cair, o robô não responde
    nem a comandos locais simples.
- **Rejeitada:** RAM apertada + AP+STA + manutenção de cert tornam o
  risco alto. O ganho (um nó a menos) não compensa.

### PC / Raspberry Pi como relay local fixo

- **Prós:**
  - Stack TLS maduro, recursos abundantes.
  - Script Python de ~50 linhas resolve o relay.
- **Contras:**
  - **Hardware extra** ligado 24/7 — custo, espaço, consumo.
  - **Não é portátil** — robô só funciona perto do PC.
  - **Redundante** com o smartphone que o usuário já carrega.
- **Rejeitada:** o smartphone cobre todas as funções do PC relay e ainda
  serve como UI de controle. Adicionar um PC é redundância.

### Segundo ESP32 como relay dedicado (na bancada)

- **Prós:**
  - Mantém tudo no ecossistema Espressif.
- **Contras:**
  - **Mesmo problema de TLS** — ESP32 como relay pagaria o custo de RAM
    do TLS de qualquer forma; não há ganho real.
  - **Mais um nó para manter** (firmware, alimentação, rede).
- **Rejeitada:** não resolve o problema de TLS; apenas move ele de chip.

## Consequences

### Positivas

- **+40–50 KB de RAM livre no ESP32** (sem TLS) — folga volta para
  ~215 KB.
- **Sem AP+STA simultâneo** — apenas STA, rádio dedicado, menos glitches
  de áudio.
- **TLS robusto** — stack nativo do iOS/Android, atualizado pelo SO;
  sem CA bundle no firmware.
- **Fallback 4G/5G** — se o WiFi de casa falhar, o celular usa rede
  móvel e o robô continua respondendo (via LAN WiFi ainda, ou o celular
  pode servir AP tethering).
- **UI unificada** — app faz controle + relay; usuário só instala um
  app.
- **Robô funciona fora de casa** — contanto que robô e celular
  compartilhem uma rede (tethering), o robô responde em qualquer lugar.

### Negativas

- **Robô depende do app estar ativo** para comandos por voz:
  - Android: serviço em background com notificação persistente funciona.
  - iOS: background networking é restrito (~30s de WebSocket em
    background); para voz sempre-on, o app precisaria estar em
    foreground, ou adotar "tap-to-talk" como fallback.
- **Desenvolvimento mobile extra** — o relay não é trivial: WebSocket
  client, permissões de microfone/rede/background, integração com
  provedor de voz, reprodução/roteamento de TTS. Mais trabalho que um
  script Python no PC.
- **Dois firmware/código bases** — firmware do ESP32 (C/C++) + app
  mobile (Kotlin/Swift/React Native/Flutter).
- **Latência adicional de um hop** — ESP32 → celular → nuvem. Na LAN é
  ~5 ms; tolerável para TTS streaming.
- **Debug mais distribuído** — problemas podem estar no ESP32, no app
  ou na nuvem; requer logs em todos os lados.

### Notas

- A descoberta do robô pelo app na LAN pode usar **mDNS** (serviço
  `_robo-felipe._tcp`) ou IP fixo configurável no app.
- O protocolo de áudio entre ESP32 e app deve ser **PCM cru 16 kHz
  16-bit mono** (ver ADR-006) — evita decoder no ESP32.
- Se no futuro for desejável voz sempre-on sem app em foreground,
  reconsiderar: (a) um relay fixo em casa (RPi), ou (b) migrar o ESP32
  para módulo com PSRAM e fazer TLS local — ver ADR-001 notas.
- Esta ADR torna **obsoleto o modo AP** do tutorial de origem (kit
  bípede, Lição 7). O app passa a controlar o robô via LAN/STA, não via AP
  dedicado do ESP32.
