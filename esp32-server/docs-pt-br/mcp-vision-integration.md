# Guia de Uso do Modelo de Visão
Este tutorial está dividido em duas partes:
- Parte 1: habilitar o modelo de visão executando o xiaozhi-server em módulo único
- Parte 2: como habilitar o modelo de visão na execução de todos os módulos

Antes de habilitar o modelo de visão, você precisa preparar três coisas:
- Você precisa de um dispositivo com câmera que já implemente a função de acionamento da câmera no repositório do Xia Ge (虾哥). Por exemplo: `立创·实战派ESP32-S3开发板` (placa de desenvolvimento LCSC “Shizhanpai” ESP32-S3)
- O firmware do seu dispositivo atualizado para a versão 1.6.6 ou superior
- Você já colocou com sucesso o módulo de conversação básica em funcionamento

## Habilitar o modelo de visão executando o xiaozhi-server em módulo único

### Passo 1: confirmar a rede
Porque o modelo de visão, por padrão, inicia a porta 8003.

Se você executa via docker, confira se o seu `docker-compose.yml` expõe a porta `8003`; se não expõe, atualize para o arquivo `docker-compose.yml` mais recente

Se você executa a partir do código-fonte, confira se o firewall libera a porta `8003`

### Passo 2: escolher o seu modelo de visão
Abra o seu arquivo `data/.config.yaml` e defina o `selected_module.VLLM` para algum modelo de visão. Atualmente já suportamos modelos de visão com interface do tipo `openai`; o `ChatGLMVLLM` é um dos modelos compatíveis com `openai`.

```
selected_module:
  VAD: ..
  ASR: ..
  LLM: ..
  VLLM: ChatGLMVLLM
  TTS: ..
  Memory: ..
  Intent: ..
```

