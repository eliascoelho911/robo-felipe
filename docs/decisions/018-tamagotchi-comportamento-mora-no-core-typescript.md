# ADR-018: Comportamento do Tamagotchi mora no Core em TypeScript auto-hospedado

## Status
Accepted

## Date
2026-08-27

## Context

O CoreS3 (placa-alvo do Tamagotchi) ainda não está em mãos, mas o projeto
não pode parar. O usuário quer uma arquitetura que permita **testar
comportamentos do pet no Android agora** (usando sensores nativos do
celular) e **trocar de host depois** quando o CoreS3 chegar, sem
reescrever a lógica.

O rascunho do usuário propõe uma divisão:

- A **Plataforma** (Android hoje / CoreS3 depois) detecta **Triggers**
  e os envia em **Batch** a um core.
- O core processa o Batch e retorna um **Plano de Ações** (uma ou mais
  ações: falar, ficar tonto, dançar, expressar emoção...).
- A Plataforma executa as Ações.

A questão central decidida nesta ADR é **onde mora esse core**. O
rascunho o chamava de "core embarcado". A investigação de viabilidade
desta sessão (subagente `explore`, registrada no histórico) concluiu:

- **Core no dispositivo (CoreS3) não é atraente.** O rascunho original
  falava em "core embarcado". Embutir a lógica no firmware `xiaozhi-esp32`
  (C++) acopla o comportamento a um upstream de commits diários e exige
  reflash para iterar; fazer MicroPython como firmware único (rotas
  exploradas na investigação: abandonar o `xiaozhi-esp32` e perder o
  pipeline de voz/TLS/display pronto, ou embutir a VM num fork — sem
  precedente público mantido) esbarra no ADR-001; um segundo MCU já foi
  descartado. Nenhuma rota on-device serve ao objetivo de iterar agora,
  sem placa em mãos.
- **Core embarcado no app Android também não porta.** Chaquopy (Python
  in-process no app) é Android-only e não migra para o CoreS3;
  TypeScript não roda nativamente no Android tampouco. Qualquer lógica
  embarcada no celular teria de ser reescrita na troca de host.
- **Um serviço na rede é o lar natural.** Se o core for um serviço nosso
  acessado por HTTPS, ele é idêntico para o Android de hoje e o CoreS3 de
  amanhã; só a Plataforma troca. A linguagem desse serviço é uma escolha
  livre — não fica presa ao firmware (C++) nem ao `xiaozhi-esp32-server`
  (Python), porque a comunicação é pelo contrato. O usuário escolhe
  **TypeScript**.

O insight decisivo: o que precisa sobreviver à troca de host não é o
código do Core — é o **contrato** (Batch → Plano de Ações).

## Decision

**A lógica de comportamento do Tamagotchi mora num Core em TypeScript
auto-hospedado**, acessado pela Plataforma via HTTPS. Especificamente:

1. O **Core** é um subsistema nosso (TypeScript, auto-hospedado) que
   recebe Batches de Triggers, decide o comportamento e responde com
   Planos de Ações. Pode chamar a **Nuvem** (ASR/LLM/TTS — provedores
   externos).
2. A **Plataforma** (app Android de laboratório hoje; CoreS3 depois) é
   responsável apenas por: detectar Triggers, enviar Batches ao Core e
   executar os Planos de Ações recebidos. Não decide comportamento.
3. O **contrato Batch → Plano de Ações** (JSON versionado) é o artefato
   canônico que sobrevive à troca de host. O que migra do Android para o
   CoreS3 é a Plataforma (Kotlin → C++), não o Core.
4. **Supersede parcial** do research `tamagotchi-pet-engine-ui.md`:
   aquele research recomendava um engine de pet on-device em C++ (TamaFi
   + design do catode32). Com esta ADR, o estado/decay/comportamento
   passa a ser responsabilidade do Core (cloud-side). O fallback offline
   (estado em NVS, `advanceStats` no wake do RTC) fica como **tópico
   aberto** para a decisão de "PET vivo" — esta ADR não o descarta, só
   não o define agora.

Esta ADR **não reescreve o ADR-016** (autocontido, sem relay de
smartphone): a Plataforma continua sem relay — o Core é um serviço nosso
na rede, não um smartphone do usuário. A Plataforma termina TLS direto
com o Core (e o Core com a Nuvem), coerente com o ADR-016.

## Alternatives Considered

### Core embarcado em C++ no CoreS3 (on-device)

- **Prós:** funciona offline; sem latência de rede; estado em NVS
  direto; alinhado à recomendação do research `tamagotchi-pet-engine-ui`.
