# Como obter informações do dispositivo com um método MCP

Este tutorial vai orientar você sobre como usar um método MCP para obter informações do dispositivo.

Passo 1: personalize o seu arquivo `agent-base-prompt.txt`

Copie o conteúdo do arquivo `agent-base-prompt.txt` do diretório do xiaozhi-server para o seu diretório `data` e renomeie-o para `.agent-base-prompt.txt`.

Passo 2: edite o arquivo `data/.agent-base-prompt.txt`, localize a tag `<context>` e adicione o seguinte conteúdo dentro da tag:
```
- **设备ID：** {{device_id}}
```

Após adicionar, o conteúdo da tag `<context>` do seu arquivo `data/.agent-base-prompt.txt` ficará aproximadamente assim:
```
<context>
【重要！以下信息已实时提供，无需调用工具查询，请直接使用：】
- **设备ID：** {{device_id}}
- **当前时间：** {{current_time}}
- **今天日期：** {{today_date}} ({{today_weekday}})
- **今天农历：** {{lunar_date}}
- **用户所在城市：** {{local_address}}
- **当地未来7天天气：** {{weather_info}}
</context>
```

Passo 3: edite o arquivo `data/.config.yaml`, localize a configuração `agent-base-prompt`; antes da alteração, o conteúdo é o seguinte:
```
prompt_template: agent-base-prompt.txt
```
Altere para
```
prompt_template: data/.agent-base-prompt.txt
```

Passo 4: reinicie o serviço do xiaozhi-server.

Passo 5: no seu método MCP, adicione um parâmetro com nome `device_id`, tipo `string` e descrição `设备ID` (ID do dispositivo).

Passo 6: acorde o Xiaozhi novamente, peça para ele chamar o método MCP e verifique se o seu método MCP consegue obter o `ID do dispositivo` (设备ID).