Supondo que usemos o `ChatGLMVLLM` como modelo de visão, primeiro precisamos acessar o site da [Zhipu AI (智谱AI)](https://bigmodel.cn/usercenter/proj-mgmt/apikeys) e solicitar a chave. Se você já solicitou uma chave antes, pode reutilizar essa chave.

No seu arquivo de configuração, acrescente esta configuração; se ela já existir, basta definir a sua api_key.

```
VLLM:
  ChatGLMVLLM:
    api_key: sua_api_key
```

### Passo 3: iniciar o serviço xiaozhi-server
Se você usa o código-fonte, digite o comando para iniciar
```
python app.py
```
Se você executa via docker, reinicie o container
```
docker restart xiaozhi-esp32-server
```

Após a inicialização, serão exibidos os seguintes logs.

```
2025-06-01 **** - OTA接口是           http://192.168.4.7:8003/xiaozhi/ota/
2025-06-01 **** - 视觉分析接口是        http://192.168.4.7:8003/mcp/vision/explain
2025-06-01 **** - Websocket地址是       ws://192.168.4.7:8000/xiaozhi/v1/
2025-06-01 **** - =======上面的地址是websocket协议地址，请勿用浏览器访问=======
2025-06-01 **** - 如想测试websocket请启动digital-human模块，打开浏览器交互测试
2025-06-01 **** - =============================================================
```

Depois de iniciar, use o navegador para abrir a conexão do `endpoint de análise visual (视觉分析接口)` indicado no log. Veja o que é exibido. Se você está no linux e não tem navegador, pode executar este comando:
```
curl -i seu-endpoint-de-analise-visual
```

Normalmente, será exibido assim
```
MCP Vision 接口运行正常，视觉解释接口地址是：http://xxxx:8003/mcp/vision/explain
```

Atenção: se você está em implantação em rede pública, ou via docker, é obrigatório alterar esta configuração no seu `data/.config.yaml`
```
server:
  vision_explain: http://seu-ip-ou-dominio:porta/mcp/vision/explain
```

Por quê? Porque o endpoint de explicação visual precisa ser entregue ao dispositivo; se o seu endereço for um endereço de rede local, ou um endereço interno do docker, o dispositivo não conseguirá acessá-lo.

Suponha que o seu endereço público seja `111.111.111.111`; então o `vision_explain` deve ser configurado assim

```
server:
  vision_explain: http://111.111.111.111:8003/mcp/vision/explain
```

Se o seu endpoint MCP Vision está funcionando normalmente e você também conseguiu abrir no navegador o `endereço do endpoint de explicação visual` entregue, siga para o próximo passo

### Passo 4: despertar o dispositivo para ativar

Diga ao dispositivo: “Por favor, abra a câmera e diga o que você está vendo”

Fique de olho na saída de logs do xiaozhi-server para ver se há erros.


## Como habilitar o modelo de visão na execução de todos os módulos

### Passo 1: confirmar a rede
Porque o modelo de visão, por padrão, inicia a porta 8003.

Se você executa via docker, confira se o seu `docker-compose_all.yml` mapeia a porta `8003`; se não mapeia, atualize para o arquivo `docker-compose_all.yml` mais recente

Se você executa a partir do código-fonte, confira se o firewall libera a porta `8003`

### Passo 2: conferir o seu arquivo de configuração

Abra o seu arquivo `data/.config.yaml` e confira se a estrutura do seu arquivo de configuração é igual à do `data/config_from_api.yaml`. Se não for igual, ou se faltar algum item, complete.

### Passo 3: configurar a chave do modelo de visão

Nesse caso, precisamos primeiro acessar o site da [Zhipu AI (智谱AI)](https://bigmodel.cn/usercenter/proj-mgmt/apikeys) e solicitar a chave. Se você já solicitou uma chave antes, pode reutilizar essa chave.

Acesse o `Console de Gerenciamento (智控台)`, clique em `Configuração de Modelos (模型配置)` no menu superior, na barra lateral esquerda clique em `Modelo de Linguagem Visual (视觉打语言模型)`, localize `VLLM_ChatGLMVLLM`, clique no botão editar e, na janela que abrir, digite a sua chave em `Chave de API (API密钥)` e clique em salvar.

Depois de salvar com sucesso, vá até o agente que você deseja testar, clique em `Configurar Persona (配置角色)` e, no conteúdo que abrir, verifique se o `Modelo de Linguagem Visual (VLLM)` selecionado é o modelo de visão configurado agora. Clique em salvar.

### Passo 3: iniciar o módulo xiaozhi-server
Se você usa o código-fonte, digite o comando para iniciar
```
python app.py
```
Se você executa via docker, reinicie o container
```
docker restart xiaozhi-esp32-server
```

Após a inicialização, serão exibidos os seguintes logs.

```
2025-06-01 **** - 视觉分析接口是        http://192.168.4.7:8003/mcp/vision/explain
2025-06-01 **** - Websocket地址是       ws://192.168.4.7:8000/xiaozhi/v1/
2025-06-01 **** - =======上面的地址是websocket协议地址，请勿用浏览器访问=======
2025-06-01 **** - 如想测试websocket请启动digital-human模块，打开浏览器交互测试
2025-06-01 **** - =============================================================
```

Depois de iniciar, use o navegador para abrir a conexão do `endpoint de análise visual (视觉分析接口)` indicado no log. Veja o que é exibido. Se você está no linux e não tem navegador, pode executar este comando:
```
curl -i seu-endpoint-de-analise-visual
```

Normalmente, será exibido assim
```
MCP Vision 接口运行正常，视觉解释接口地址是：http://xxxx:8003/mcp/vision/explain
```

Atenção: se você está em implantação em rede pública, ou via docker, é obrigatório alterar esta configuração no seu `data/.config.yaml`
```
server:
  vision_explain: http://seu-ip-ou-dominio:porta/mcp/vision/explain
```

Por quê? Porque o endpoint de explicação visual precisa ser entregue ao dispositivo; se o seu endereço for um endereço de rede local, ou um endereço interno do docker, o dispositivo não conseguirá acessá-lo.

Suponha que o seu endereço público seja `111.111.111.111`; então o `vision_explain` deve ser configurado assim

```
server:
  vision_explain: http://111.111.111.111:8003/mcp/vision/explain
```

Se o seu endpoint MCP Vision está funcionando normalmente e você também conseguiu abrir no navegador o `endereço do endpoint de explicação visual` entregue, siga para o próximo passo

### Passo 4: despertar o dispositivo para ativar

Diga ao dispositivo: “Por favor, abra a câmera e diga o que você está vendo”

Fique de olho na saída de logs do xiaozhi-server para ver se há erros.
