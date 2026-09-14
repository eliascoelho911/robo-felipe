# Guia de implantação do MCP endpoint (接入点)

Este tutorial tem 3 partes:
- 1. Como implantar o serviço de MCP endpoint
- 2. Na implantação completa (todos os módulos), como configurar o MCP endpoint
- 3. Na implantação de módulo único, como configurar o MCP endpoint

# 1. Como implantar o serviço de MCP endpoint

## Primeiro passo: baixe o código-fonte do projeto de MCP endpoint

Abra no navegador o [endereço do projeto mcp-endpoint-server](https://github.com/xinnan-tech/mcp-endpoint-server)

Aberta a página, localize o botão verde escrito `Code`, clique nele e você verá o botão `Download ZIP`.

Clique nele para baixar o pacote do código-fonte do projeto. Depois de baixar no seu computador, extraia-o; nesse momento a pasta pode se chamar `mcp-endpoint-server-main`,
e você precisa renomeá-la para `mcp-endpoint-server`.

## Segundo passo: inicie o programa
Este projeto é bem simples; recomenda-se executá-lo com Docker. Se você não quiser usar Docker, pode consultar [esta página](https://github.com/xinnan-tech/mcp-endpoint-server/blob/main/README_dev.md) para executar a partir do código-fonte. Segue o método de execução com Docker:

```
# Entre no diretório raiz do código-fonte do projeto
cd mcp-endpoint-server

# Limpe o cache
docker compose -f docker-compose.yml down
docker stop mcp-endpoint-server
docker rm mcp-endpoint-server
docker rmi ghcr.nju.edu.cn/xinnan-tech/mcp-endpoint-server:latest

# Inicie o contêiner Docker
docker compose -f docker-compose.yml up -d
# Veja os logs
docker logs -f mcp-endpoint-server
```

Nesse momento, os logs exibirão algo semelhante a:
```
250705 INFO-=====下面的地址分别是智控台/单模块MCP接入点地址====
250705 INFO-智控台MCP参数配置: http://172.22.0.2:8004/mcp_endpoint/health?key=abc
250705 INFO-单模块部署MCP接入点: ws://172.22.0.2:8004/mcp_endpoint/mcp/?token=def
250705 INFO-=====请根据具体部署选择使用，请勿泄露给任何人======
```

Copie os dois endereços de interface:

Como você está usando Docker, jamais use os endereços acima diretamente!

Como você está usando Docker, jamais use os endereços acima diretamente!

Como você está usando Docker, jamais use os endereços acima diretamente!

Primeiro copie os endereços para um rascunho; você precisa saber qual é o IP de rede local do seu computador — por exemplo, o IP de rede local do meu computador é `192.168.1.25`; assim,
os meus endereços de interface originais
```
智控台MCP参数配置: http://172.22.0.2:8004/mcp_endpoint/health?key=abc
单模块部署MCP接入点: ws://172.22.0.2:8004/mcp_endpoint/mcp/?token=def
```
passam a ser
```
智控台MCP参数配置: http://192.168.1.25:8004/mcp_endpoint/health?key=abc
单模块部署MCP接入点: ws://192.168.1.25:8004/mcp_endpoint/mcp/?token=def
```

Depois de ajustar, acesse diretamente pelo navegador o endereço `智控台MCP参数配置` (parâmetros MCP do Console de Gerenciamento). Quando o navegador exibir algo semelhante ao código a seguir, deu certo.
```
{"result":{"status":"success","connections":{"tool_connections":0,"robot_connections":0,"total_connections":0}},"error":null,"id":null,"jsonrpc":"2.0"}
```

Guarde bem os dois `endereços de interface` acima; serão usados no próximo passo.

# 2. Na implantação completa (todos os módulos), como configurar o MCP endpoint
Primeiro, você precisa ativar a função de MCP endpoint. No Console de Gerenciamento (智控台), clique em `Dicionário de Parâmetros` (参数字典) no topo e, no menu suspenso, abra a página `Configuração de funções do sistema`. Nela, marque `MCP接入点` (MCP endpoint) e clique em `Salvar configuração`. Na página `Configuração de Papéis` (角色配置), clique no botão `Editar funções` e você verá a função `MCP接入点` (MCP endpoint).

Se a sua implantação é completa (todos os módulos), faça login no Console de Gerenciamento com uma conta de administrador, clique em `Dicionário de Parâmetros` no topo e selecione a função `Gerenciamento de Parâmetros` (参数管理).

Em seguida, pesquise o parâmetro `server.mcp_endpoint`; nesse momento, o valor dele deve ser `null`.
Clique no botão de edição e cole o endereço `智控台MCP参数配置` obtido no passo anterior no campo `Valor do parâmetro` (参数值). Depois salve.

Se o salvamento funcionar, está tudo bem; vá ao agente conferir o resultado. Se não funcionar, o Console de Gerenciamento não conseguiu acessar o MCP endpoint — muito provavelmente é o firewall da rede, ou o IP de rede local não foi informado corretamente.

# 3. Na implantação de módulo único, como configurar o MCP endpoint

Se a sua implantação é de módulo único, localize o seu arquivo de configuração `data/.config.yaml`.
No arquivo de configuração, busque `mcp_endpoint`; se não encontrar, adicione a configuração `mcp_endpoint`. No meu caso, ficou assim:
```
server:
  websocket: ws://seu-ip-ou-dominio:porta/xiaozhi/v1/
  http_port: 8002
log:
  log_level: INFO

# Aqui pode haver ainda mais configurações..

mcp_endpoint: seu-endereco-websocket-do-endpoint
```
Agora, cole no `mcp_endpoint` o endereço `单模块部署MCP接入点` (MCP endpoint para implantação de módulo único) obtido em "Como implantar o serviço de MCP endpoint". Algo como:

```
server:
  websocket: ws://seu-ip-ou-dominio:porta/xiaozhi/v1/
  http_port: 8002
log:
  log_level: INFO

# Aqui pode haver ainda mais configurações

mcp_endpoint: ws://192.168.1.25:8004/mcp_endpoint/mcp/?token=def
```

Com a configuração pronta, ao iniciar o módulo único serão exibidos logs como os abaixo.
```
250705[__main__]-INFO-初始化组件: vad成功 SileroVAD
250705[__main__]-INFO-初始化组件: asr成功 FunASRServer
250705[__main__]-INFO-OTA接口是          http://192.168.1.25:8002/xiaozhi/ota/
250705[__main__]-INFO-视觉分析接口是     http://192.168.1.25:8002/mcp/vision/explain
250705[__main__]-INFO-mcp接入点是        ws://192.168.1.25:8004/mcp_endpoint/mcp/?token=abc
250705[__main__]-INFO-Websocket地址是    ws://192.168.1.25:8000/xiaozhi/v1/
250705[__main__]-INFO-=======上面的地址是websocket协议地址，请勿用浏览器访问=======
250705[__main__]-INFO-如想测试websocket请启动digital-human模块，打开浏览器交互测试
250705[__main__]-INFO-=============================================================
```

Como acima, se for exibido, em algo semelhante a `mcp接入点是`, o valor `ws://192.168.1.25:8004/mcp_endpoint/mcp/?token=abc`, a configuração foi bem-sucedida.
