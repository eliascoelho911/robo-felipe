# ADR-018: Comportamento do Tamagotchi mora no xiaozhi-server (Nuvem)

## Status

Accepted

## Date

2026-09-19 (reescrita; a versão anterior decidia um Core TypeScript auto-hospedado — histórico completo no git)

## Context

O Tamagotchi precisa de um lugar onde viva o **comportamento** do pet:
estado (stats, estágio, sickness), decay por tempo, moods, reação a
Triggers não-vozeados (sacudida, botão, proximidade, RTC wake) e as
ferramentas que o LLM aciona por voz. A versão anterior desta ADR decidiu
por um **Core em TypeScript auto-hospedado**, separado da Nuvem, falando
com a Plataforma por um contrato versionado (Batch → Plano de Ações),
com o app Android como Plataforma de laboratório.

Esse desenho acumulou três custos que pesaram mais do que a separação
comprava:

1. **Um serviço a mais para hospedar e operar.** A Nuvem
   (xiaozhi-esp32-server, ADR-022) já é o serviço sempre-ligado do
   projeto; o Core exigia um segundo processo (Node) no ar para o pet
   funcionar.
2. **Um hop HTTP extra e um contrato versionado como peso permanente.**
   Dedupe por `sensationId`, versionamento do envelope, JSON Schema para
   gerar tipos em cada linguagem consumidora — tudo para transportar o
   que, no fim, era a decisão do pet. E o adapter Python no
   xiaozhi-server (ADR-023 emenda) existia só para fazer a ponte entre o
   LLM e esse serviço — glue permanente entre dois processos nossos.
3. **O pressuposto da troca de host caiu.** O contrato existia para
   sobreviver à troca Android → CoreS3. Com o Android removido do
   projeto, a única encarnação é o CoreS3 (ADR-019); o que cruza a rede
   é o próprio protocolo WebSocket do xiaozhi — não há duas Plataformas
   a manter sincronizadas por um schema.

A Nuvem já concentra exatamente os dois pontos de acoplamento que o
comportamento precisa: o **`conn`** (a conexão WebSocket com o device,
necessária para enviar ações não-verbais — ver emenda da ADR-023) e o
**function calling do LLM** (que reconhece as intents de ação na fala do
Sobrinho). Hospedar o comportamento ali elimina a ponte em vez de
reforçá-la.

## Decision

**A lógica de comportamento do Tamagotchi mora no xiaozhi-esp32-server
(fork nosso), como código Python nativo — a Nuvem é ao mesmo tempo o
pipeline de voz e o host do comportamento.** Especificamente:

1. **Estado e regras do pet são server-side** (18 stats com health
   derivado, decay em tiers, estágios Filhote→Jovem→Adulto, moods —
   modelo na ADR-023). O estado canônico persiste na Nuvem
   (cloud-primary, sem fallback no device no MVP).
2. **Tools do pet são funções nativas do xiaozhi-server**
   (`plugins_func`, `ToolType.SYSTEM_CTL`), registradas no function
   calling. Quando o Sobrinho fala uma intent de ação, o LLM chama a
   tool; a tool muta o estado localmente (sem HTTP para fora) e envia
   ações ao device via `conn` (mensagens `pet_action` JSON sobre o WSS
   do xiaozhi).
3. **Triggers não-vozeados** (sacudida, botão, proximidade, RTC wake)
   chegam ao server como mensagens no protocolo WebSocket do xiaozhi,
   tratadas por um handler interno do fork: aplicam decay on-demand,
   mapeiam Trigger → Ação (mesma regra de antes: sacudida com coragem
   baixa → assustado; senão → tonto) e devolvem `pet_action` ao device.
4. **O device (firmware do CoreS3, ADR-021) é a Plataforma**: detecta
   Triggers, transporta voz e executa as ações recebidas (TTS do texto,
   animações LVGL). Não decide comportamento e não detém estado.
5. **Não existe mais um contrato Batch → Plano de Ações versionado.** A
   forma das mensagens device ↔ server é o protocolo do xiaozhi
   estendido com mensagens `pet_action`/trigger, definida no fork (e
   espelhada no firmware quando necessário). Sem JSON Schema gerado, sem
   dedupe de envelope, sem bump de versão de contrato.
6. **Supersedes** a versão anterior desta ADR (Core TypeScript +
   contrato + adapter Python HTTP). Comportamento iterável **sem
   reflash** permanece: tunar o pet é editar o fork da Nuvem e
   reiniciar o processo.

