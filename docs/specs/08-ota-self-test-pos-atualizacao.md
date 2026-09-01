# Spec 08: Self-test pós-OTA — validações que exigem hardware físico

**Ticket:** [08 — Self-test pós-OTA no CoreS3](../tickets/08-ota-self-test-pos-atualizacao.md)
**Status:** ready-for-agent (execução aguarda o hardware CoreS3 — Fase 2)
**Blocked by:** (nenhum ticket; fisicamente bloqueado pela chegada do CoreS3)

## Problem Statement

A CI do Spec 07 verifica tudo o que é verificável em software: lint,
testes, build, assinatura RSA, manifest. Mas a atualização OTA só é
segura se o firmware novo **funcionar no dispositivo**. O ADR-020
estabeleceu o mecanismo anti-brick: no primeiro boot pós-OTA o app roda
em `ESP_OTA_IMG_PENDING_VERIFY` e um self-test de ~30 s decide entre
`esp_ota_mark_app_valid_cancel_rollback()` (commit) e a reversão
automática pelo bootloader (`CONFIG_BOOTLOADER_APP_ROLLBACK_ENABLE`).
O que falta é a definição do **checklist exato** do self-test — quais
capacidades validar, em que ordem, com quais critérios de aprovação —
e do script/módulo de firmware que o executa. Essas validações exigem
hardware físico (WiFi real, I2S, display, microfone) e não podem ser
cobertas por CI.

## Solution

Um módulo de self-test no firmware do cores3-felipe, executado
automaticamente no primeiro boot após cada OTA. Ele valida em sequência
(WiFi → áudio → wake word → display), reporta progresso no display
(o Sobrinho vê que o pet está "acordando"), e só marca a imagem como
válida se **todas** as checagens passarem. Qualquer falha (ou travamento
— o watchdog/timeout cobre) deixa a imagem em pending verify e o
bootloader reverte para a versão anterior no próximo reset.

## User Stories

1. As a Sobrinho, I want o pet mostrar que está se atualizando e depois
   "acordar" sozinho, so that eu nunca precise saber que houve uma
   atualização nem usar cabo.
2. As a Sobrinho, I want que se a atualização deixar o pet doente
   (travado), ele volte sozinho para a versão que funcionava, so que o
   pet nunca fique bricado por causa de update.
3. As a desenvolvedor, I want o self-test validar a conexão WiFi em
   N segundos, so that um firmware que não reconecta à rede seja
   revertido.
4. As a desenvolvedor, I want o self-test ler amostras do microfone
   I2S e verificar níveis não-silenciosos, so que um firmware com
   pipeline de captura quebrado seja revertido.
5. As a desenvolvedor, I want o self-test tocar um tom curto no
   speaker I2S e verificar que o codec responde, so que o caminho de
   saída de áudio seja validado.
6. As a desenvolvedor, I want o self-test carregar o modelo de wake
   word quando ela estiver habilitada (no MVP push-to-talk, esta
   checagem é pulada sem falhar), so que o check sirva para antes e
   depois da wake word "Felipe" existir.
7. As a desenvolvedor, I want o self-test desenhar um frame de teste
   no display e verificar resposta do barramento, so que um firmware
   cego (display morto) seja revertido.
8. As a desenvolvedor, I want o resultado do self-test logado (UART +
   display) com motivo de falha, so que eu diagnostique reverts sem
   cabo quando possível.
9. As a desenvolvedor, I want um comando manual para disparar o
   self-test fora do fluxo OTA, so que eu valide o checklist no
   hardware antes de confiar nele numa release.

## Implementation Decisions

### Estado do app e janela de decisão

- `CONFIG_BOOTLOADER_APP_ROLLBACK_ENABLE=y` no sdkconfig da board
  cores3-felipe (já prescrito no ADR-020).
- No boot, checar `esp_ota_get_running_partition()` + estado da imagem:
  se `ESP_OTA_IMG_PENDING_VERIFY`, executar o self-test **antes** de
  iniciar a aplicação normal (WebSocket da Nuvem, loops de áudio).
- Timeout total de ~30 s (ADR-020). Cada checagem individual tem seu
  próprio timeout menor; a soma cabe na janela.
- Sucesso → `esp_ota_mark_app_valid_cancel_rollback()` e o boot segue
  para a aplicação normal. Falha → log + reboot deliberado (o bootloader
  reverte por conta própria).

### Checklist em ordem (dependência: barato/fundamental primeiro)

