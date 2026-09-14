# Guia para habilitar o reconhecimento de voiceprint (声纹识别)

Este tutorial tem 3 partes
- 1. Como implantar o serviço de reconhecimento de voiceprint
- 2. Na implantação completa (todos os módulos), como configurar a interface de voiceprint
- 3. Na implantação mínima, como configurar o reconhecimento de voiceprint

# 1. Como implantar o serviço de reconhecimento de voiceprint

## Passo 1: baixar o código-fonte do projeto de voiceprint

Abra no navegador o [endereço do projeto de voiceprint](https://github.com/xinnan-tech/voiceprint-api)

Com a página aberta, localize o botão verde escrito `Code`, clique nele e você verá o botão `Download ZIP`.

Clique nele para baixar o pacote do código-fonte do projeto. Depois de baixar no seu computador, extraia o conteúdo; a pasta provavelmente se chamará `voiceprint-api-main`
e você deve renomeá-la para `voiceprint-api`.

## Passo 2: criar o banco de dados e as tabelas

O reconhecimento de voiceprint depende do banco de dados `mysql`. Se você já implantou o Console de Gerenciamento (智控台) antes, então o `mysql` já está instalado e você pode compartilhá-lo.

Teste no host o comando `telnet` para ver se a porta `3306` do `mysql` responde normalmente:
```
telnet 127.0.0.1 3306
```
Se a porta 3306 responde, ignore o conteúdo abaixo e vá direto para o passo 3.

Se não responde, relembre como o seu `mysql` foi instalado.

Se o mysql foi instalado manualmente com um pacote de instalação, significa que ele está com isolamento de rede. Nesse caso, resolva primeiro o acesso à porta `3306` do `mysql`.

Se o `mysql` foi instalado pelo `docker-compose_all.yml` deste projeto, localize o arquivo `docker-compose_all.yml` que você usou para criar o banco e altere o seguinte

Antes da alteração
```
  xiaozhi-esp32-server-db:
    ...
    networks:
      - default
    expose:
      - "3306:3306"
```

Depois da alteração
```
  xiaozhi-esp32-server-db:
    ...
    networks:
      - default
    ports:
      - "3306:3306"
```

Atenção: troque o `expose` por `ports` no serviço `xiaozhi-esp32-server-db`. Depois da alteração, é preciso reiniciar. Comando para reiniciar o mysql:

```
# Entre na pasta onde está o seu docker-compose_all.yml; no meu caso é xiaozhi-server
cd xiaozhi-server
docker compose -f docker-compose_all.yml down
docker compose -f docker-compose.yml up -d
```

Depois de subir, use o comando `telnet` no host de novo para ver se a porta `3306` do `mysql` responde.
```
telnet 127.0.0.1 3306
```
Em condições normais, agora o acesso funciona.

## Passo 3: criar o banco de dados e as tabelas
Se o seu host acessa o banco mysql normalmente, crie no mysql um banco chamado `voiceprint_db` e a tabela `voiceprints`.

```
CREATE DATABASE voiceprint_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE voiceprint_db;

CREATE TABLE voiceprints (
    id INT AUTO_INCREMENT PRIMARY KEY,
    speaker_id VARCHAR(255) NOT NULL UNIQUE,
    feature_vector LONGBLOB NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_speaker_id (speaker_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

## Passo 4: configurar a conexão com o banco de dados

Entre na pasta `voiceprint-api` e crie uma pasta chamada `data`.

Copie o `voiceprint.yaml` da raiz do `voiceprint-api` para a pasta `data` e renomeie para `.voiceprint.yaml`

Em seguida, configure com atenção a conexão de banco de dados no `.voiceprint.yaml`.

```
mysql:
  host: "127.0.0.1"
  port: 3306
  user: "root"
  password: "your_password"
  database: "voiceprint_db"
```

Atenção! Como o seu serviço de voiceprint é implantado via Docker, o `host` deve ser preenchido com o `IP de rede local da máquina onde está o mysql`.

Atenção! Como o seu serviço de voiceprint é implantado via Docker, o `host` deve ser preenchido com o `IP de rede local da máquina onde está o mysql`.

Atenção! Como o seu serviço de voiceprint é implantado via Docker, o `host` deve ser preenchido com o `IP de rede local da máquina onde está o mysql`.

## Passo 5: iniciar o programa
Este projeto é bem simples; recomendamos executá-lo com Docker. Se você preferir não usar Docker, siga [esta página](https://github.com/xinnan-tech/voiceprint-api/blob/main/README.md) para rodar a partir do código-fonte. Abaixo, o modo de execução via Docker

```
# Entre na raiz do código-fonte do projeto
cd voiceprint-api

# Limpar caches
docker compose -f docker-compose.yml down
docker stop voiceprint-api
docker rm voiceprint-api
docker rmi ghcr.nju.edu.cn/xinnan-tech/voiceprint-api:latest

# Iniciar o contêiner docker
docker compose -f docker-compose.yml up -d
# Ver os logs
docker logs -f voiceprint-api
```

Nesse momento, o log mostrará algo como:
```
250711 INFO-🚀 开始: 生产环境服务启动（Uvicorn），监听地址: 0.0.0.0:8005
250711 INFO-============================================================
250711 INFO-声纹接口地址: http://127.0.0.1:8005/voiceprint/health?key=abcd
250711 INFO-============================================================
```

Copie o endereço da API de voiceprint:

Como você está com implantação via Docker, jamais use o endereço acima diretamente!

Como você está com implantação via Docker, jamais use o endereço acima diretamente!

Como você está com implantação via Docker, jamais use o endereço acima diretamente!

Primeiro copie o endereço para um rascunho e descubra qual é o IP de rede local do seu computador; por exemplo, se o IP de rede local do meu computador é `192.168.1.25`, então
o meu endereço original
```
http://127.0.0.1:8005/voiceprint/health?key=abcd

```
deve ser alterado para
```
http://192.168.1.25:8005/voiceprint/health?key=abcd
```

Depois de ajustar, acesse o `endereço da API de voiceprint` direto no navegador. Se aparecer no navegador algo como o código abaixo, deu certo.
```
{"total_voiceprints":0,"status":"healthy"}
```

Guarde o `endereço da API de voiceprint` já ajustado; o próximo passo vai usá-lo.

# 2. Na implantação completa (todos os módulos), como configurar o reconhecimento de voiceprint

## Passo 1: configurar a interface
Primeiro, habilite o reconhecimento de voiceprint. No Console de Gerenciamento, clique em `Dicionário de Parâmetros` (参数字典) no topo e, no menu suspenso, abra a página `Configuração de Funções do Sistema` (系统功能配置). Nessa página, marque `Reconhecimento de Voiceprint` e clique em `Salvar Configuração`; o botão `Reconhecimento de Voiceprint` aparecerá no cartão de criação de novos Agentes (智能体).

Se você usa a implantação completa, entre no Console de Gerenciamento com uma conta de administrador, clique em `Dicionário de Parâmetros` no topo e selecione a função `Gerenciamento de Parâmetros` (参数管理).

Depois, busque o parâmetro `server.voice_print`; neste momento, o valor dele deve estar `null`.
Clique no botão editar e cole o `endereço da API de voiceprint` obtido no passo anterior em `Valor do Parâmetro` (参数值). Depois salve.

Se salvar com sucesso, está tudo bem: vá ao Agente conferir o resultado. Se não salvar, o Console de Gerenciamento não consegue acessar o serviço de voiceprint — muito provavelmente é um firewall de rede ou um IP de rede local incorreto.

## Passo 2: definir o modo de memória do Agente

Na configuração de papel do seu Agente, defina a memória como `Memória de Curto Prazo Local` (本地短期记忆) e ative obrigatoriamente `Enviar texto + áudio` (上报文字+语音).

## Passo 3: conversar com o seu Agente

Ligue o seu dispositivo e converse com ele em velocidade e tom de voz normais.

## Passo 4: registrar as voiceprints

No Console de Gerenciamento, na página `Gerenciamento de Agentes` (智能体管理), o painel do Agente tem um botão `Reconhecimento de Voiceprint`; clique nele. Na parte de baixo há um botão `Adicionar`. Assim você registra a voiceprint do que determinada pessoa disse.
No diálogo que se abre, recomendamos preencher o atributo `Descrição`: pode ser a profissão, a personalidade e os hobbies da pessoa, ajudando o Agente a analisar e conhecer quem está falando.

## Passo 3: conversar com o seu Agente

Ligue o seu dispositivo e pergunte a ele: "você sabe quem eu sou?". Se ele souber responder, o reconhecimento de voiceprint está funcionando normalmente.

# 3. Na implantação mínima, como configurar o reconhecimento de voiceprint

## Passo 1: configurar a interface
Abra o arquivo `xiaozhi-server/data/.config.yaml` (crie-o se não existir) e adicione/ajuste o seguinte:

```
# Configuração do reconhecimento de voiceprint
voiceprint:
  # Endereço da API de voiceprint
  url: seu-endereco-de-api-de-voiceprint
  # Configuração dos falantes: speaker_id,nome,descrição
  speakers:
    - "test1,张三,张三是一个程序员"
    - "test2,李四,李四是一个产品经理"
    - "test3,王五,王五是一个设计师"
```

Cole o `endereço da API de voiceprint` obtido no passo anterior em `url`. Depois salve.

Adicione entradas em `speakers` conforme a sua necessidade. Atenção ao parâmetro `speaker_id`: ele será usado para registrar as voiceprints depois.

## Passo 2: registrar uma voiceprint
Se o serviço de voiceprint já estiver no ar, acesse `http://localhost:8005/voiceprint/docs` no navegador local para ver a documentação da API; aqui explicamos apenas como usar a API de registro de voiceprint.

O endpoint da API de registro de voiceprint é `http://localhost:8005/voiceprint/register`, com método POST.

O cabeçalho da requisição precisa de autenticação Bearer Token; o token é a parte após `?key=` do `endereço da API de voiceprint`. Por exemplo, se o meu endereço de registro de voiceprint é `http://127.0.0.1:8005/voiceprint/health?key=abcd`, então o meu token é `abcd`.

O corpo da requisição contém o ID do falante (speaker_id) e o arquivo de áudio WAV (file). Exemplo de requisição:

```
curl -X POST \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -F "speaker_id=your_speaker_id_here" \
  -F "file=@/path/to/your/file" \
  http://localhost:8005/voiceprint/register
```

 Aqui, `file` é o arquivo de áudio da fala do falante a ser registrado, e o `speaker_id` deve ser igual ao `speaker_id` configurado na interface no Passo 1. Por exemplo, se eu preciso registrar a voiceprint de Zhang San (张三) e no `.config.yaml` o `speaker_id` dele é `test1`, então, ao registrar a voiceprint de Zhang San, o `speaker_id` no corpo da requisição é `test1` e o `file` é o arquivo de áudio de uma fala de Zhang San.

 ## Passo 3: iniciar os serviços

Inicie o servidor do xiaozhi e o serviço de voiceprint, e tudo funcionará normalmente.
