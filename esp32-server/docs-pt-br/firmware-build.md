# Compilação do firmware ESP32

## Passo 1: prepare seu endereço OTA

Se você usa a versão 0.3.12 deste projeto, tanto na implantação de servidor simples quanto na implantação completa (todos os módulos), haverá um endereço OTA.

Como a forma de definir o endereço OTA difere entre a implantação de servidor simples e a implantação completa, escolha abaixo o caso específico:

### Se você usa a implantação de servidor simples
Agora, abra o seu endereço OTA no navegador; por exemplo, o meu endereço OTA é
```
http://192.168.1.25:8003/xiaozhi/ota/
```
Se exibir “A interface OTA está funcionando normalmente, o endereço websocket enviado ao dispositivo é: ws://xxx:8000/xiaozhi/v1/

Você pode iniciar o módulo `digital-human` e abrir o `index.html` para testar se consegue conectar ao endereço websocket exibido pela página OTA.

Se não conseguir acessar, você precisa alterar o endereço `server.websocket` no arquivo de configuração `.config.yaml`, reiniciar e testar novamente, até que o `index.html` acesse normalmente.

Se funcionou, siga para o passo 2

### Se você usa a implantação completa (todos os módulos)
Agora, abra o seu endereço OTA no navegador; por exemplo, o meu endereço OTA é
```
http://192.168.1.25:8002/xiaozhi/ota/
```

Se exibir “A interface OTA está funcionando normalmente, quantidade de clusters websocket: X”, siga para o passo 2.

Se exibir “A interface OTA não está funcionando normalmente”, provavelmente você ainda não configurou o endereço `Websocket` no Console de Gerenciamento (智控台). Então:

- 1. Faça login no Console de Gerenciamento com a conta de superadministrador

- 2. No menu superior, clique em `Gerenciamento de Parâmetros` (参数管理)

- 3. Na lista, localize o item `server.websocket` e informe o seu endereço `Websocket`. Por exemplo, o meu é

```
ws://192.168.1.25:8000/xiaozhi/v1/
```

Depois de configurar, atualize no navegador o endereço da sua interface OTA e veja se voltou ao normal. Se ainda não estiver normal, confirme novamente se o websocket iniciou corretamente e se o endereço Websocket foi configurado.

## Passo 2: configurar o ambiente
Primeiro configure o ambiente do projeto seguindo este tutorial: [《Montagem do ambiente de desenvolvimento ESP IDF 5.3.2 no Windows e compilação do xiaozhi》](https://icnynnzcwou8.feishu.cn/wiki/JEYDwTTALi5s2zkGlFGcDiRknXf)

## Passo 3: abrir o arquivo de configuração
Com o ambiente de compilação configurado, baixe o código-fonte do projeto iaozhi-esp32 do Xiaoge (虾哥),

Baixe daqui o [código-fonte do projeto xiaozhi-esp32 do Xiaoge (虾哥)](https://github.com/78/xiaozhi-esp32).

Depois de baixar, abra o arquivo `xiaozhi-esp32/main/Kconfig.projbuild`.

## Passo 4: alterar o endereço OTA

Localize o conteúdo do `default` de `OTA_URL` e troque `https://api.tenclass.net/xiaozhi/ota/`
   pelo seu próprio endereço; por exemplo, se o meu endereço de interface é `http://192.168.1.25:8002/xiaozhi/ota/`, altero o conteúdo para ele.

Antes da alteração:
```
config OTA_URL
    string "Default OTA URL"
    default "https://api.tenclass.net/xiaozhi/ota/"
    help
        The application will access this URL to check for new firmwares and server address.
```
Depois da alteração:
```
config OTA_URL
    string "Default OTA URL"
    default "http://192.168.1.25:8002/xiaozhi/ota/"
    help
        The application will access this URL to check for new firmwares and server address.
```

## Passo 4: definir os parâmetros de compilação

Defina os parâmetros de compilação

```
# Pelo terminal, entre no diretório raiz do xiaozhi-esp32
cd xiaozhi-esp32
# Como exemplo, a placa que uso é esp32s3, então defino o alvo de compilação como esp32s3; se a sua placa for de outro modelo, substitua pelo modelo correspondente
idf.py set-target esp32s3
# Abra o menu de configuração
idf.py menuconfig
```

Dentro do menu de configuração, entre em `Xiaozhi Assistant` e ajuste `BOARD_TYPE` para o modelo específico da sua placa.
Salve e saia, voltando ao terminal.

## Passo 5: compilar o firmware

```
idf.py build
```

## Passo 6: empacotar o binário do firmware

```
cd scripts
python release.py
```

Após a execução do comando de empacotamento acima, o arquivo de firmware `merged-binary.bin` será gerado no diretório `build`, na raiz do projeto.
Esse `merged-binary.bin` é o arquivo de firmware a ser gravado no hardware.

Observação: se, ao executar o segundo comando, aparecer um erro relacionado a “zip”, ignore esse erro; desde que o arquivo de firmware `merged-binary.bin` seja gerado no diretório `build`
, o impacto é pequeno; continue.

## Passo 7: gravar o firmware
   Conecte o dispositivo ESP32 ao computador, use o navegador Chrome e abra o seguinte endereço

```
https://espressif.github.io/esp-launchpad/
```

Abra este tutorial: [Ferramenta de gravação / gravação de firmware pelo navegador web (sem ambiente IDF)](https://ccnphfhqs21z.feishu.cn/wiki/Zpz4wXBtdimBrLk25WdcXzxcnNS).
Vá até: `Método 2: gravação do firmware pelo navegador web ESP-Launchpad (方式二：ESP-Launchpad 浏览器WEB端烧录)`, comece em `3. Gravar firmware / baixar para a placa de desenvolvimento` e siga o tutorial.

Depois de gravar com sucesso e conectar à rede, desperte o xiaozhi pela wake word e observe as mensagens do console no lado do server.

## Perguntas frequentes
Seguem algumas perguntas frequentes, para referência:

[1. Por que, quando eu falo, o xiaozhi reconhece muito coreano, japonês e inglês](./FAQ.md)

[2. Por que aparece “TTS 任务出错 文件不存在” (erro na tarefa de TTS: arquivo inexistente)?](./FAQ.md)

[3. O TTS falha com frequência, sempre dá timeout](./FAQ.md)

[4. Com WiFi consigo conectar ao meu servidor próprio, mas no modo 4G não conecta](./FAQ.md)

[5. Como melhorar a velocidade de resposta da conversa do xiaozhi?](./FAQ.md)

[6. Eu falo devagar e, nas pausas, o xiaozhi sempre me interrompe](./FAQ.md)

[7. Quero controlar luzes, ar-condicionado, ligar/desligar aparelhos à distância e outras ações pelo xiaozhi](./FAQ.md)
