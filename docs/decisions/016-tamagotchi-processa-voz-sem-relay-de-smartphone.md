# ADR-016: Tamagotchi processa voz sem relay de smartphone

## Status
Accepted

## Context

O projeto Robô Felipe acumula duas variantes de corpo — **bípede** e
**quadrúpede** — ambas apoiadas na mesma arquitetura de voz definida em
conjunto pelas ADR-002, ADR-006 e ADR-007:

- **ADR-002** — o smartphone funciona como **relay local**: o ESP32 fala
  HTTP/WebSocket puro (sem TLS) na LAN com o app, e o app fala HTTPS com
  a nuvem. O ESP32 vira apenas STA; o TLS sai do firmware inteiramente
  (+40–50 KB de RAM livre).
- **ADR-006** — ASR e TTS rodam em **nuvem**, acessados via relay.
- **ADR-007** — OTA é **empurrado** ao ESP32 pelo WebSocket do relay.

Essa arquitetura foi desenhada para um robô de chão (bípede/quadrúpede),
usado **perto de um smartphone** que o usuário já carrega.

A nova variante em estudo é um **Tamagotchi**: um pet de bolso, com
display, microfone e alto-falante, **sem câmera, sem pernas, sem
servos**. A premissa do brinquedo é ser **autocontido** — a criança
pega, conversa, e ele responde. Não há configuração, não há segundo
dispositivo, não há "app para ligar o brinquedo".

Para essa forma de corpo, a dependência do smartphone da ADR-002 **rompe
a premissa central do Tamagotchi**:

1. **Mata a experiência "pega e brinca".** Um Tamagotchi que só fala com
   o app de celular aberto não é um Tamagotchi — é um periférico de
   telefone. A criança de 8 anos (o sobrinho) quer pegar o bichinho e
   interagir; não quer depender de um celular que pode estar longe,
   descarregado, ou com o app em background restrito (iOS limita
   WebSocket em background a ~30 s).
2. **A criança não gerencia app.** Um pet que para de responder porque
   "o app fechou" é um brinquedo quebrado aos olhos do usuário final.
3. **Portabilidade real.** O bichinho é carregado para todo lado — no
   bolso, no quintal, na escola. Exigir um smartphone pareado em cada
   local vira fricção, não diversão.
4. **O segundo dispositivo é o problema.** No quadrupede o relay é
   aceitável porque o robô vive no chão de casa, perto do celular. O
   Tamagotchi vive no bolso da criança; amarrá-lo a um segundo aparelho
   derrota o form factor.

A questão é: **como o Tamagotchi acessa a pipeline de voz (nuvem de
ASR/NLP/TTS) sem o relay?**

Restrições herdadas:

- **TLS é obrigatório** para qualquer provedor de voz na internet. Sem
  relay, o ESP32 precisa terminar TLS ele mesmo — custo de ~40–50 KB de
  RAM por sessão (handshake + buffers de cifra + CA bundle), conforme
  medido no ADR-002.
- **KWS local permanece** (ADR-005) — o gatilho "Felipe" continua no
  dispositivo, não na nuvem. Isso não muda com esta decisão.
- **Qualidade de voz em português** — ASR/TTS on-device de boa qualidade
  para pt-BR não existe no ESP32 hoje (modelos pequenos demais). A nuvem
  ainda é o caminho viável para uma conversa natural.

## Decision

**Para a variante Tamagotchi, o dispositivo é autocontido: não depende
de smartphone como relay.** O ESP32 conecta-se diretamente ao WiFi
disponível como STA e **termina TLS ele mesmo**, falando HTTPS direto
com o provedor de voz na nuvem. O app mobile deixa de ser parte do
produto para esta variante.

Topologia resultante (Tamagotchi):

```
               Internet (nuvem: ASR / NLP / TTS)
                              │ HTTPS (TLS termina no ESP32)
                     ┌────────┴────────────────────┐
                     │  ESP32-S3 + PSRAM           │
                     │  • STA no WiFi disponível  │
                     │  • KWS local (TinyML)       │
                     │  • TLS próprio (mbedTLS)    │
                     │  • Mic I2S → stream up      │
                     │  • Speaker I2S ← stream down│
                     │  • Display TFT (pet UI)     │
                     │  • Bateria + charge IC      │
                     └─────────────────────────────┘
```

Sem segundo nó. Sem app. Sem relay.

### Escopo da decisão

Esta ADR é **específica da variante Tamagotchi**. As ADR-002, ADR-006 e
ADR-007 continuam **válidas para o quadrupede** (robô de chão, usado
perto do smartphone). O Tamagotchi herda delas apenas o que for
compatível com "sem relay":

- **ADR-005 (KWS local):** mantida integralmente — o gatilho de voz
  continua no dispositivo, offline-capable.
- **ADR-006 (ASR/TTS na nuvem):** o *destino* (nuvem) é o mesmo; o que
  muda é o *caminho* — direto via TLS do próprio ESP32, em vez de
  indireto via relay.
