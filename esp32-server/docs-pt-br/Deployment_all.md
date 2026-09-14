# Diagrama da arquitetura de implantação
![Consulte - diagrama da arquitetura de instalação de todos os módulos](../upstream/docs/images/deploy2.png)
# Método 1: Docker executando todos os módulos
A partir da versão `0.8.2`, as imagens Docker publicadas por este projeto suportam apenas a `arquitetura x86`. Se você precisar implantar em uma CPU de `arquitetura arm64`, siga [este tutorial](docker-build.md) para compilar a `imagem arm64` na própria máquina.

## 1. Instalação do Docker

Se o seu computador ainda não tem o Docker instalado, siga este tutorial: [instalação do Docker](https://www.runoob.com/docker/ubuntu-docker-install.html)

Há duas formas de instalar todos os módulos com o Docker: você pode [usar o script de instalação automática](./Deployment_all.md#11-懒人脚本) (autor [@VanillaNahida](https://github.com/VanillaNahida))  
O script baixa automaticamente os arquivos e os arquivos de configuração de que você precisa; você também pode usar a [implantação manual](./Deployment_all.md#12-手动部署) para montar tudo do zero.



### 1.1 Script de instalação automática (懒人脚本)
A implantação é simples; você pode consultar o [tutorial em vídeo](https://www.bilibili.com/video/BV17bbvzHExd/). Abaixo, a versão em texto do tutorial:
> [!NOTE]  
> Por enquanto, a implantação com um clique só foi testada em servidores Ubuntu; outros sistemas não foram experimentados e podem apresentar alguns bugs estranhos

Conecte-se ao servidor com uma ferramenta de SSH e execute o script abaixo com privilégios de root
```bash
sudo bash -c "$(wget -qO- https://ghfast.top/https://raw.githubusercontent.com/xinnan-tech/xiaozhi-esp32-server/main/docker-setup.sh)"
```

O script conclui automaticamente as seguintes etapas:
> 1. Instalar o Docker
> 2. Configurar os mirrors de imagens
> 3. Baixar/puxar as imagens
> 4. Baixar os arquivos do modelo de reconhecimento de fala
> 5. Guiar a configuração do servidor
>

Depois de executar e configurar o básico, consulte as 3 coisas mais importantes indicadas em [4. Execução do programa](#4. 运行程序) e em [5. Reinício do xiaozhi-esp32-server](#5.重启xiaozhi-esp32-server); concluídas essas três configurações, está pronto para uso.

### 1.2 Implantação manual

#### 1.2.1 Criação dos diretórios

Depois de instalar o Docker, você precisa escolher um diretório para guardar os arquivos de configuração deste projeto; por exemplo, podemos criar uma pasta chamada `xiaozhi-server`.

Criado o diretório, você precisa criar dentro de `xiaozhi-server` as pastas `data` e `models`, e dentro de `models` criar ainda a pasta `SenseVoiceSmall`.

A estrutura final de diretórios fica assim:

```
xiaozhi-server
  ├─ data
  ├─ models
     ├─ SenseVoiceSmall
```

#### 1.2.2 Download dos arquivos do modelo de reconhecimento de fala

O modelo de reconhecimento de fala padrão deste projeto é o `SenseVoiceSmall`, usado para converter fala em texto. Como o modelo é grande, o download é feito à parte; depois de baixar, coloque o arquivo `model.pt`
no diretório `models/SenseVoiceSmall`
. Escolha uma das duas rotas de download abaixo.

- Rota 1: download no Alibaba ModelScope [SenseVoiceSmall](https://modelscope.cn/models/iic/SenseVoiceSmall/resolve/master/model.pt)
- Rota 2: download no Baidu Netdisk [SenseVoiceSmall](https://pan.baidu.com/share/init?surl=QlgM58FHhYv1tFnUT_A8Sg&pwd=qvna) código de extração:
  `qvna`


#### 1.2.3 Download dos arquivos de configuração

Você precisa baixar dois arquivos de configuração: `docker-compose_all.yaml` e `config_from_api.yaml`. Os dois devem ser baixados do repositório do projeto.

##### 1.2.3.1 Download do docker-compose_all.yaml

Abra [este link](../upstream/main/xiaozhi-server/docker-compose_all.yml) no navegador.

No lado direito da página, localize o botão chamado `RAW`; ao lado do botão `RAW`, encontre o ícone de download e clique nele para baixar o arquivo `docker-compose_all.yml`. Coloque o arquivo dentro da sua
pasta `xiaozhi-server`.

Ou execute diretamente `wget https://raw.githubusercontent.com/xinnan-tech/xiaozhi-esp32-server/refs/heads/main/main/xiaozhi-server/docker-compose_all.yml` para baixar.

Depois do download, volte a este tutorial e continue.

##### 1.2.3.2 Download do config_from_api.yaml

Abra [este link](../upstream/main/xiaozhi-server/config_from_api.yaml) no navegador.

No lado direito da página, localize o botão chamado `RAW`; ao lado do botão `RAW`, encontre o ícone de download e clique nele para baixar o arquivo `config_from_api.yaml`. Coloque o arquivo na pasta
`data` dentro da sua `xiaozhi-server` e, em seguida, renomeie o arquivo `config_from_api.yaml` para `.config.yaml`.

Ou execute diretamente `wget https://raw.githubusercontent.com/xinnan-tech/xiaozhi-esp32-server/refs/heads/main/main/xiaozhi-server/config_from_api.yaml` para baixar e salvar.

Baixados os arquivos de configuração, confirme se os arquivos dentro de todo o `xiaozhi-server` estão assim:

```
xiaozhi-server
  ├─ docker-compose_all.yml
  ├─ data
    ├─ .config.yaml
  ├─ models
     ├─ SenseVoiceSmall
       ├─ model.pt
```

Se a sua estrutura de diretórios também está como acima, siga em frente. Se não está, reveja com calma se algum passo ficou para trás.

## 2. Backup dos dados

Se você já executou o Console de Gerenciamento (智控台) com sucesso antes e ele guarda as suas informações de chaves, copie primeiro para fora do Console de Gerenciamento os dados importantes. Durante a atualização, os dados originais podem ser sobrescritos.

## 3. Limpeza das imagens e dos containers de versões anteriores
Em seguida, abra uma ferramenta de linha de comando, use o `Terminal` ou o `Prompt de comando` para entrar no seu diretório `xiaozhi-server` e execute os comandos abaixo

```
docker compose -f docker-compose_all.yml down

docker stop xiaozhi-esp32-server
docker rm xiaozhi-esp32-server

docker stop xiaozhi-esp32-server-web
docker rm xiaozhi-esp32-server-web

docker stop xiaozhi-esp32-server-db
docker rm xiaozhi-esp32-server-db

docker stop xiaozhi-esp32-server-redis
docker rm xiaozhi-esp32-server-redis

docker rmi ghcr.nju.edu.cn/xinnan-tech/xiaozhi-esp32-server:server_latest
docker rmi ghcr.nju.edu.cn/xinnan-tech/xiaozhi-esp32-server:web_latest
```

## 4. Execução do programa
Execute o comando abaixo para iniciar os containers da nova versão

```
docker compose -f docker-compose_all.yml up -d
```

Depois de executar, rode o comando abaixo para ver os logs.

```
docker logs -f xiaozhi-esp32-server-web
```

Quando você vir este log de saída, significa que o seu Console de Gerenciamento iniciou com sucesso.

```
2025-xx-xx 22:11:12.445 [main] INFO  c.a.d.s.b.a.DruidDataSourceAutoConfigure - Init DruidDataSource
2025-xx-xx 21:28:53.873 [main] INFO  xiaozhi.AdminApplication - Started AdminApplication in 16.057 seconds (process running for 17.941)
http://localhost:8002/xiaozhi/doc.html
```

Observe que, neste momento, apenas o Console de Gerenciamento está em execução; se o `xiaozhi-esp32-server` na porta 8000 reportar erro, ignore por enquanto.

Agora, use o navegador para abrir o Console de Gerenciamento no endereço http://127.0.0.1:8002 e registre o primeiro usuário. O primeiro usuário é o superadministrador; todos os usuários seguintes serão usuários comuns. Usuários comuns só podem vincular dispositivos e configurar Agentes (智能体); o superadministrador pode gerenciar modelos, gerenciar usuários, configurar parâmetros etc.

Em seguida, há três coisas importantes a fazer:

### Primeira coisa importante

Usando a conta de superadministrador, entre no Console de Gerenciamento, localize no menu superior `Gerenciamento de Parâmetros` (参数管理), localize o primeiro registro da lista, cujo `Código do Parâmetro` (参数编码) é `server.secret`, e copie o seu `Valor do Parâmetro` (参数值).

Vale uma explicação sobre o `server.secret`: esse `Valor do Parâmetro` é muito importante; ele serve para o nosso lado `Server` conectar ao `manager-api`. O `server.secret` é uma chave gerada automaticamente e aleatoriamente toda vez que o módulo manager é implantado do zero.

Copiado o `Valor do Parâmetro`, abra o arquivo `.config.yaml` no diretório `data` dentro de `xiaozhi-server`. Neste momento, o conteúdo do seu arquivo de configuração deve ser este:

```
manager-api:
  url:  http://127.0.0.1:8002/xiaozhi
  secret: seu-valor-de-server.secret
```
1. Cole o `Valor do Parâmetro` do `server.secret` que você acabou de copiar do Console de Gerenciamento no campo `secret` do arquivo `.config.yaml`.

2. Como a sua implantação é via Docker, altere o `url` para o `http://xiaozhi-esp32-server-web:8002/xiaozhi` abaixo

3. Como a sua implantação é via Docker, altere o `url` para o `http://xiaozhi-esp32-server-web:8002/xiaozhi` abaixo

4. Como a sua implantação é via Docker, altere o `url` para o `http://xiaozhi-esp32-server-web:8002/xiaozhi` abaixo

Ficará assim
```
manager-api:
  url: http://xiaozhi-esp32-server-web:8002/xiaozhi
  secret: 12345678-xxxx-xxxx-xxxx-123456789000
```

Salvado o arquivo, siga para a segunda coisa importante

### Segunda coisa importante

Usando a conta de superadministrador, entre no Console de Gerenciamento, localize no menu superior `Configuração de Modelos` (模型配置) e, na barra lateral esquerda, clique em `LLM` (大语言模型); localize o primeiro registro, `Zhipu AI` (智谱AI), e clique no botão `Editar` (修改),
quando a caixa de edição abrir, preencha a chave da sua conta `Zhipu AI` registrada no campo `Chave de API` (API密钥). Em seguida, clique em Salvar.

## 5. Reinício do xiaozhi-esp32-server

Em seguida, abra uma ferramenta de linha de comando, use o `Terminal` ou o `Prompt de comando` e digite
```
docker restart xiaozhi-esp32-server
docker logs -f xiaozhi-esp32-server
```
Se você vir um log semelhante ao abaixo, é o sinal de que o Server iniciou com sucesso.

```
25-02-23 12:01:09[core.websocket_server] - INFO - Websocket地址是      ws://xxx.xx.xx.xx:8000/xiaozhi/v1/
25-02-23 12:01:09[core.websocket_server] - INFO - =======上面的地址是websocket协议地址，请勿用浏览器访问=======
25-02-23 12:01:09[core.websocket_server] - INFO - 如想测试websocket请启动digital-human模块，打开浏览器交互测试
25-02-23 12:01:09[core.websocket_server] - INFO - =======================================================
```

Como você fez a implantação de todos os módulos, há dois endpoints importantes que precisam ser gravados no ESP32.

Endpoint OTA:
```
http://ip-do-seu-host-na-rede-local:8002/xiaozhi/ota/
```

Endpoint Websocket:
```
ws://ip-do-seu-host:8000/xiaozhi/v1/
```

### Terceira coisa importante

Usando a conta de superadministrador, entre no Console de Gerenciamento, localize no menu superior `Gerenciamento de Parâmetros`, localize o parâmetro cujo código é `server.websocket` e insira o seu `Endpoint Websocket`.

Usando a conta de superadministrador, entre no Console de Gerenciamento, localize no menu superior `Gerenciamento de Parâmetros`, localize o código do parâmetro `server.ota` e insira o seu `Endpoint OTA`.

Agora você já pode começar a operar o seu dispositivo ESP32: pode `compilar o firmware do ESP32 você mesmo` ou configurar para usar o `firmware versão 1.6.1 ou superior pré-compilado pelo Xiaoge (虾哥)`. Escolha um dos dois

1. [Compilar o seu próprio firmware do ESP32](firmware-build.md).

2. [Configurar um servidor personalizado sobre o firmware pré-compilado pelo Xiaoge](firmware-setting.md).


# Método 2: código-fonte local executando todos os módulos

## 1. Instalação do banco de dados MySQL

Se o MySQL já está instalado nesta máquina, basta criar diretamente no banco de dados um banco chamado `xiaozhi_esp32_server`.

```sql
CREATE DATABASE xiaozhi_esp32_server CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Se ainda não há MySQL, você pode instalar o mysql via docker

```
docker run --name xiaozhi-esp32-server-db -e MYSQL_ROOT_PASSWORD=123456 -p 3306:3306 -e MYSQL_DATABASE=xiaozhi_esp32_server -e MYSQL_INITDB_ARGS="--character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci" -e TZ=Asia/Shanghai -d mysql:latest
```

## 2. Instalação do redis

Se ainda não há Redis, você pode instalar o redis via docker

```
docker run --name xiaozhi-esp32-server-redis -d -p 6379:6379 redis
```

## 3. Execução do programa manager-api

3.1 Instale o JDK21 e configure as variáveis de ambiente do JDK

3.2 Instale o Maven e configure as variáveis de ambiente do Maven

3.3 Use a ferramenta de programação VSCode e instale os plugins relacionados ao ambiente Java

3.4 Use a ferramenta de programação VSCode para carregar o módulo manager-api

Configure as informações de conexão com o banco de dados em `src/main/resources/application-dev.yml`

```
spring:
  datasource:
    username: root
    password: 123456
```
Configure as informações de conexão com o Redis em `src/main/resources/application-dev.yml`
```
spring:
    data:
      redis:
        host: localhost
        port: 6379
        password:
        database: 0
```

3.5 Execute o programa principal

Este projeto é um projeto SpringBoot; para iniciá-lo:
abra o `Application.java` e execute o método `Main` para iniciar

```
Caminho do arquivo:
src/main/java/xiaozhi/AdminApplication.java
```

Quando você vir este log de saída, significa que o seu `manager-api` iniciou com sucesso.

```
2025-xx-xx 22:11:12.445 [main] INFO  c.a.d.s.b.a.DruidDataSourceAutoConfigure - Init DruidDataSource
2025-xx-xx 21:28:53.873 [main] INFO  xiaozhi.AdminApplication - Started AdminApplication in 16.057 seconds (process running for 17.941)
http://localhost:8002/xiaozhi/doc.html
```

## 4. Execução do programa manager-web

4.1 Instale o nodejs

4.2 Use a ferramenta de programação VSCode para carregar o módulo manager-web

No terminal, entre no diretório manager-web

```
npm install
```
Em seguida, inicie
```
npm run serve
```

Observe que, se a API do seu manager-api não estiver em `http://localhost:8002`, altere durante o desenvolvimento o caminho em
`main/manager-web/.env.development`

Tendo iniciado com sucesso, use o navegador para abrir o Console de Gerenciamento no endereço http://127.0.0.1:8001 e registre o primeiro usuário. O primeiro usuário é o superadministrador; todos os usuários seguintes serão usuários comuns. Usuários comuns só podem vincular dispositivos e configurar Agentes; o superadministrador pode gerenciar modelos, gerenciar usuários, configurar parâmetros etc.


Importante: registrado com sucesso, use a conta de superadministrador para entrar no Console de Gerenciamento, localize no menu superior `Configuração de Modelos` e, na barra lateral esquerda, clique em `LLM`; localize o primeiro registro, `Zhipu AI`, e clique no botão `Editar`,
quando a caixa de edição abrir, preencha a chave da sua conta `Zhipu AI` registrada no campo `Chave de API`. Em seguida, clique em Salvar.

Importante: registrado com sucesso, use a conta de superadministrador para entrar no Console de Gerenciamento, localize no menu superior `Configuração de Modelos` e, na barra lateral esquerda, clique em `LLM`; localize o primeiro registro, `Zhipu AI`, e clique no botão `Editar`,
quando a caixa de edição abrir, preencha a chave da sua conta `Zhipu AI` registrada no campo `Chave de API`. Em seguida, clique em Salvar.

Importante: registrado com sucesso, use a conta de superadministrador para entrar no Console de Gerenciamento, localize no menu superior `Configuração de Modelos` e, na barra lateral esquerda, clique em `LLM`; localize o primeiro registro, `Zhipu AI`, e clique no botão `Editar`,
quando a caixa de edição abrir, preencha a chave da sua conta `Zhipu AI` registrada no campo `Chave de API`. Em seguida, clique em Salvar.

## 5. Instalação do ambiente Python

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

# Ao implantar em Linux, se surgir um erro do tipo falta da biblioteca dinâmica libiconv.so.2, instale com o comando abaixo
conda install libiconv -y
```

Atenção: os comandos acima raramente funcionam se você executar todos de uma vez. Execute passo a passo e, ao final de cada passo, confira o log de saída para ver se deu certo.

## 6. Instalação das dependências do projeto

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

### 7. Download dos arquivos do modelo de reconhecimento de fala

O modelo de reconhecimento de fala padrão deste projeto é o `SenseVoiceSmall`, usado para converter fala em texto. Como o modelo é grande, o download é feito à parte; depois de baixar, coloque o arquivo `model.pt`
no diretório `models/SenseVoiceSmall`
. Escolha uma das duas rotas de download abaixo.

- Rota 1: download no Alibaba ModelScope [SenseVoiceSmall](https://modelscope.cn/models/iic/SenseVoiceSmall/resolve/master/model.pt)
- Rota 2: download no Baidu Netdisk [SenseVoiceSmall](https://pan.baidu.com/share/init?surl=QlgM58FHhYv1tFnUT_A8Sg&pwd=qvna) código de extração:
  `qvna`

## 8. Configuração dos arquivos do projeto

Usando a conta de superadministrador, entre no Console de Gerenciamento, localize no menu superior `Gerenciamento de Parâmetros`, localize o primeiro registro da lista, cujo `Código do Parâmetro` é `server.secret`, e copie o seu `Valor do Parâmetro`.

Vale uma explicação sobre o `server.secret`: esse `Valor do Parâmetro` é muito importante; ele serve para o nosso lado `Server` conectar ao `manager-api`. O `server.secret` é uma chave gerada automaticamente e aleatoriamente toda vez que o módulo manager é implantado do zero.

Se o seu diretório `xiaozhi-server` não tem a pasta `data`, você precisa criar o diretório `data`.
Se dentro de `data` não há um arquivo `.config.yaml`, copie o arquivo `config_from_api.yaml` do diretório `xiaozhi-server` para `data` e renomeie para `.config.yaml`

Copiado o `Valor do Parâmetro`, abra o arquivo `.config.yaml` no diretório `data` dentro de `xiaozhi-server`. Neste momento, o conteúdo do seu arquivo de configuração deve ser este:

```
manager-api:
  url: http://127.0.0.1:8002/xiaozhi
  secret: seu-valor-de-server.secret
```

Cole o `Valor do Parâmetro` do `server.secret` que você acabou de copiar do Console de Gerenciamento no campo `secret` do arquivo `.config.yaml`.

Ficará assim
```
manager-api:
  url: http://127.0.0.1:8002/xiaozhi
  secret: 12345678-xxxx-xxxx-xxxx-123456789000
```

## 9. Execução do projeto

```
# Certifique-se de executar dentro do diretório xiaozhi-server
conda activate xiaozhi-esp32-server
python app.py
```

Se você vir um log parecido com o abaixo, é o sinal de que o serviço deste projeto iniciou com sucesso.

```
25-02-23 12:01:09[core.websocket_server] - INFO - Server is running at ws://xxx.xx.xx.xx:8000/xiaozhi/v1/
25-02-23 12:01:09[core.websocket_server] - INFO - =======上面的地址是websocket协议地址，请勿用浏览器访问=======
25-02-23 12:01:09[core.websocket_server] - INFO - 如想测试websocket请启动digital-human模块，打开浏览器交互测试
25-02-23 12:01:09[core.websocket_server] - INFO - =======================================================
```

Como você fez a implantação de todos os módulos, você tem dois endpoints importantes.

Endpoint OTA:
```
http://ip-do-seu-computador-na-rede-local:8002/xiaozhi/ota/
```

Endpoint Websocket:
```
ws://ip-do-seu-computador-na-rede-local:8000/xiaozhi/v1/
```

Grave obrigatoriamente esses dois endereços de endpoint no Console de Gerenciamento: eles afetam a distribuição do endereço websocket e a função de atualização automática.

1. Usando a conta de superadministrador, entre no Console de Gerenciamento, localize no menu superior `Gerenciamento de Parâmetros`, localize o parâmetro cujo código é `server.websocket` e insira o seu `Endpoint Websocket`.

2. Usando a conta de superadministrador, entre no Console de Gerenciamento, localize no menu superior `Gerenciamento de Parâmetros`, localize o código do parâmetro `server.ota` e insira o seu `Endpoint OTA`.


Agora você já pode começar a operar o seu dispositivo ESP32: pode `compilar o firmware do ESP32 você mesmo` ou configurar para usar o `firmware versão 1.6.1 ou superior pré-compilado pelo Xiaoge`. Escolha um dos dois

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
1. [Como habilitar o registro no Console de Gerenciamento por número de celular](./ali-sms-integration.md)<br/>
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
