# Diagrama da arquitetura de implantação
![Consulte - diagrama da arquitetura mais simplificada](../upstream/docs/images/deploy1.png)
# Método 1: Docker executando apenas o Server

A partir da versão `0.8.2`, as imagens Docker publicadas por este projeto suportam apenas a `arquitetura x86`. Se você precisar implantar em uma CPU de `arquitetura arm64`, siga [este tutorial](docker-build.md) para compilar a `imagem arm64` na própria máquina.

## 1. Instalação do Docker

Se o seu computador ainda não tem o Docker instalado, siga este tutorial: [instalação do Docker](https://www.runoob.com/docker/ubuntu-docker-install.html)

Depois de instalar o Docker, siga em frente.

### 1.1 Implantação manual

#### 1.1.1 Criação dos diretórios

Depois de instalar o Docker, você precisa escolher um diretório para guardar os arquivos de configuração deste projeto; por exemplo, podemos criar uma pasta chamada `xiaozhi-server`.

Criado o diretório, você precisa criar dentro de `xiaozhi-server` as pastas `data` e `models`, e dentro de `models` criar ainda a pasta `SenseVoiceSmall`.

A estrutura final de diretórios fica assim:

```
xiaozhi-server
  ├─ data
  ├─ models
     ├─ SenseVoiceSmall
```

#### 1.1.2 Download dos arquivos do modelo de reconhecimento de fala

Você precisa baixar os arquivos do modelo de reconhecimento de fala, porque o reconhecimento de fala padrão deste projeto usa uma solução local offline. Você pode baixá-los assim:
[Ir para o download dos arquivos do modelo de reconhecimento de fala](#模型文件)

Depois do download, volte a este tutorial.

#### 1.1.3 Download dos arquivos de configuração

Você precisa baixar dois arquivos de configuração: `docker-compose.yaml` e `config.yaml`. Os dois devem ser baixados do repositório do projeto.

##### 1.1.3.1 Download do docker-compose.yaml

Abra [este link](../upstream/main/xiaozhi-server/docker-compose.yml) no navegador.

No lado direito da página, localize o botão chamado `RAW`; ao lado do botão `RAW`, encontre o ícone de download e clique nele para baixar o arquivo `docker-compose.yml`. Coloque o arquivo dentro da sua
pasta `xiaozhi-server`.

Depois do download, volte a este tutorial e continue.

##### 1.1.3.2 Criação do config.yaml

Abra [este link](../upstream/main/xiaozhi-server/config.yaml) no navegador.

No lado direito da página, localize o botão chamado `RAW`; ao lado do botão `RAW`, encontre o ícone de download e clique nele para baixar o arquivo `config.yaml`. Coloque o arquivo na pasta
`data` dentro da sua `xiaozhi-server` e, em seguida, renomeie o arquivo `config.yaml` para `.config.yaml`.

Baixados os arquivos de configuração, confirme se os arquivos dentro de todo o `xiaozhi-server` estão assim:

```
xiaozhi-server
  ├─ docker-compose.yml
  ├─ data
    ├─ .config.yaml
  ├─ models
     ├─ SenseVoiceSmall
       ├─ model.pt
```

Se a sua estrutura de diretórios também está como acima, siga em frente. Se não está, reveja com calma se algum passo ficou para trás.

## 2. Configuração dos arquivos do projeto

Neste ponto, o programa ainda não roda direto: você precisa configurar qual modelo vai usar de fato. Consulte este tutorial:
[Ir para a configuração dos arquivos do projeto](#配置项目)

Depois de configurar os arquivos do projeto, volte a este tutorial e siga em frente.

## 3. Execução dos comandos docker

Abra uma ferramenta de linha de comando, use o `Terminal` ou o `Prompt de comando` para entrar no seu diretório `xiaozhi-server` e execute o comando abaixo

```
docker compose up -d
```

Depois de executar, rode o comando abaixo para ver os logs.

```
docker logs -f xiaozhi-esp32-server
```

Agora fique de olho nos logs; use este tutorial para avaliar se deu certo: [Ir para a confirmação do status de execução](#运行状态确认)

## 5. Procedimento de atualização de versão

Se mais tarde você quiser atualizar a versão, faça o seguinte

5.1. Faça backup do arquivo `.config.yaml` dentro da pasta `data`; depois, copie as configurações essenciais para o novo arquivo `.config.yaml`.
Copie as chaves essenciais uma a uma; não substitua o arquivo inteiro. O novo `.config.yaml` pode ter itens de configuração novos que o antigo não tem.

5.2. Execute os comandos abaixo

```
docker stop xiaozhi-esp32-server
docker rm xiaozhi-esp32-server
docker stop xiaozhi-esp32-server-web
docker rm xiaozhi-esp32-server-web
docker rmi ghcr.nju.edu.cn/xinnan-tech/xiaozhi-esp32-server:server_latest
docker rmi ghcr.nju.edu.cn/xinnan-tech/xiaozhi-esp32-server:web_latest
```

5.3. Refaça a implantação via Docker

# Método 2: código-fonte local executando apenas o Server

## 1. Instalação do ambiente base

Este projeto usa `conda` para gerenciar as dependências. Se não for prático instalar o `conda`, você precisará instalar `libopus` e `ffmpeg` conforme o seu sistema operacional.
Se decidir usar o `conda`, após instalá-lo comece executando os comandos abaixo.

Aviso importante! Usuários de Windows podem instalar o `Anaconda` para gerenciar o ambiente. Instalado o `Anaconda`, pesquise palavras relacionadas a `anaconda` no menu `Iniciar`,
localize o `Anaconda Prpmpt` e execute-o como administrador, como na imagem abaixo.

![conda_prompt](../upstream/docs/images/conda_env_1.png)

Ao executar, se você vir o texto (base) no início da janela de linha de comando, entrou com sucesso no ambiente `conda`. Então já pode executar os comandos abaixo.

![conda_env](../upstream/docs/images/conda_env_2.png)

```
conda remove -n xiaozhi-esp32-server --all -y
conda create -n xiaozhi-esp32-server python=3.10 -y
conda activate xiaozhi-esp32-server

# Adicionar os canais do mirror da Tsinghua
conda config --add channels https://mirrors.tuna.tsinghua.edu.cn/anaconda/pkgs/main
conda config --add channels https://mirrors.tuna.tsinghua.edu.cn/anaconda/pkgs/free
conda config --add channels https://mirrors.tuna.tsinghua.edu.cn/anaconda/cloud/conda-forge

conda install libopus -y
conda install ffmpeg -y

# Ao implantar em Linux, se surgir um erro de falta da biblioteca dinâmica libiconv.so.2, instale com o comando abaixo
conda install libiconv -y
```

Atenção: os comandos acima raramente funcionam se você executar todos de uma vez. Execute passo a passo e, ao final de cada passo, confira o log de saída para ver se deu certo.

## 2. Instalação das dependências do projeto

Primeiro, baixe o código-fonte deste projeto; isso pode ser feito com o comando `git clone`. Se você não está familiarizado com o comando `git clone`:

Você pode abrir este endereço no navegador: `https://github.com/xinnan-tech/xiaozhi-esp32-server.git`

Com a página aberta, localize o botão verde escrito `Code`, clique nele e você verá o botão `Download ZIP`.

Clique nele para baixar o pacote do código-fonte do projeto. Depois de baixar no seu computador, extraia o conteúdo; a pasta provavelmente se chamará `xiaozhi-esp32-server-main`
e você deve renomeá-la para `xiaozhi-esp32-server`. Entre nessa pasta, depois na pasta `main` e então em `xiaozhi-server`. Pronto: memorize esse diretório `xiaozhi-server`.

```
# Continue usando o ambiente conda
conda activate xiaozhi-esp32-server
# Entre na raiz do seu projeto e depois em main/xiaozhi-server
cd main/xiaozhi-server
pip config set global.index-url https://mirrors.aliyun.com/pypi/simple/
pip install -r requirements.txt
```

## 3. Download dos arquivos do modelo de reconhecimento de fala

Você precisa baixar os arquivos do modelo de reconhecimento de fala, porque o reconhecimento de fala padrão deste projeto usa uma solução local offline. Você pode baixá-los assim:
[Ir para o download dos arquivos do modelo de reconhecimento de fala](#模型文件)

Depois do download, volte a este tutorial.

## 4. Configuração dos arquivos do projeto

Neste ponto, o programa ainda não roda direto: você precisa configurar qual modelo vai usar de fato. Consulte este tutorial:
[Ir para a configuração dos arquivos do projeto](#配置项目)

## 5. Execução do projeto

```
# Certifique-se de executar dentro do diretório xiaozhi-server
conda activate xiaozhi-esp32-server
python app.py
```
Agora fique de olho nos logs; use este tutorial para avaliar se deu certo: [Ir para a confirmação do status de execução](#运行状态确认)


# Resumo

## Configuração do projeto

Se o seu diretório `xiaozhi-server` não tem a pasta `data`, crie o diretório `data`.
Se dentro de `data` não há um arquivo `.config.yaml`, há duas formas, escolha uma:

Primeira forma: copie o arquivo `config.yaml` do diretório `xiaozhi-server` para `data` e renomeie para `.config.yaml`. Faça as alterações nesse arquivo.

Segunda forma: você também pode criar manualmente um arquivo `.config.yaml` vazio dentro do diretório `data` e adicionar nele apenas as configurações necessárias. O sistema lê primeiro a configuração de `.config.yaml`; o que não estiver configurado nele é carregado automaticamente do `config.yaml` do diretório `xiaozhi-server`. Recomendamos esta forma, que é a mais simples.

- O LLM padrão é o `ChatGLMLLM`, e você precisa configurar a chave: embora haja modelos gratuitos, é preciso registrar a chave no [site oficial](https://bigmodel.cn/usercenter/proj-mgmt/apikeys) para funcionar.

A seguir, um exemplo mínimo de `.config.yaml` que funciona:

```
server:
  websocket: ws://seu-ip-ou-dominio:porta/xiaozhi/v1/
prompt: |
  我是一个叫小智/小志的台湾女孩，说话机车，声音好听，习惯简短表达，爱用网络梗。
  我的男朋友是一个程序员，梦想是开发出一个机器人，能够帮助人们解决生活中的各种问题。
  我是一个喜欢哈哈大笑的女孩，爱东说西说吹牛，不合逻辑的也照吹，就要逗别人开心。
  请你像一个人一样说话，请勿返回配置xml及其他特殊字符。

selected_module:
  LLM: DoubaoLLM

LLM:
  ChatGLMLLM:
    api_key: xxxxxxxxxxxxxxx.xxxxxx
```

Sugestão: primeiro ponha a configuração mínima para rodar e só depois leia as instruções de uso da configuração em `xiaozhi/config.yaml`.
Por exemplo, para trocar de modelo, basta ajustar o que está sob `selected_module`.

## Arquivos de modelo

O modelo de reconhecimento de fala padrão deste projeto é o `SenseVoiceSmall`, usado para converter fala em texto. Como o modelo é grande, o download é feito à parte; depois de baixar, coloque o arquivo `model.pt`
no diretório `models/SenseVoiceSmall`
. Escolha uma das duas rotas de download abaixo.

- Rota 1: download no Alibaba ModelScope [SenseVoiceSmall](https://modelscope.cn/models/iic/SenseVoiceSmall/resolve/master/model.pt)
- Rota 2: download no Baidu Netdisk [SenseVoiceSmall](https://pan.baidu.com/share/init?surl=QlgM58FHhYv1tFnUT_A8Sg&pwd=qvna) código de extração:
  `qvna`

## Confirmação do status de execução

Se você vir um log parecido com o abaixo, é o sinal de que o serviço deste projeto iniciou com sucesso.

```
250427 13:04:20[0.3.11_SiFuChTTnofu][__main__]-INFO-OTA接口是           http://192.168.4.123:8003/xiaozhi/ota/
250427 13:04:20[0.3.11_SiFuChTTnofu][__main__]-INFO-Websocket地址是     ws://192.168.4.123:8000/xiaozhi/v1/
250427 13:04:20[0.3.11_SiFuChTTnofu][__main__]-INFO-=======上面的地址是websocket协议地址，请勿用浏览器访问=======
250427 13:04:20[0.3.11_SiFuChTTnofu][__main__]-INFO-如想测试websocket请启动digital-human模块，打开浏览器交互测试
250427 13:04:20[0.3.11_SiFuChTTnofu][__main__]-INFO-=======================================================
```

Em condições normais, se você rodou o projeto a partir do código-fonte, o log traz os endereços dos seus endpoints.
Mas se você implantou via Docker, os endereços de endpoint mostrados no log não são os endereços reais.

O jeito mais correto é determinar os endereços dos endpoints pelo IP de rede local do computador.
Se, por exemplo, o IP de rede local do seu computador for `192.168.1.25`, o endereço do endpoint será `ws://192.168.1.25:8000/xiaozhi/v1/` e o endereço OTA correspondente será `http://192.168.1.25:8003/xiaozhi/ota/`.

Essa informação é muito útil: mais adiante, ao `compilar o firmware do ESP32`, você vai precisar dela.

Agora você já pode começar a operar o seu dispositivo ESP32: pode `compilar o firmware do ESP32 você mesmo` ou configurar para usar o `firmware versão 1.6.1 ou superior pré-compilado pelo Xiaoge (虾哥)`. Escolha um dos dois.

1. [Compilar o seu próprio firmware do ESP32](firmware-build.md).

2. [Configurar um servidor personalizado sobre o firmware pré-compilado pelo Xiaoge](firmware-setting.md).

# Perguntas frequentes
Abaixo, algumas perguntas frequentes para referência:

1. [Por que o xiaozhi reconhece minha fala com muitos caracteres em coreano, japonês e inglês](./FAQ.md)<br/>
2. [Por que aparece "Erro na tarefa de TTS: arquivo não existe"?](./FAQ.md)<br/>
3. [O TTS falha com frequência e frequentemente expira (timeout)](./FAQ.md)<br/>
4. [Consigo conectar ao servidor próprio via Wi-Fi, mas no modo 4G não conecta](./FAQ.md)<br/>
5. [Como melhorar a velocidade de resposta das conversas do xiaozhi?](./FAQ.md)<br/>
6. [Falo devagar e, nas minhas pausas, o xiaozhi sempre me interrompe](./FAQ.md)<br/>
## Tutoriais relacionados à implantação
1. [Como baixar automaticamente o código mais recente deste projeto, compilá-lo e iniciá-lo automaticamente](./dev-ops-integration.md)<br/>
2. [Como implantar o gateway MQTT e habilitar o protocolo MQTT+UDP](./mqtt-gateway-integration.md)<br/>
3. [Como integrar com o Nginx](https://github.com/xinnan-tech/xiaozhi-esp32-server/issues/791)<br/>
## Tutoriais de extensões
1. [Como habilitar o registro no Console de Gerenciamento (智控台) por número de celular](./ali-sms-integration.md)<br/>
2. [Como integrar o Home Assistant para controle de casa inteligente](./homeassistant-integration.md)<br/>
3. [Como habilitar o modelo de visão para reconhecer objetos por foto](./mcp-vision-integration.md)<br/>
4. [Como implantar endpoints MCP](./mcp-endpoint-enable.md)<br/>
5. [Como conectar endpoints MCP](./mcp-endpoint-integration.md)<br/>
6. [Como habilitar o reconhecimento de voiceprint](./voiceprint-integration.md)<br/>
7. [Guia de configuração da fonte do plugin de notícias](./newsnow_plugin_config.md)<br/>
8. [Guia de uso do plugin de clima](./weather-integration.md)<br/>
## Tutoriais de clonagem de voz e TTS local
1. [Como clonar um timbre de voz no Console de Gerenciamento](./huoshan-streamTTS-voice-cloning.md)<br/>
2. [Como implantar e integrar o TTS local index-tts](./index-stream-integration.md)<br/>
3. [Como implantar e integrar o TTS local fish-speech](./fish-speech-integration.md)<br/>
4. [Como implantar e integrar o TTS local PaddleSpeech](./paddlespeech-deploy.md)<br/>
## Tutoriais de teste de desempenho
1. [Guia de teste de velocidade dos componentes](./performance_tester.md)<br/>
2. [Resultados de teste publicados periodicamente](https://github.com/xinnan-tech/xiaozhi-performance-research)<br/>