- **ADR-002 (relay smartphone):** **não se aplica** ao Tamagotchi.
- **ADR-007 (OTA push via relay):** **não se aplica** ao Tamagotchi —
  ver Consequences para o caminho de OTA desta variante.

## Alternatives Considered

### Manter o relay de smartphone (ADR-002 as-is) para o Tamagotchi

- **Prós:**
  - Reuso integral da arquitetura e do app mobile já em
    desenvolvimento (Android, ADR-002).
  - ESP32 sem PSRAM (WROOM-32E-N4) continua viável — TLS sai do chip.
  - 4G/5G do celular como fallback de internet.
- **Contras:**
  - **Derrota o form factor Tamagotchi** — vira um acessório de
    celular, não um bichinho de bolso.
  - **Criança de 8 anos não gerencia app** em foreground / permissões
    de microfone / notificação persistente. UX inaceitável para o
    usuário final.
  - **iOS**: WebSocket em background limitado a ~30 s — voz sempre-on
    exige app aberto, inviável para brinquedo.
  - **Portabilidade zero** — só funciona onde há celular pareado.
- **Rejeitada:** a dependência do app contradiz a premissa do
  Tamagotchi. O ganho (reuso de código) não compensa a perda da
  experiência de brinquedo.

### Relay fixo em casa (Raspberry Pi / mini-PC) no lugar do celular

- **Prós:**
  - TLS robusto, recursos abundantes, reusa o script de relay.
  - ESP32 segue sem TLS (economia de RAM mantida).
- **Contras:**
  - **Não é portátil** — o Tamagotchi só fala dentro do raio do WiFi de
    casa onde o Pi está ligado. Levar o bichinho para a escola/rua
    entrega um pet mudo.
  - **Hardware extra 24/7** — custo, espaço, consumo, mais um ponto de
    falha.
  - **Configuração doméstica** — fora do alcance de um presente
    "abrir a caixa e brincar".
- **Rejeitada:** troca a dependência do celular pela dependência de um
  servidor caseiro; nenhum dos dois é autocontido.

### ASR/TTS inteiramente on-device (sem nuvem, sem relay)

- **Prós:**
  - Totalmente offline, zero infraestrutura, máxima privacidade.
  - Latência mínima.
- **Contras:**
  - **Qualidade de voz em pt-BR inexistente no ESP32 hoje.** Modelos
    de ASR/TTS pequenos o bastante para o chip geram voz robótica e
    reconhecimento limitado — insuficiente para a conversa natural que
    define o brinquedo (e que é o diferencial herdado do Robô Felipe).
  - **Flash/RAM apertados** para modelos de qualidade razoável.
  - **Perde a personalidade via LLM** (ADR-015, cérebro backend) — sem
    nuvem não há NLP rico, só respostas fixas.
- **Rejeitada para agora:** a qualidade de voz é o coração do projeto.
  Mantida como direção futura (ver Notas) caso modelos on-device de
  pt-BR amadureçam.

### ESP32 faz TLS direto para a nuvem (sem relay, com PSRAM)

- **Prós:**
  - **Autocontido** — um só dispositivo, exatamente a premissa do
    Tamagotchi.
  - Reusa o destino da ADR-006 (mesmos provedores de voz na nuvem) —
    só muda o caminho.
  - WiFi em qualquer lugar com rede disponível (casa, escola, casa de
    amigos) sem pareamento.
  - Criança não instala nem abre nada — liga o bichinho e conversa.
- **Contras:**
  - **TLS custa ~40–50 KB de RAM** no ESP32 → MCU **precisa de
    PSRAM**. O WROOM-32E-N4 (sem PSRAM, adotado no ADR-001) fica de
    fora desta variante. Exige ESP32-S3 com PSRAM.
  - **Sem fallback 4G/5G** — onde não há WiFi, não há nuvem (modo
    degradado offline necessário).
  - **Manutenção de CA bundle** no firmware a cada rotação de
    certificado do provedor (fricção que a ADR-002 justamente evitava).
  - **OTA muda de modelo** — não há relay para empurrar firmware (ver
    Consequences).
- **Escolhida:** é o único caminho que preserva a promessa do
  Tamagotchi (autocontido) sem sacrificar a qualidade de voz (nuvem).

## Consequences

### Positivas

- **Autocontido** — o brinquedo é um só objeto. A criança pega,
  liga e conversa. Sem app, sem pareamento, sem segundo dispositivo.
  Esta é a premissa central do Tamagotchi, finalmente satisfeita.
- **Portátil de verdade** — funciona em qualquer WiFi disponível
  (casa, escola, visita), sem dependência de celular específico.
- **Sem desenvolvimento mobile para esta variante** — o app Android
  (em desenvolvimento, ADR-002) deixa de ser entrega do Tamagotchi;
  fica associado ao quadrupede. Um subproduto inteiro de código sai
  do escopo do presente.
- **UX alinhada ao usuário final** — um pet que responde ao toque,
  não a um app aberto. A restrição de foreground do iOS deixa de ser
  problema (não há app).