Esta ADR **mantém o ADR-016** (autocontido, sem relay): o device
termina TLS direto com a Nuvem, que agora também decide o
comportamento — a Nuvem é nossa, não de terceiros.

## Alternatives Considered

### Manter o Core TypeScript separado

- **Prós:** comportamento desacoplado da Nuvem (a troca de provedores
  de ASR/LLM não toca o pet); TypeScript com tipagem forte para o
  modelo de estado; o contrato sobreviveria a outra troca de host.
- **Contras:** segundo serviço always-on para operar; hop HTTP e
  contrato versionado como peso permanente; adapter Python de ponte
  eterna entre LLM e Core; com o Android fora, o contrato perdeu o
  consumidor que o justificava.
- **Rejeitada.** A separação comprava portabilidade que o projeto não
  usa mais, ao custo de operar um serviço inteiro.

### Manter o app Android de laboratório como Plataforma

- **Prós:** testar comportamento com sensores nativos do celular antes
  do hardware chegar.
- **Contras:** sem o Core TS, o Android não teria função — o
  comportamento passa pelo server, que o device CoreS3 consome
  diretamente; manter um segundo código de Plataforma (Kotlin +
  executor de ações) duplicava esforço.
- **Rejeitada.** O Android foi removido do projeto; a validação de
  comportamento passa a ser no device real (CoreS3) ou num cliente de
  teste do WSS.

### Comportamento on-device no firmware (C++)

- **Prós:** funciona offline; sem latência de rede; estado em NVS.
- **Contras:** iterar comportamento exige reflash; acopla o pet ao
  upstream `xiaozhi-esp32` (C++, cadência de commits diária); o LLM
  perderia o acesso direto ao estado para moldar respostas.
- **Rejeitada**, como na versão anterior desta ADR. O fallback offline
  (NVS) segue tópico próprio da ADR-023, não decisão desta.

## Consequences

### Positivas

- **Um serviço a menos** — a Nuvem já é always-on; não há segundo
  processo (Node) cuja queda derruba o pet (ADR-023 listava isso como
  negativa).
- **Sem hop HTTP na decisão** — tools do LLM e handlers de Trigger
  mutam o estado no mesmo processo que fala com o device.
- **Sem contrato versionado** — nada de dedupe/versionamento de
  envelope nem JSON Schema por linguagem; a forma das mensagens vive em
  um único lugar (o fork).
- **Iteração sem reflash preservada** — decay rates, thresholds de
  sickness e estágios são código Python no fork; tunar o pet não toca
  firmware.
- **LLM informado com menos indireção** — o contexto dinâmico do prompt
  e as tools leem o estado localmente, sem buscar pela rede.

### Negativas

- **Comportamento acoplado ao fork da Nuvem** — merges do upstream
  `xiaozhi-esp32-server` (Python, movimentado) agora passam por cima do
  código do pet. Mitigação: comportamento isolado em módulo próprio
  dentro do fork, sem espalhar lógica pelo core upstream.
- **Port TS → Python é reescrita** — o modelo de stats/decay/estágios
  escrito em TypeScript não é reusado; o design (ADR-023) permanece, o
  código não.
- **Menos separação de responsabilidades** — a Nuvem deixa de ser
  provedor de capacidades puro e passa a decidir o comportamento. A
  troca de provedores ASR/LLM/TTS continua config (ADR-022); a troca de
  comportamento continua código nosso — a fronteira que apertou é a
  entre voz e pet, dentro do mesmo serviço.
- **Validação de comportamento exige o device real** — sem o app de
  laboratório, exercitar o pet de ponta a ponta pede o CoreS3 ou um
  cliente de teste do WSS.

## Notas

- **Termos do glossário atualizados** em `CONTEXT.md`: `Core`, `Batch`,
  `Ação` e `Plano de Ações` saem; `Plataforma` passa a ser o firmware
  do CoreS3; `Nuvem` passa a incluir o comportamento do pet.
- **Integração com a ADR-022**: a Nuvem é descrita lá como provedores
  pt-BR + comportamento do pet; as portas/ativação de device não
  mudam. A emenda da ADR-023 (HTTP + adapter) foi substituída por este
  desenho — ver a emenda atualizada lá.
- **Referências**: ADR-016 (sem relay), ADR-019 (hardware CoreS3),
  ADR-021 (firmware), ADR-022 (Nuvem), ADR-023 (modelo do pet vivo).
