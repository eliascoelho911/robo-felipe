# Guia de integração do ragflow

Este tutorial tem basicamente duas partes

- 1. Como implantar o ragflow
- 2. Como configurar a interface do ragflow no Console de Gerenciamento (智控台)

Se você conhece bem o ragflow e já o tem implantado, pode pular a primeira parte e ir direto para a segunda. Mas se você quer orientação passo a passo para implantar o ragflow de modo a compartilhar com o `xiaozhi-esp32-server` os serviços base `mysql` e `redis` — reduzindo o custo de recursos —, comece pela primeira parte.

# Parte 1: como implantar o ragflow
## Passo 1: verificar se mysql e redis estão acessíveis

O ragflow depende do banco de dados `mysql`. Se você já implantou o Console de Gerenciamento antes, então o `mysql` já está instalado e você pode compartilhá-lo.

Teste no host o comando `telnet` para ver se a porta `3306` do `mysql` responde normalmente:
``` shell
telnet 127.0.0.1 3306

telnet 127.0.0.1 6379
```
Se as portas `3306` e `6379` respondem, ignore o conteúdo abaixo e vá direto para o passo 2.

Se não respondem, relembre como o seu `mysql` foi instalado.

Se o mysql foi instalado manualmente com um pacote de instalação, significa que ele está com isolamento de rede. Nesse caso, resolva primeiro o acesso à porta `3306` do `mysql`.

Se o `mysql` foi instalado pelo `docker-compose_all.yml` deste projeto, localize o arquivo `docker-compose_all.yml` que você usou para criar o banco e altere o seguinte

Antes da alteração
``` yaml
  xiaozhi-esp32-server-db:
    ...
    networks:
      - default
    expose:
      - "3306:3306"
  xiaozhi-esp32-server-redis:
    ...
    expose:
      - 6379
```

Depois da alteração
``` yaml
  xiaozhi-esp32-server-db:
    ...
    networks:
      - default
    ports:
      - "3306:3306"
  xiaozhi-esp32-server-redis:
    ...
    ports:
      - "6379:6379"
```

Atenção: troque o `expose` por `ports` nos serviços `xiaozhi-esp32-server-db` e `xiaozhi-esp32-server-redis`. Depois da alteração, é preciso reiniciar. Comando para reiniciar o mysql:

``` shell
# Entre na pasta onde está o seu docker-compose_all.yml; no meu caso é xiaozhi-server
cd xiaozhi-server
docker compose -f docker-compose_all.yml down
docker compose -f docker-compose.yml up -d
```

Depois de subir, use o comando `telnet` no host de novo para ver se a porta `3306` do `mysql` responde.
``` shell
telnet 127.0.0.1 3306

telnet 127.0.0.1 6379
```
Em condições normais, agora o acesso funciona.

## Passo 2: criar o banco de dados e as tabelas
Se o seu host acessa o banco mysql normalmente, crie no mysql um banco chamado `rag_flow` e o usuário `rag_flow`, com a senha `infini_rag_flow`.

``` sql
-- Criar o banco de dados
CREATE DATABASE IF NOT EXISTS rag_flow CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Criar o usuário e conceder permissões
CREATE USER IF NOT EXISTS 'rag_flow'@'%' IDENTIFIED BY 'infini_rag_flow';
GRANT ALL PRIVILEGES ON rag_flow.* TO 'rag_flow'@'%';

-- Recarregar as permissões
FLUSH PRIVILEGES;
```

## Passo 3: baixar o projeto ragflow

Escolha no seu computador uma pasta para guardar o projeto ragflow. Por exemplo, eu usei a pasta `/home/system/xiaozhi`.

Você pode usar o comando `git` para baixar o projeto ragflow nessa pasta; este tutorial usa a versão `v0.22.0` para instalação e implantação.
```
git clone https://ghfast.top/https://github.com/infiniflow/ragflow.git
cd ragflow
git checkout v0.22.0
```
Depois do download, entre na pasta `docker`.
``` shell
cd docker
```
Edite o arquivo `docker-compose.yml` da pasta `ragflow/docker` e remova a configuração `depends_on` dos serviços `ragflow-cpu` e `ragflow-gpu`, para eliminar a dependência do serviço `ragflow-cpu` em relação ao `mysql`.