- **Reuso da pipeline de voz da nuvem** — mesmos provedores de
  ASR/NLP/TTS da ADR-006, mesma personalidade via LLM (ADR-015). O
  "cérebro" do bichinho não muda; muda só o transporte.
- **KWS local preservado** (ADR-005) — o gatilho "Felipe" segue no
  dispositivo, offline-capable, decidindo quando abrir a sessão de
  voz com a nuvem.

### Negativas

- **MCU tem que ter PSRAM** — TLS direto exige a RAM extra. O
  WROOM-32E-N4 (escolhido no ADR-001, sem PSRAM) **não serve para a
  variante Tamagotchi**. Implica migrar para um ESP32-S3 com PSRAM.
  - Consequência direta sobre a pesquisa de hardware (`/search-first`,
    não registrada em ADR ainda): o **M5Stack Cardputer** (StampS3,
    8 MB flash, **sem PSRAM**) passa a ser um encaixe **pior** para a
    pipeline de voz; o **M5Stack CoreS3** (ESP32-S3, 16 MB flash,
    **8 MB PSRAM**, microfone duplo + speaker I2S + bateria) passa a
    ser o candidato preferido — apesar de trazer câmera (indesejada)
    e ser maior. Essa mudança de recomendação é um efeito imediato
    desta ADR e deve ser registrada na futura ADR de hardware do
    Tamagotchi.
- **TLS e CA bundle no firmware** — custo de RAM absorvido pela
  PSRAM, mas a fricção de manutenção de certificados volta (a cada
  rotação do provedor). Mitigado por atualização OTA do CA bundle.
- **Sem fallback móvel** — onde não há WiFi, o bichinho fica mudo na
  parte de nuvem. Exige um **modo degradado offline** (ver Notas).
- **OTA muda de modelo** — a ADR-007 (push de firmware via WebSocket
  do relay) **não se aplica** ao Tamagotchi. Sem relay, o OTA passa a
  ser **pull**: o ESP32 busca, ele mesmo via TLS, um manifest + binário
  assinado de um bucket/GitHub Releases. Isso é basicamente o modelo
  da `esp32FOTA` (rejeitada no ADR-007 por ser pull — agora, sem
  relay, pull é o caminho natural). Nova ADR necessária para OTA do
  Tamagotchi.
- **Segunda arquitetura de voz no projeto** — quadrupede (relay) e
  Tamagotchi (direto) divergem em transporte. A camada de aplicação
  (KWS, formato de áudio PCM, protocolo com a nuvem) é compartilhada;
  a camada de transporte (HTTP puro vs. TLS) é diferente. Há risco de
  duplicação se não houver abstração limpa.
- **Descoberta de rede sem app** — sem o app para mDNS/IP fixo, o
  Tamagotchi precisa obter credenciais WiFi de outra forma (captive
  portal no próprio display, ou provisionamento por Bluetooth). Mais
  um subsistema para desenvolver.

### Notas

- **Modo degradado offline** (sem WiFi): o Tamagotchi deve continuar
  minimamente vivo — KWS local responde a poucos comandos fixos
  ("olá", "tchau", piada pronta) com TTS de baixa qualidade
  embarcado, e o pet mostra animações de "dormindo / com fome" no
  display. A personalidade de bichinho (stats, humor, evolução)
  existe independente de internet; só a **conversa rica** exige a
  nuvem. Isso casaria bem com motores de Tamagotchi como
  `cifertech/TamaFi` ou `moonbench/catode32` (pesquisados via
  `/search-first`) para a camada offline.
- **Provisionamento de WiFi**: captive portal servido pelo próprio
  ESP32 em modo AP temporário, configurável pelo display + botões (o
  Cardputer tem teclado; o CoreS3 tem touch), é o caminho natural sem
  app. BLE provisioning é alternativa.
- **OTA pull**: candidata natural é `esp32FOTA` (414★, assinatura RSA,
  semver, manifest JSON) — exatamente o modelo rejeitado no ADR-007
  *para o quadrupede com relay*. No Tamagotchi, sem relay, o pull via
  TLS do próprio ESP32 é o caminho correto e merece sua própria ADR.
- **Escolha de MCU (ESP32-S3 com PSRAM) e placa pronta vs. PCB custom**
  ficam para a próxima ADR de hardware do Tamagotchi, mas esta ADR já
  estabelece a **restrição decisiva**: PSRAM é obrigatório, o que
  elimina o WROOM-32E-N4 (ADR-001) e o StampS3 do Cardputer como
  opções para a pipeline de voz direta.
- **Compatibilidade com o quadrupede**: nada aqui obriga o quadrupede
  a abandonar o relay. As duas variantes podem coexistir com camadas
  de transporte diferentes, compartilhando KWS (ADR-005), formato de
  áudio e interface com a nuvem (ADR-006). Uma abstração de
  "transporte de voz" limpa é o ponto de costura.
- **Retrocompatibilidade de ADRs**: esta ADR **supersede a ADR-002
  apenas para a variante Tamagotchi**. A ADR-002 permanece Accepted
  para o quadrupede. Não reescrevemos a ADR-002; registramos a
  exceção aqui, por escopo.