| # | Checagem | Critério de aprovação | Timeout |
|:--|:--|:--|:--|
| 1 | NVS legível | abre namespace do pet sem erro | 2 s |
| 2 | WiFi conecta | obtém IP no AP configurado | 10 s |
| 3 | Mic I2S lê | N amostras com RMS acima do ruído de fundo | 5 s |
| 4 | Speaker I2S toca | tom de 440 Hz escrito sem erro no codec | 3 s |
| 5 | Wake word carrega (condicional) | modelo KWS inicializa — só quando wake word habilitada | 5 s |
| 6 | Display responde | frame de teste escrito e lido de volta / ack do barramento | 3 s |

- **Wake word condicional**: o MVP roda com `CONFIG_WAKE_WORD_DISABLED`
  (push-to-talk por toque no display, ADR-021). A checagem 5 só executa
  quando a wake word estiver compilada; caso contrário é pulada sem
  falha. O checklist já nasce pronto para o futuro.
- **Mic**: RMS acima de limiar calibrado (silêncio de sala tem ruído de
  fundo; um mic morto lê zeros constantes). Não exige que o Sobrinho
  fale nada durante o update.

### Reporting

- Display: sequência visual "acordando" (mesma linguagem do boot normal,
  coerente com Tamagotchi) com passos que acendem.
- UART: cada passo loga início, resultado e motivo de falha.
- O resultado persiste em NVS (`selftest_result`) para diagnóstico
  pós-mortem após um revert (a imagem anterior pode ler o que a nova
  escreveu antes de falhar).

### Gatilho manual

O mesmo módulo expõe uma função `RunSelfTest()` que pode ser chamada via
comando de debug (ex.: linha de série da board) fora do fluxo OTA —
para validar o checklist no hardware sem precisar de uma OTA real.

### Onde mora

`main/boards/m5stack/cores3-felipe/` no fork do firmware — código da
board, não core do xiaozhi (regra "o core do xiaozhi nunca depende de
board concreto"). Hooks no `app_main`/estado de boot.

## Testing Decisions

### O que faz um bom teste

O self-test valida hardware real, então o teste primário é **no
dispositivo**: o que pode ser testado em host (lógica de sequência,
timeouts, decisão de commit/revert) roda nos unit tests do firmware;
o que exige hardware é verificado manualmente no CoreS3 com checklist
explícito.

### Módulos testados

- **Sequência/decisão** (host, `python -m unittest` no padrão dos
  `scripts/tests` do firmware, ou teste C no host se o módulo for C):
  dado passo que falha → não chama `esp_ota_mark_app_valid`; dado
  todos passos OK → chama; wake word desabilitada → passo pulado sem
  falha.
- **No dispositivo (manual, exige CoreS3)**:
  - OTA feliz: tag nova → update → self-test passa → pet volta a
    funcionar; `esptool`/log confirma imagem válida.
  - OTA doente: firmware de teste com mic deliberadamente quebrado →
    self-test falha no passo 3 → reboot → bootloader reverte → pet
    volta na versão anterior.
  - Timeout: firmware que trava no self-test → watchdog → revert.

### Prior art

- `MarkCurrentVersionValid()` da classe `Ota` nativa do xiaozhi —
  mesmo conceito de commit pós-verificação, preservado no wrapper
  esp32FOTA (ADR-020).
- `esp_ota_mark_app_valid_cancel_rollback()` — API ESP-IDF do ciclo
  rollback.

## Out of Scope

- Validação de câmera GC0308 no self-test (o MVP Tamagotchi não usa a
  câmera em fluxo crítico; pode ser adicionada como checagem 7 depois).
- Testes de áudio de ponta a ponta com a Nuvem (ASR/TTS) — o self-test
  valida o hardware local, não a Nuvem.
- Canary/rollout de release por dispositivo.
- Self-test periódico fora do contexto OTA.

## Further Notes

- **Relação com a CI (Spec 07):** a CI garante que o binário assinado
  que chega ao dispositivo builda, tem versão coerente e manifest
  válido; o self-test garante que ele **funciona no hardware**. São as
  duas metades da segurança da release OTA — sem esta, o ADR-020 fica
  sem sua metade embarcada.
- **Execução na Fase 2:** o hardware CoreS3 ainda não chegou; este
  spec está pronto para implementação assim que chegar (AGENTS.md:
  "Fase 2 inicia após o hardware chegar").
- **Referências:** ADR-020 (self-test + rollback, política), ADR-021
  (board cores3-felipe, push-to-talk MVP), ADR-019 (hardware CoreS3),
  Spec 07 (CI — a outra metade da validação de release).