Antes da alteração:
``` yaml
  ragflow-cpu:
    depends_on:
      mysql:
        condition: service_healthy
    profiles:
      - cpu
  ...
  ragflow-gpu:
    depends_on:
      mysql:
        condition: service_healthy
    profiles:
      - gpu
```
Depois da alteração:
``` yaml
  ragflow-cpu:
    profiles:
      - cpu
  ...
  ragflow-gpu:
    profiles:
      - gpu
```

Em seguida, edite o arquivo `docker-compose-base.yml` da pasta `ragflow/docker` e remova as configurações de `mysql` e `redis`.

Por exemplo, antes de remover:
``` yaml
services:
  minio:
    image: quay.io/minio/minio:RELEASE.2025-06-13T11-33-47Z
    ...
  mysql:
    image: mysql:8.0
    ...
  redis:
    image: redis:6.2-alpine
    ...
```

Depois de remover
``` yaml
services:
  minio:
    image: quay.io/minio/minio:RELEASE.2025-06-13T11-33-47Z
    ...
```
## Passo 4: ajustar as variáveis de ambiente

Edite o arquivo `.env` da pasta `ragflow/docker`, localize as configurações abaixo — busque e altere uma a uma! Busque e altere uma a uma!

Nesta alteração do `.env`, 60% das pessoas esquecem a configuração `MYSQL_USER` e o ragflow não sobe; por isso, o aviso é repetido três vezes:

Primeiro aviso: se o seu arquivo `.env` não tiver a configuração `MYSQL_USER`, acrescente esse item no arquivo!

Segundo aviso: se o seu arquivo `.env` não tiver a configuração `MYSQL_USER`, acrescente esse item no arquivo!

Terceiro aviso: se o seu arquivo `.env` não tiver a configuração `MYSQL_USER`, acrescente esse item no arquivo!

``` env
# Configuração de portas
SVR_WEB_HTTP_PORT=8008           # porta HTTP
SVR_WEB_HTTPS_PORT=8009          # porta HTTPS
# Configuração do MySQL - ajuste com as informações do seu MySQL local
MYSQL_HOST=host.docker.internal  # host.docker.internal permite que o contêiner acesse os serviços da máquina host
MYSQL_PORT=3306                  # porta do MySQL local
MYSQL_USER=rag_flow              # usuário criado acima; se este item não existir, acrescente-o
MYSQL_PASSWORD=infini_rag_flow   # senha definida acima
MYSQL_DBNAME=rag_flow            # nome do banco de dados

# Configuração do Redis - ajuste com as informações do seu Redis local
REDIS_HOST=host.docker.internal  # host.docker.internal permite que o contêiner acesse os serviços da máquina host
REDIS_PORT=6379                  # porta do Redis local
REDIS_PASSWORD=                  # se o seu Redis não tem senha, deixe assim; senão, informe a senha
```

Atenção: se o seu Redis não tem senha, você também precisa editar o `service_conf.yaml.template` na pasta `ragflow/docker`, substituindo `infini_rag_flow` por uma string vazia.

Antes da alteração
``` shell
redis:
  db: 1
  password: '${REDIS_PASSWORD:-infini_rag_flow}'
  host: '${REDIS_HOST:-redis}:6379'
```
Depois da alteração
``` shell
redis:
  db: 1
  password: '${REDIS_PASSWORD:-}'
  host: '${REDIS_HOST:-redis}:6379'
```

## Passo 5: iniciar o serviço do ragflow
Execute o comando:
``` shell
docker-compose -f docker-compose.yml up -d
```
Se o comando terminar bem, use o comando `docker logs -n 20 -f docker-ragflow-cpu-1` para ver o log do serviço `docker-ragflow-cpu-1`.

Se não houver erros no log, o serviço do ragflow subiu com sucesso.

# Passo 5: registrar a conta
Você pode acessar `http://127.0.0.1:8008` no navegador e clicar em `Sign Up` para criar uma conta.

Depois de registrar, você pode clicar em `Sign In` para entrar no serviço do ragflow. Se quiser desativar o registro de contas do ragflow, para que outras pessoas não criem conta, defina a opção `REGISTER_ENABLED` como `0` no arquivo `.env` da pasta `ragflow/docker`.

``` dotenv
REGISTER_ENABLED=0
```
Depois da alteração, reinicie o serviço do ragflow.
``` shell
docker-compose -f docker-compose.yml down
docker-compose -f docker-compose.yml up -d
```

