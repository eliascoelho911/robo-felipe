# 08: Self-test pós-OTA no CoreS3

**What to build:** Módulo de self-test no firmware da board
`m5stack/cores3-felipe` que roda automaticamente no primeiro boot após
cada OTA, enquanto a imagem está em `ESP_OTA_IMG_PENDING_VERIFY`
(`CONFIG_BOOTLOADER_APP_ROLLBACK_ENABLE=y`). O self-test valida em
sequência — NVS legível, WiFi conecta (IP em ≤10 s), mic I2S lê amostras
com RMS acima do ruído de fundo, speaker I2S toca um tom de teste,
wake word carrega (condicional: pulada enquanto
`CONFIG_WAKE_WORD_DISABLED`), display escreve/lê um frame de teste — com
timeout total de ~30 s. Tudo passando → `esp_ota_mark_app_valid_
cancel_rollback()` e o boot segue normal. Qualquer falha ou travamento
→ log do motivo (UART + NVS `selftest_result`) e reboot, com o
bootloader revertendo para a imagem anterior.

O progresso aparece no display (sequência visual "acordando") e um
comando de debug expõe `RunSelfTest()` para disparar o mesmo checklist
fora do fluxo OTA. O módulo mora em
`firmware/main/boards/m5stack/cores3-felipe/` (código da board, não core
do xiaozhi), com hooks no boot.

**Blocked by:** (nenhum ticket — fisicamente bloqueado pela chegada do
hardware CoreS3; executar na Fase 2)

**Status:** ready-for-agent

- [ ] `CONFIG_BOOTLOADER_APP_ROLLBACK_ENABLE=y` no sdkconfig da board cores3-felipe.
- [ ] Detecção de `ESP_OTA_IMG_PENDING_VERIFY` no boot, executando o self-test antes da aplicação normal.
- [ ] Checagem 1: NVS abre namespace do pet sem erro (timeout 2 s).
- [ ] Checagem 2: WiFi obtém IP (timeout 10 s).
- [ ] Checagem 3: mic I2S lê amostras com RMS acima de limiar calibrado (timeout 5 s).
- [ ] Checagem 4: speaker I2S toca tom de 440 Hz sem erro no codec (timeout 3 s).
- [ ] Checagem 5: modelo de wake word inicializa — condicional, pulada sem falhar enquanto `CONFIG_WAKE_WORD_DISABLED` (timeout 5 s quando ativa).
- [ ] Checagem 6: display escreve e confirma frame de teste (timeout 3 s).
- [ ] Sucesso chama `esp_ota_mark_app_valid_cancel_rollback()`; falha loga motivo e reboota (bootloader reverte).
- [ ] Resultado persistido em NVS (`selftest_result`) legível após revert.
- [ ] Sequência visual "acordando" no display durante o self-test.
- [ ] Comando de debug `RunSelfTest()` para execução manual fora do fluxo OTA.
- [ ] Testes de sequência/decisão em host (falha em passo → sem commit; todos OK → commit; wake word off → pulada).
- [ ] Módulo em `firmware/main/boards/m5stack/cores3-felipe/`, sem tocar o core do xiaozhi.
- [ ] Demoable (exige CoreS3 + release OTA): tag nova → update → self-test passa → imagem commitada e pet funcional.
- [ ] Demoable (exige CoreS3): firmware de teste com mic quebrado → self-test falha → reboot → bootloader reverte → pet volta na versão anterior.