- **Contras:** **não dá para testar agora** (sem CoreS3 em mãos); iterar
  comportamento exige reflash de firmware; lógica fica acoplada ao
  firmware `xiaozhi-esp32` (C++, cadência de commits diária do upstream).
- **Rejeitada para o protótipo.** O estado local permanece viável como
  fallback offline futura (conversa do PET vivo) — não é uma rejeição
  permanente, é uma decisão de sequenciamento.

### MicroPython no CoreS3 como firmware único

- **Prós:** Python no dispositivo; precedentes como o `moonbench/catode32`
  (MicroPython integral em C6/C3).
- **Contras:** o **ADR-001** rejeitou MicroPython para o firmware
  (interpretador residente, GC, user C modules que exigem recompilar);
  abandonaria o firmware `xiaozhi-esp32` e seu pipeline de voz/TLS/
  display/câmera prontos; sem precedente público de casar MicroPython
  com firmware C++ conversacional existente — seria pioneiro.
- **Rejeitada.** Exigiria ADR nova revogando parcialmente o ADR-001.

### Core Python embarcado no app Android via Chaquopy

- **Prós:** sem servidor; Python no protótipo.
- **Contras:** **não porta para o CoreS3** (Chaquopy é Android-only) —
  viola o objetivo de trocar de host sem reescrever; licença comercial
  (grátis só para apps open-source); acopla a lógica de comportamento ao
  app Android.
- **Rejeitada.** Um Core em servidor é portátil entre hosts; Chaquopy não.

## Consequences

### Positivas

- **Iteração de comportamento sem reflash** — mudar o comportamento do
  pet é editar o Core (TypeScript), não regravar firmware.
- **Mesmo Core em ambos os hosts** — o Android de hoje e o CoreS3 de
  amanhã consomem o mesmo endpoint; só a Plataforma troca.
- **Linguagem desacoplada do dispositivo e da Nuvem** — o Core é
  independente do firmware (C++) e do `xiaozhi-esp32-server` (Python);
  comunica-se com ambos por HTTP/contrato. TypeScript traz tipagem
  estática e tooling maduro para evoluir o contrato.
- **Contrato como fonte única de verdade** — o JSON Schema versionado
  gera os tipos Kotlin (app) e as interfaces TypeScript (Core); o schema
  é a fonte, as implementações espelham.

### Negativas

- **Dependência de conectividade para comportamento "esperto"** — sem
  rede, o pet não decide (fica "burro"). O fallback offline (estado
  mínimo em NVS) é a próxima conversa (PET vivo).
- **Latência de round-trip na decisão** — aceitável para o uso (a voz
  já vai à Nuvem), mas acrescenta um salto Plataforma→Core→Nuvem.
- **Hosting do Core vira responsabilidade** — PC do autor no protótipo
  (Node/Bun); VPS/Docker depois. Fora do escopo desta ADR.
- **Disciplina de contrato** — o Batch versionado exige versionamento,
  dedupe (`seq`) e tratamento de versão desconhecida na Plataforma.

## Notas

- **Hot key de voz no protótipo Android = push-to-talk** (segurar botão).
  A wake word "Felipe" em pt-BR continua sendo um gap do CoreS3 (research
  `tamagotchi-firmware-voz.md`, gap #1) e não entra no protótipo.
- **TTS da Ação `falar` é renderizado pela Plataforma** — o Core retorna
  **texto**, não áudio. No Android de hoje, TTS nativo pt-BR; no CoreS3
  depois, via protocolo `xiaozhi`. O contrato fica alto-nível
  (`falar{texto}`) para cada Plataforma escolher a renderização.
- **Evolução natural:** o Core expõe ferramentas via HTTP REST; um
  adapter Python interno no `xiaozhi-esp32-server` bridgeia as tools ao
  LLM e envia ações ao device (ver ADR-022 emenda e ADR-023 §7 emenda).
  O caminho MCP foi considerado e descartado — MCP servers externos não
  têm acesso ao `conn` do device (ver ADR-022 emenda).
- **Termos de glossário adicionados** em `CONTEXT.md`: Plataforma,
  Trigger, Batch, Core, Ação, Plano de Ações (com _Avoid_ incluindo
  "core embarcado" — o termo do rascunho original agora é evitado).
- **Fora do escopo desta ADR** (próxima conversa): gerenciamento de
  estado e comportamento (stats, decay, estágios, persistência cloud vs
  NVS) e a visão do "PET vivo".
