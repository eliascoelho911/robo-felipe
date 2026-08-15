# ADR-013: Usar componente espfriends/servo_dog_ctrl para locomoção

## Status
Accepted

## Date
2026-08-09

## Context

ADR-010 decidiu 4 servos (1 por pata) com gait do ESP-HI. Pesquisa do
componente `servo_dog_ctrl` (via ESP Component Registry, publicado pela
ESP-Friends no Gitee) revelou:

- O componente é **independente do framework xiaozhi** — zero referências
  a `Application`, `McpServer`, `WifiBoard`, `Board`. Pode ser usado
  standalone.
- Suporta **todos os targets** ESP32 (`"supports all targets"` no
  registry) — roda no WROOM-32E (esp32), não só no C3.
- Pins de servo são **parâmetros em runtime** (`servo_dog_ctrl_config_t`),
  não hardcoded — GPIO 5/16/17/18 do ADR-010 sem mudança de código.
- Fornece **18 ações**: forward, backward, turn_left/right, idle, lay_down,
  bow, lean_back, bow_lean, sway_back_forth, sway, shake_hand, poke,
  shake_back_legs, jump_forward, jump_backward, retract_legs, installation.
- Gait é procedural (interpolação linear 1°/step com `vTaskDelay`), task
  FreeRTOS + queue, preemptível (novo comando interrompe gait em andamento).
- Dependências: `espressif/servo ^1.0.0` (LEDC-based), ESP-IDF ≥5.0.0.
  Sem WebUI (opcional, desabilitável via Kconfig).

## Decision

**Usar o componente `espfriends/servo_dog_ctrl ^0.2.0` do ESP Component
Registry diretamente no firmware do WROOM-32E**, sem o framework xiaozhi.

```yaml
# main/idf_component.yml
dependencies:
  espfriends/servo_dog_ctrl: ^0.2.0
  espressif/servo: ^1.0.0
```

```c
// init
servo_dog_ctrl_config_t cfg = {
    .fl_gpio_num = GPIO_NUM_5,
    .fr_gpio_num = GPIO_NUM_16,
    .bl_gpio_num = GPIO_NUM_17,
    .br_gpio_num = GPIO_NUM_18,
};
servo_dog_ctrl_init(&cfg);

// comando
servo_dog_ctrl_send(DOG_STATE_FORWARD, NULL);
servo_dog_ctrl_send(DOG_STATE_SHAKE_HAND, NULL);
```

Desabilitar WebUI (`CONFIG_ESP_HI_WEB_CONTROL_ENABLED=n`) — o controle
vem pelo WebSocket do app (ADR-002/006), não por HTTP local.

Calibrar ângulos neutros via `servo_dog_set_leg_offset()` para o chassi
adaptado do ESP-HI (não usar os defaults 70/110/110/70 que são do
mounting do ESP-HI).

## Alternatives Considered

### Vendorizar os dois arquivos core (servo_dog_ctrl.c + .h)
- Prós: controle total, ajustar gait para o chassi, sem dependência
  externa (Gitee).
- Contras: ~850 linhas para manter; atualizações upstream lost.
- **Adiada:** só vendorizar se o componente upstream parar de ser
  mantido ou se precisarmos de gait custom que o componente não suporta.

### Reimplementar do zero
- Prós: design limpo para o WROOM, sem dependências.
- Contras: ~200+ linhas de gait procedural para reescrever e debugar.
  O componente já funciona e é testado.
- **Rejeitada:** reinventar a roda.

### Portar via framework xiaozhi completo
- Prós: MCP tools prontos, AI integration nativa.
- Contras: framework é para ESP32-S3 com PSRAM, TLS direto — contraria
  ADR-002 e ADR-008.
- **Rejeitada:** incompatível com a arquitetura relay.

## Consequences

### Positivas

- 18 ações prontas (forward, backward, turn, shake_hand, bow, sway,
  jump, poke, lay_down, etc.) sem escrever gait code.
- Componente é testado pela comunidade ESP-Friends.
- Pins remappable — funciona no WROOM sem código hardcoded.
- Task FreeRTOS + queue — não bloqueia a pipeline de áudio.
- Preemptível — "parar" interrompe qualquer gait em andamento.
- De-energiza servos no repouso (sem torque de holding → economiza
  bateria).

### Negativas

- Dependência externa (Gitee/ESP Component Registry) — se o registry
  cair, build quebra. Mitigado: cache local ou vendorização.
- Ângulos neutros precisam recalibração para o chassi adaptado.
- Gait é procedural (não IK) — não é suave como caminhada real, é
  shuffling diagonal. Aceitável para 4 servos.
- O componente desenergiza servos entre ações — o cão "relaxa" entre
  comandos. Isto é uma feature (economiza bateria) mas pode parecer
  estranho se o sobrinho espera o cão sempre "em pé".

### Notas

- O componente usa `espressif/servo` (LEDC-based), não a `ESP32Servo`
  library do Arduino core. O firmware do Robô Felipe deve usar ESP-IDF
  (ou arduino-esp32 com IDF component support) para integrar.
- A `ACB_Biped_Robot` do tutorial ACEBOTT fica como referência morta —
  não é usada no cão.
- As 18 ações devem ser mapeadas para o vocabulário de voz do sobrinho:
  "Felipe, anda!" → `DOG_STATE_FORWARD`, "Dá a pata!" →
  `DOG_STATE_SHAKE_HAND`, "Deita!" → `DOG_STATE_LAY_DOWN`, etc.
