# Tutorial de Implantação do Gateway MQTT

O projeto `xiaozhi-esp32-server`, combinado com o projeto [xiaozhi-mqtt-gateway](https://github.com/78/xiaozhi-mqtt-gateway) open source do Xia Ge (虾哥), com uma pequena adaptação, permite conectar o hardware XiaoZhi via MQTT+UDP.
Este tutorial está dividido em três partes; conforme a sua implantação seja de todos os módulos ou de módulo único, escolha a parte correspondente para conectar o gateway MQTT:
- Parte 1: implantar o gateway MQTT
- Parte 2: executar todos os módulos para conectar o hardware XiaoZhi via MQTT+UDP
- Parte 3: executar o xiaozhi-server em módulo único para conectar o hardware XiaoZhi via MQTT+UDP

## Preparação
Tenha em mãos o endereço de conexão `mqtt-websocket` do seu `xiaozhi-server`. A partir do seu `endereço websocket` original, acrescente o sufixo `?from=mqtt_gateway` para obter o endereço de conexão `mqtt-websocket`

1. Se você fez a implantação a partir do código-fonte, o seu endereço `mqtt-websocket` é:
```
ws://127.0.0.1:8000/xiaozhi/v1/?from=mqtt_gateway
```

2. Se você fez a implantação com docker, o seu endereço `mqtt-websocket` é
```
ws://SEU_IP_DE_REDE_DO_HOST:8000/xiaozhi/v1/?from=mqtt_gateway
```

## Aviso importante

Se você está implantando em servidor, é preciso garantir que as portas `1883`, `8884` e `8007` do servidor estejam todas abertas ao público externo. Para a `8884`, o tipo de protocolo escolhido é `UDP`; para as demais, `TCP`.

Se você está implantando em servidor, é preciso garantir que as portas `1883`, `8884` e `8007` do servidor estejam todas abertas ao público externo. Para a `8884`, o tipo de protocolo escolhido é `UDP`; para as demais, `TCP`.

Se você está implantando em servidor, é preciso garantir que as portas `1883`, `8884` e `8007` do servidor estejam todas abertas ao público externo. Para a `8884`, o tipo de protocolo escolhido é `UDP`; para as demais, `TCP`.


## Parte 1: Implantar o gateway MQTT

1. Clone o [projeto xiaozhi-mqtt-gateway já adaptado](https://github.com/xinnan-tech/xiaozhi-mqtt-gateway.git):
```bash
git clone https://ghfast.top/https://github.com/xinnan-tech/xiaozhi-mqtt-gateway.git
cd xiaozhi-mqtt-gateway
```

2. Instale as dependências:
```bash
npm install
npm install -g pm2
```

3. Configure o `config.json`:
```bash
cp config/mqtt.json.example config/mqtt.json
```

4. Edite o arquivo de configuração config/mqtt.json e substitua, dentro de `chat_servers`, o endereço `mqtt-websocket` obtido na `seção de preparação deste tutorial`. Por exemplo, para um `xiaozhi-server` implantado a partir do código-fonte, a configuração fica assim:

```
{
    "production": {
        "chat_servers": [
            "ws://127.0.0.1:8000/xiaozhi/v1/?from=mqtt_gateway"
        ]
    },
    "debug": false,
    "max_mqtt_payload_size": 8192,
    "mcp_client": {
        "capabilities": {
        },
        "client_info": {
            "name": "xiaozhi-mqtt-client",
            "version": "1.0.0"
        },
        "max_tools_count": 128
    }
}
```
5. Crie um arquivo `.env` na raiz do projeto e defina as seguintes variáveis de ambiente:
```
PUBLIC_IP=your-ip         # IP público do servidor
MQTT_PORT=1883            # Porta do servidor MQTT
UDP_PORT=8884             # Porta do servidor UDP
API_PORT=8007             # Porta da API de gerenciamento
MQTT_SIGNATURE_KEY=test   # Chave de assinatura MQTT
SERVER_SECRET=Te1st12134  # Chave do servidor; mantenha igual à do Console de Gerenciamento (server.secret) ou igual ao server.auth_key do xiaozhi-server
```
Atenção à configuração `PUBLIC_IP`: garanta que ela corresponda ao IP público real; se você tiver um domínio, preencha com o domínio.

A `MQTT_SIGNATURE_KEY` é a chave usada para autenticar a conexão MQTT; o ideal é defini-la bem complexa, preferencialmente com mais de 8 caracteres e contendo letras maiúsculas e minúsculas. Essa chave será usada novamente mais adiante.

- Não use senhas simples, como `123456`, `test` etc.
- Não use senhas simples, como `123456`, `test` etc.
- Não use senhas simples, como `123456`, `test` etc.

A `SERVER_SECRET` é usada para gerar as informações de autenticação da conexão websocket.

1. Se você é da implantação de todos os módulos e definiu `server.auth.enabled` como `true` no Gerenciamento de Parâmetros (参数管理) do Console de Gerenciamento (智控台), então a `SERVER_SECRET` precisa ser igual à do Console de Gerenciamento (`server.secret`).

2. Se você é da implantação de módulo único e definiu `server.auth.enabled` como `true` no arquivo de configuração, então a `SERVER_SECRET` precisa ser igual à do arquivo de configuração (`server.auth_key`).


6. Inicie o gateway MQTT
```
# Iniciar o serviço
pm2 start ecosystem.config.js

# Ver os logs
pm2 logs xz-mqtt
```

Quando você visualizar o log abaixo, significa que o gateway MQTT iniciou com sucesso:
```
0|xz-mqtt  | 2025-09-11T12:14:48: MQTT 服务器正在监听端口 1883
0|xz-mqtt  | 2025-09-11T12:14:48: UDP 服务器正在监听 x.x.x.x:8884
```

Se precisar reiniciar o gateway MQTT, execute o comando abaixo:
```
pm2 restart xz-mqtt
```

## Parte 2: Executar todos os módulos para conectar o hardware XiaoZhi via MQTT+UDP

Verifique o número da versão no rodapé da página inicial do seu Console de Gerenciamento (智控台) e confirme se a versão é a `0.7.7` ou superior. Se não for, é preciso atualizar o Console de Gerenciamento.

1. No topo do Console de Gerenciamento, clique em `Gerenciamento de Parâmetros`, pesquise `server.mqtt_gateway`, clique em editar e preencha com o `PUBLIC_IP`+`:`+`MQTT_PORT` definidos no seu arquivo `.env`. Algo como
```
192.168.0.7:1883
```
2. No topo do Console de Gerenciamento, clique em `Gerenciamento de Parâmetros`, pesquise `server.mqtt_signature_key`, clique em editar e preencha com a `MQTT_SIGNATURE_KEY` definida no seu arquivo `.env`.

3. No topo do Console de Gerenciamento, clique em `Gerenciamento de Parâmetros`, pesquise `server.udp_gateway`, clique em editar e preencha com o `PUBLIC_IP`+`:`+`UDP_PORT` definidos no seu arquivo `.env`. Algo como
```
192.168.0.7:8884
```
4. No topo do Console de Gerenciamento, clique em `Gerenciamento de Parâmetros`, pesquise `server.mqtt_manager_api`, clique em editar e preencha com o `PUBLIC_IP`+`:`+`API_PORT` definidos no seu arquivo `.env`. Algo como
```
192.168.0.7:8007
```

Concluída a configuração acima, você pode usar o comando curl para verificar se o seu endereço OTA entrega a configuração mqtt; substitua o `http://localhost:8002/xiaozhi/ota/` abaixo pelo seu endereço OTA
```
curl 'http://localhost:8002/xiaozhi/ota/' \
  -H 'Content-Type: application/json' \
  -H 'Client-Id: 7b94d69a-9808-4c59-9c9b-704333b38aff' \
  -H 'Device-Id: 11:22:33:44:55:66' \
  --data-raw $'{\n  "application": {\n    "version": "1.0.1",\n    "elf_sha256": "1"\n  },\n  "board": {\n    "mac": "11:22:33:44:55:66"\n  }\n}'
```

Se o conteúdo retornado incluir configurações relacionadas a `mqtt`, a configuração foi bem-sucedida. Algo assim

```
{"server_time":{"timestamp":1757567894012,"timeZone":"Asia/Shanghai","timezone_offset":480},"activation":{"code":"460609","message":"http://xiaozhi.server.com\n460609","challenge":"11:22:33:44:55:66"},"firmware":{"version":"1.0.1","url":"http://xiaozhi.server.com:8002/xiaozhi/otaMag/download/NOT_ACTIVATED_FIRMWARE_THIS_IS_A_INVALID_URL"},"websocket":{"url":"ws://192.168.4.23:8000/xiaozhi/v1/"},"mqtt":{"endpoint":"192.168.0.7:1883","client_id":"GID_default@@@11_22_33_44_55_66@@@7b94d69a-9808-4c59-9c9b-704333b38aff","username":"eyJpcCI6IjA6MDowOjA6MDowOjA6MSJ9","password":"Y8XP9xcUhVIN9OmbCHT9ETBiYNE3l3Z07Wk46wV9PE8=","publish_topic":"device-server","subscribe_topic":"devices/p2p/11_22_33_44_55_66"}}
```

Como as informações de MQTT são entregues por meio do endereço OTA, basta garantir que você consiga conectar normalmente ao endereço OTA do servidor e, então, reiniciar e despertar o dispositivo.

Depois de despertar, fique de olho nos logs do mqtt-gateway para confirmar se há logs de conexão bem-sucedida.
```
pm2 logs xz-mqtt
```

## Parte 3: Executar o xiaozhi-server em módulo único para conectar o hardware XiaoZhi via MQTT+UDP

Abra o seu arquivo `data/.config.yaml`; em `server`, localize `mqtt_gateway` e preencha com o `PUBLIC_IP`+`:`+`MQTT_PORT` definidos no seu arquivo `.env`. Algo como
```
192.168.0.7:1883
```
Em `server`, localize `mqtt_signature_key` e preencha com a `MQTT_SIGNATURE_KEY` definida no seu arquivo `.env`.

Em `server`, localize `udp_gateway` e preencha com o `PUBLIC_IP`+`:`+`UDP_PORT` definidos no seu arquivo `.env`. Algo como
```
192.168.0.7:8884
```

Concluída a configuração acima, você pode usar o comando curl para verificar se o seu endereço OTA entrega a configuração mqtt; substitua o `http://localhost:8002/xiaozhi/ota/` abaixo pelo seu endereço OTA
```
curl 'http://localhost:8002/xiaozhi/ota/' \
  -H 'Device-Id: 11:22:33:44:55:66' \
  --data-raw $'{\n  "application": {\n    "version": "1.0.1",\n    "elf_sha256": "1"\n  },\n  "board": {\n    "mac": "11:22:33:44:55:66"\n  }\n}'
```

Se o conteúdo retornado incluir configurações relacionadas a `mqtt`, a configuração foi bem-sucedida. Algo assim
```
{"server_time":{"timestamp":1758781561083,"timeZone":"GMT+08:00","timezone_offset":480},"activation":{"code":"527111","message":"http://xiaozhi.server.com\n527111","challenge":"11:22:33:44:55:66"},"firmware":{"version":"1.0.1","url":"http://xiaozhi.server.com:8002/xiaozhi/otaMag/download/NOT_ACTIVATED_FIRMWARE_THIS_IS_A_INVALID_URL"},"websocket":{"url":"ws://192.168.1.15:8000/xiaozhi/v1/"},"mqtt":{"endpoint":"192.168.1.15:1883","client_id":"GID_default@@@11_22_33_44_55_66@@@11_22_33_44_55_66","username":"eyJpcCI6IjE5Mi4xNjguMS4xNSJ9","password":"fjAYs49zTJecWqJ3jBt+kqxVn/x7vkXRAc85ak/va7Y=","publish_topic":"device-server","subscribe_topic":"devices/p2p/11_22_33_44_55_66"}}
```

Como as informações de MQTT são entregues por meio do endereço OTA, basta garantir que você consiga conectar normalmente ao endereço OTA do servidor e, então, reiniciar e despertar o dispositivo.

Depois de despertar, fique de olho nos logs do mqtt-gateway para confirmar se há logs de conexão bem-sucedida.
```
pm2 logs xz-mqtt
```
