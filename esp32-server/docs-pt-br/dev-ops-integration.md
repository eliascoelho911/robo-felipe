# Método de Atualização Automática para Implantação de Todos os Módulos a Partir do Código-Fonte

Este tutorial destina-se a facilitar a vida dos entusiastas que fazem a implantação de todos os módulos a partir do código-fonte: como usar comandos automáticos para baixar o código-fonte automaticamente, compilar automaticamente e iniciar automaticamente os serviços nas portas. Assim se alcança a máxima eficiência na atualização do sistema.

A plataforma de teste deste projeto, `https://2662r3426b.vicp.fun`, usa este método desde a sua abertura, com bons resultados.

Como tutorial de referência, veja o vídeo publicado pelo criador `毕乐labs` da Bilibili (B站): [Tutorial passo a passo de atualização automática do servidor open source xiaozhi-server e de configuração do endpoint MCP da versão mais recente](https://www.bilibili.com/video/BV15H37zHE7Q)

# Condições para começar
- O seu computador/servidor usa sistema operacional linux
- Você já colocou todo o fluxo em funcionamento
- Você gosta de acompanhar os recursos mais recentes, mas acha a implantação manual de cada vez um pouco trabalhosa e gostaria de um método de atualização automática

A segunda condição precisa ser atendida, porque alguns arquivos envolvidos neste tutorial — ambiente JDK, ambiente Node.js, ambiente Conda etc. — só existem depois que você coloca todo o fluxo em funcionamento; se você não colocou, quando eu mencionar determinado arquivo, talvez você não entenda do que se trata.

# O que este tutorial proporciona
- Resolve o problema de não conseguir baixar da rede nacional (China) o código-fonte mais recente do projeto
- Baixa o código e compila os arquivos do front-end automaticamente
- Baixa o código e compila os arquivos java automaticamente, encerra automaticamente o processo na porta 8002 e inicia automaticamente a porta 8002
- Baixa o código python automaticamente, encerra automaticamente o processo na porta 8000 e inicia automaticamente a porta 8000

# Passo 1: escolha o diretório do seu projeto

Por exemplo, o diretório do projeto que eu defini foi o abaixo; é um diretório novo e vazio — se você não quiser ter problemas, pode fazer como eu
```
/home/system/xiaozhi
```

# Passo 2: clone este projeto
Neste momento, primeiro execute o primeiro comando para baixar o código-fonte; este comando funciona para servidores e computadores na rede nacional (China), sem necessidade de VPN

```
cd /home/system/xiaozhi
git clone https://ghproxy.net/https://github.com/xinnan-tech/xiaozhi-esp32-server.git
```

Depois de executar, aparecerá uma pasta `xiaozhi-esp32-server` no diretório do seu projeto — esse é o código-fonte do projeto

# Passo 3: copie os arquivos básicos

Se você já colocou todo o fluxo em funcionamento antes, não será estranho para você o arquivo de modelo do funasr `xiaozhi-server/models/SenseVoiceSmall/model.pt` e o seu arquivo de configuração privado `xiaozhi-server/data/.config.yaml`.

Agora você precisa copiar o arquivo `model.pt` para o novo diretório; você pode fazer assim
```
# Criar os diretórios necessários
mkdir -p /home/system/xiaozhi/xiaozhi-esp32-server/main/xiaozhi-server/data/

cp /caminho/completo/do/seu/.config.yaml /home/system/xiaozhi/xiaozhi-esp32-server/main/xiaozhi-server/data/.config.yaml
cp /caminho/completo/do/seu/model.pt /home/system/xiaozhi/xiaozhi-esp32-server/main/xiaozhi-server/models/SenseVoiceSmall/model.pt
```

# Passo 4: crie os três arquivos de compilação automática

## 4.1 Compilar automaticamente o módulo mananger-web
No diretório `/home/system/xiaozhi/`, crie um arquivo chamado `update_8001.sh` com o seguinte conteúdo

```
cd /home/system/xiaozhi/xiaozhi-esp32-server
git fetch --all
git reset --hard
git pull origin main


cd /home/system/xiaozhi/xiaozhi-esp32-server/main/manager-web
npm install
npm run build
rm -rf /home/system/xiaozhi/manager-web
mv /home/system/xiaozhi/xiaozhi-esp32-server/main/manager-web/dist /home/system/xiaozhi/manager-web
```

Depois de salvar, execute o comando para dar permissão
```
chmod 777 update_8001.sh
```
Depois de executar, continue adiante

## 4.2 Compilar e executar automaticamente o módulo manager-api
No diretório `/home/system/xiaozhi/`, crie um arquivo chamado `update_8002.sh` com o seguinte conteúdo

```
cd /home/system/xiaozhi/xiaozhi-esp32-server
git pull origin main


cd /home/system/xiaozhi/xiaozhi-esp32-server/main/manager-api
rm -rf target
mvn clean package -Dmaven.test.skip=true
cd /home/system/xiaozhi/

# Localizar o PID do processo que ocupa a porta 8002
PID=$(sudo netstat -tulnp | grep 8002 | awk '{print $7}' | cut -d'/' -f1)

rm -rf /home/system/xiaozhi/xiaozhi-esp32-api.jar
mv /home/system/xiaozhi/xiaozhi-esp32-server/main/manager-api/target/xiaozhi-esp32-api.jar /home/system/xiaozhi/xiaozhi-esp32-api.jar

# Verificar se o PID foi encontrado
if [ -z "$PID" ]; then
  echo "没有找到占用8002端口的进程"
else
  echo "找到占用8002端口的进程，进程号为: $PID"
  # Encerrar o processo
  kill -9 $PID
  kill -9 $PID
  echo "已杀掉进程 $PID"
fi

nohup java -jar xiaozhi-esp32-api.jar --spring.profiles.active=dev &

tail tail -f nohup.out
```

Depois de salvar, execute o comando para dar permissão
```
chmod 777 update_8002.sh
```
Depois de executar, continue adiante

## 4.3 Compilar e executar automaticamente o projeto Python
No diretório `/home/system/xiaozhi/`, crie um arquivo chamado `update_8000.sh` com o seguinte conteúdo

```
cd /home/system/xiaozhi/xiaozhi-esp32-server
git pull origin main

# Localizar o PID do processo que ocupa a porta 8000
PID=$(sudo netstat -tulnp | grep 8000 | awk '{print $7}' | cut -d'/' -f1)

# Verificar se o PID foi encontrado
if [ -z "$PID" ]; then
  echo "没有找到占用8000端口的进程"
else
  echo "找到占用8000端口的进程，进程号为: $PID"
  # Encerrar o processo
  kill -9 $PID
  kill -9 $PID
  echo "已杀掉进程 $PID"
fi
cd main/xiaozhi-server
# Inicializar o ambiente conda
source ~/.bashrc
conda activate xiaozhi-esp32-server
pip install -r requirements.txt
nohup python app.py >/dev/null &
tail -f /home/system/xiaozhi/xiaozhi-esp32-server/main/xiaozhi-server/tmp/server.log
```

Depois de salvar, execute o comando para dar permissão
```
chmod 777 update_8000.sh
```
Depois de executar, continue adiante

# Atualização do dia a dia

Com os scripts acima criados, para a atualização do dia a dia basta executar os comandos abaixo, um após o outro, para atualizar e iniciar automaticamente

```
cd /home/system/xiaozhi
# Atualizar e iniciar o programa Java
./update_8001.sh
# Atualizar o programa web
./update_8002.sh
# Atualizar e iniciar o programa python
./update_8000.sh


# Mais tarde, para ver o log do java, execute o comando abaixo
tail -f nohup.out
# Mais tarde, para ver o log do python, execute o comando abaixo
tail -f /home/system/xiaozhi/xiaozhi-esp32-server/main/xiaozhi-server/tmp/server.log
```

# Observações
A plataforma de teste `https://2662r3426b.vicp.fun` usa nginx como proxy reverso. A configuração detalhada do nginx.conf pode ser [consultada aqui](https://github.com/xinnan-tech/xiaozhi-esp32-server/issues/791)

## Perguntas frequentes

### 1. Por que não vejo a porta 8001?
Resposta: a 8001 é a porta usada no ambiente de desenvolvimento para rodar o front-end. Se você está implantando em servidor, não é recomendável usar `npm run serve` para subir o front-end na porta 8001; em vez disso, compile em arquivos html como neste tutorial e use o nginx para gerenciar o acesso.

### 2. A cada atualização é preciso executar manualmente os comandos SQL?
Resposta: não, porque o projeto usa o **Liquibase** para gerenciar as versões do banco de dados e executa automaticamente os novos scripts sql.
