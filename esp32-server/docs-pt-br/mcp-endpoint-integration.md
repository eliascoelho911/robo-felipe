# Guia de uso do endpoint MCP (MCP 接入点)

Este tutorial usa como exemplo a função de calculadora MCP open source do Xiaoge (虾哥) para mostrar como conectar o seu próprio serviço MCP personalizado ao seu endpoint MCP.

A premissa deste tutorial é que o seu `xiaozhi-server` já tenha a função de endpoint MCP habilitada; se ainda não habilitou, primeiro siga [este tutorial](mcp-endpoint-enable.md) para habilitar.

# Como conectar uma função MCP simples a um agente, como uma calculadora

### Se você usa a implantação com todos os módulos
Se você usa a implantação com todos os módulos, entre no Console de Gerenciamento (智控台), em Gerenciamento de Agentes, clique em `Configurar Papel` (配置角色); à direita de `Reconhecimento de Intenção` (意图识别), há o botão `Editar Funções` (编辑功能).

Clique nesse botão. Na página que abrir, na parte de baixo, haverá o `Endpoint MCP` (MCP接入点); em condições normais, será exibido o `Endereço do Endpoint MCP` (MCP接入点地址) deste agente. A seguir, vamos estender este agente com uma função de calculadora baseada na tecnologia MCP.

Esse `Endereço do Endpoint MCP` é importante; você vai usá-lo daqui a pouco.

### Se você usa a implantação de módulo único
Se você usa a implantação de módulo único e já configurou o endereço do endpoint MCP no arquivo de configuração, em condições normais, ao iniciar a implantação de módulo único, serão exibidos os logs abaixo.
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

Como mostrado acima, na saída `mcp接入点是`, o valor `ws://192.168.1.25:8004/mcp_endpoint/mcp/?token=abc` é o seu `Endereço do Endpoint MCP`.

Esse `Endereço do Endpoint MCP` é importante; você vai usá-lo daqui a pouco.

## Passo 1: baixar o código do projeto de calculadora MCP do Xiaoge

Abra no navegador o [projeto da calculadora](https://github.com/78/mcp-calculator) escrito pelo Xiaoge.

Com a página aberta, localize o botão verde escrito `Code`, clique nele e você verá o botão `Download ZIP`.

Clique nele para baixar o pacote com o código-fonte do projeto. Depois de baixá-lo no seu computador, descompacte-o; o nome provavelmente será `mcp-calculatorr-main` e você precisa renomeá-lo para `mcp-calculator`. Em seguida, pelo terminal, entre no diretório do projeto e instale as dependências


```bash
# Entrar no diretório do projeto
cd mcp-calculator

conda remove -n mcp-calculator --all -y
conda create -n mcp-calculator python=3.10 -y
conda activate mcp-calculator

pip install -r requirements.txt
```

## Passo 2: iniciar

Antes de iniciar, você já deve ter copiado o endereço do endpoint MCP do seu agente no Console de Gerenciamento.

Por exemplo, o endereço MCP do meu agente é
```
ws://192.168.1.25:8004/mcp_endpoint/mcp/?token=abc
```

Agora digite o comando

```bash
export MCP_ENDPOINT=ws://192.168.1.25:8004/mcp_endpoint/mcp/?token=abc
```

Depois de digitar, inicie o programa

```bash
python mcp_pipe.py calculator.py
```

### Se você usa a implantação com Console de Gerenciamento
Se você usa a implantação com Console de Gerenciamento, depois de iniciar, entre novamente no Console de Gerenciamento e clique em atualizar o status de conexão do MCP; você verá a lista de funções que adicionou.

### Se você usa a implantação de módulo único
Se você usa a implantação de módulo único, quando o dispositivo conectar serão exibidos logs semelhantes ao abaixo, o que indica sucesso

```
250705 -INFO-正在初始化MCP接入点: wss://2662r3426b.vicp.fun/mcp_e 
250705 -INFO-发送MCP接入点初始化消息
250705 -INFO-MCP接入点连接成功
250705 -INFO-MCP接入点初始化成功
250705 -INFO-统一工具处理器初始化完成
250705 -INFO-MCP接入点服务器信息: name=Calculator, version=1.9.4
250705 -INFO-MCP接入点支持的工具数量: 1
250705 -INFO-所有MCP接入点工具已获取，客户端准备就绪
250705 -INFO-工具缓存已刷新
250705 -INFO-当前支持的函数列表: [ 'get_time', 'get_lunar', 'play_music', 'get_weather', 'handle_exit_intent', 'calculator']
```
Se `'calculator'` estiver incluído, significa que o dispositivo poderá chamar a ferramenta de calculadora com base no reconhecimento de intenção.
