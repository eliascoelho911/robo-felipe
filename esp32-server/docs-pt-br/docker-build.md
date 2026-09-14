# Como compilar a imagem Docker localmente

Atualmente este projeto já usa o recurso de `compilação automática de imagem Docker` do `GitHub`. Se você usa as imagens publicadas pelo projeto e não tem a necessidade de compilar a sua própria imagem, pode ignorar este documento.

Se você modificou o código-fonte e deseja implantar e executar com `docker`, siga os passos abaixo:

## 1. Preparação do ambiente

Instale o Docker:
```bash
sudo apt-get install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

## 2. Compilar a imagem

Depois de alterar o código, quando precisar compilar uma nova imagem, siga estes passos:

Tenha em mãos o `seu-usuario` e a `nova-versao`.
- O `seu-usuario` é o nome de usuário com o qual você se registrou no `docker hub`, por exemplo `xiaozhi`. Claro que, se você não precisar publicar no `docker hub`, pode defini-lo livremente.
- A `nova-versao` é a versão da imagem que você está compilando, por exemplo `1.2.3`; você pode personalizá-la como quiser ou usar o formato de data (por exemplo `20260609`) — o objetivo é diferenciá-la do número de versão em execução atual e, de quebra, facilitar lembrar depois quando ela foi construída. Não use o mesmo número de versão que está em execução na sua máquina neste momento.

Entre no diretório raiz do projeto `xiaozhi-esp32-server` e compile as duas imagens, server e web:

```bash
cd raiz-do-projeto

# Compilar a imagem do server
docker build -f Dockerfile-server -t seu-usuario/xiaozhi-esp32-server:nova-versao .

# Compilar a imagem do web
docker build -f Dockerfile-web -t seu-usuario/xiaozhi-esp32-server-web:nova-versao .

```

## 3. Modificar a configuração do docker-compose

```bash
cd main/xiaozhi-server
```

Edite o arquivo `docker-compose_all.yml` e substitua a versão das imagens pela versão que você acabou de compilar:

```yaml
services:
  xiaozhi-esp32-server:
    image: seu-usuario/xiaozhi-esp32-server:nova-versao   # altere para o endereço da sua imagem
    ...

  xiaozhi-esp32-server-web:
    image: seu-usuario/xiaozhi-esp32-server-web:nova-versao   # altere para o endereço da sua imagem
    ...
```

## 4. Reiniciar os serviços

```bash
# Parar os contêineres antigos
docker compose -f docker-compose_all.yml down

# Iniciar os contêineres novos
docker compose -f docker-compose_all.yml up -d
```

## 5. Verificação

Confira os logs para verificar se os serviços iniciaram normalmente:

```bash
# Ver os logs do server
docker logs -f -n 50 xiaozhi-esp32-server

# Ver os logs do web
docker logs -f -n 50 xiaozhi-esp32-server-web
```