# Passo 6: configurar os modelos do serviço ragflow
Você pode acessar `http://127.0.0.1:8008` no navegador e clicar em `Sign In` para entrar no serviço do ragflow. Clique no `avatar` no canto superior direito para abrir a página de configurações.
Primeiro, na navegação à esquerda, clique em `Provedores de Modelos` para abrir a página de configuração de modelos. Embaixo da caixa de busca `Modelos Disponíveis` à direita, selecione `LLM`, escolha na lista o seu provedor de modelos, clique em `Adicionar` e informe a sua chave;
depois, selecione `TEXT EMBEDDING`, escolha na lista o seu provedor de modelos, clique em `Adicionar` e informe a sua chave.
Por fim, atualize a página e, na lista `Definir Modelos Padrão`, selecione LLM e Embedding com os modelos que você usa. Confirme que a sua chave tem esses serviços habilitados; por exemplo, se o meu modelo de Embedding é do provedor xxx, preciso verificar no site oficial desse provedor se o modelo exige a compra de um pacote de recursos para ser usado.


# Parte 2: configurar o serviço ragflow

# Passo 1: entrar no serviço ragflow
Você pode acessar `http://127.0.0.1:8008` no navegador e clicar em `Sign In` para entrar no serviço do ragflow.

Depois clique no `avatar` no canto superior direito para abrir a página de configurações. Na navegação à esquerda, clique na função `API` e, em seguida, no botão "API Key". Um diálogo aparece;

no diálogo, clique no botão "Create new Key" para gerar uma API Key. Copie essa `API Key`; você vai usá-la mais adiante.

# Passo 2: configurar no Console de Gerenciamento
Verifique se o seu Console de Gerenciamento está na versão `0.8.7` ou superior. Entre no Console de Gerenciamento com a conta de superadministrador.

Primeiro, habilite a função de Base de Conhecimento (知识库). Na navegação do topo, clique em `Dicionário de Parâmetros` (参数字典) e, no menu suspenso, abra a página `Configuração de Funções do Sistema` (系统功能配置). Nessa página, marque `Base de Conhecimento` e clique em `Salvar Configuração`; a função `Base de Conhecimento` aparecerá na navegação.

Na navegação do topo, clique em `Configuração de Modelos` (模型配置) e, na navegação à esquerda, clique em `Base de Conhecimento`. Na lista, localize `RAG_RAGFlow` e clique no botão `Editar`.

Em `Endereço do Serviço` (服务地址), preencha `http://ip-de-rede-do-seu-servico-ragflow:8008`; por exemplo, se o IP de rede do meu serviço ragflow é `192.168.1.100`, eu preencho `http://192.168.1.100:8008`.

Em `Chave de API` (API密钥), preencha com a `API Key` copiada antes.

Por fim, clique no botão Salvar.

# Passo 2: criar uma Base de Conhecimento
Entre no Console de Gerenciamento com a conta de superadministrador. Na navegação do topo, clique em `Base de Conhecimento`; no canto inferior esquerdo da lista, clique no botão `Adicionar` (新增). Preencha um nome e uma descrição para a base de conhecimento. Clique em Salvar.

Para melhorar a capacidade do LLM de compreender e recuperar o conteúdo da base, ao criá-la use um nome e uma descrição significativos. Por exemplo, se você quer criar uma base sobre `Apresentação da Empresa`, o nome dela pode ser `Apresentação da Empresa` e a descrição pode ser `Informações sobre a empresa, como dados básicos, serviços prestados, telefone de contato, endereço etc.`.

Depois de salvar, a base aparece na lista de bases de conhecimento. Clique no botão `Visualizar` (查看) da base recém-criada para abrir a página de detalhes.

Na página de detalhes da base, clique no botão `Adicionar` no canto inferior esquerdo para enviar documentos à base.

Após o envio, os documentos aparecem na página de detalhes da base. Nesse momento, clique no botão `Analisar` (解析) do documento para processá-lo.

Concluída a análise, você pode ver as informações dos chunks gerados. Na página de detalhes da base, clique no botão `Teste de Recuperação` (召回测试) para testar o recall/a busca da base de conhecimento.

# Passo 3: fazer o xiaozhi usar a base de conhecimento do ragflow
Entre no Console de Gerenciamento. Na navegação do topo, clique em `Agentes` (智能体), localize o agente que você quer configurar e clique no botão `Configurar Papel` (配置角色).

À esquerda de `Reconhecimento de Intenção` (意图识别), clique no botão `Editar Funções` (编辑功能); um diálogo se abre. Nele, selecione a base de conhecimento que você quer adicionar. Depois é só salvar.
