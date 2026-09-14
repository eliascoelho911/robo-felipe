# Guia de configuração da atualização OTA automática do firmware na implantação de módulo único

Este tutorial orienta como configurar a atualização OTA automática do firmware no cenário de **implantação de módulo único**, permitindo a atualização automática do firmware do dispositivo.

Se você já usa a **implantação completa (todos os módulos)**, ignore este tutorial.

## Apresentação da função

Na implantação de módulo único, o xiaozhi-server traz embutida a função de gerenciamento de firmware OTA, capaz de detectar automaticamente a versão do dispositivo e entregar o firmware de atualização. Conforme o modelo do dispositivo e a versão atual, o sistema faz a correspondência e envia automaticamente a versão mais recente do firmware.

## Pré-requisitos

- Você já realizou com sucesso a **implantação de módulo único** e mantém o xiaozhi-server em execução
- O dispositivo consegue se conectar normalmente ao servidor

## Primeiro passo: preparar os arquivos de firmware

### 1. Crie o diretório de armazenamento dos firmwares

Os arquivos de firmware devem ficar no diretório `data/bin/`. Se ele não existir, crie manualmente:

```bash
mkdir -p data/bin
```

### 2. Regra de nomenclatura dos arquivos de firmware

O nome do arquivo de firmware deve seguir o formato:

```
{modelo_do_dispositivo}_{versao}.bin
```

**Regras de nomenclatura:**
- `modelo_do_dispositivo`: o nome do modelo do dispositivo, por exemplo `lichuang-dev`, `bread-compact-wifi` etc.
- `versao`: o número de versão do firmware, que deve começar com um dígito e aceita números, letras, pontos, underscores e hífens, por exemplo `1.6.6`, `2.0.0` etc.
- A extensão do arquivo deve ser `.bin`

**Exemplos de nomenclatura:**
```
bread-compact-wifi_1.6.6.bin
lichuang-dev_2.0.0.bin
```

### 3. Colocar os arquivos de firmware

Copie os arquivos de firmware preparados (arquivos .bin) para o diretório `data/bin/`:

Coisas importantes se dizem três vezes: o binário de atualização é o `xiaozhi.bin`, e não o arquivo de firmware completo `merged-binary.bin`!

Coisas importantes se dizem três vezes: o binário de atualização é o `xiaozhi.bin`, e não o arquivo de firmware completo `merged-binary.bin`!

Coisas importantes se dizem três vezes: o binário de atualização é o `xiaozhi.bin`, e não o arquivo de firmware completo `merged-binary.bin`!

```bash
cp xiaozhi.bin data/bin/modelo_do_dispositivo_versao.bin
```

Por exemplo:
```bash
cp xiaozhi.bin data/bin/bread-compact-wifi_1.6.6.bin
```

## Segundo passo: configurar o endereço de acesso público (apenas para implantação na internet)

**Atenção: este passo se aplica apenas ao cenário de implantação de módulo único em rede pública.**

Se o seu xiaozhi-server está implantado em rede pública (com IP público ou domínio), você **precisa** configurar o parâmetro `server.vision_explain`, porque o endereço de download do firmware OTA usará o domínio e a porta dessa configuração.

Se a sua implantação é em rede local, pode pular este passo.

### Por que configurar esse parâmetro?

Na implantação de módulo único, ao gerar o endereço de download do firmware, o sistema usa o domínio e a porta configurados em `vision_explain` como endereço base. Sem configuração, ou com configuração errada, o dispositivo não conseguirá acessar o endereço de download do firmware.

### Como configurar

Abra o arquivo `data/.config.yaml`, localize o bloco de configuração `server` e defina o parâmetro `vision_explain`:

```yaml
server:
  vision_explain: http://seu-dominio-ou-ip:porta/mcp/vision/explain
```

**Exemplos de configuração:**

Implantação em rede local (padrão):
```yaml
server:
  vision_explain: http://192.168.1.100:8003/mcp/vision/explain
```

Implantação com domínio público:
```yaml
server:
  vision_explain: http://seudominio.com:8003/mcp/vision/explain
```

### Observações

- O domínio ou IP deve ser um endereço que o dispositivo consiga acessar
- Em implantação com Docker, não use endereços internos do Docker (como 127.0.0.1 ou localhost)
- Se você usa proxy reverso nginx, informe o endereço e a porta externos, e não a porta em que o projeto roda


## Perguntas frequentes

### 1. O dispositivo não recebe a atualização de firmware

**Possíveis causas e soluções:**

- Verifique se o nome do arquivo de firmware segue a regra: `{modelo_do_dispositivo}_{versao}.bin`
- Verifique se o arquivo de firmware foi colocado corretamente no diretório `data/bin/`
- Verifique se o modelo do dispositivo corresponde ao modelo no nome do arquivo de firmware
- Verifique se a versão do firmware é superior à versão atual do dispositivo
- Consulte os logs do servidor para confirmar se as requisições OTA foram processadas normalmente

### 2. O dispositivo informa que o endereço de download está inacessível

**Possíveis causas e soluções:**

- Verifique se o domínio ou IP configurado em `server.vision_explain` está correto
- Confirme que a porta está configurada corretamente (padrão 8003)
- Em implantação pública, garanta que o dispositivo consiga acessar esse endereço público
- Em implantação com Docker, garanta que não foi usado endereço interno (127.0.0.1)
- Verifique se o firewall liberou a porta correspondente
- Se você usa proxy reverso nginx, informe o endereço e a porta externos, e não a porta em que o projeto roda

### 3. Como confirmar a versão atual do dispositivo

Consulte os logs das requisições OTA; neles aparece a versão reportada pelo dispositivo:

```
[ota_handler] - 设备 AA:BB:CC:DD:EE:FF 固件已是最新: 1.6.6
```

### 4. O arquivo de firmware foi colocado, mas não surtiu efeito

O sistema tem um cache de 30 segundos (padrão). Você pode:
- Aguardar 30 segundos antes de o dispositivo fazer nova requisição OTA
- Reiniciar o serviço xiaozhi-server
- Ajustar a configuração `firmware_cache_ttl` para um tempo menor
