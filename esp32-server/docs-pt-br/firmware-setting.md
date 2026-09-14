# Configurar um servidor personalizado sobre o firmware pré-compilado pelo Xiaoge (虾哥)

## Passo 1 Confirmar a versão
Grave o [firmware versão 1.6.1 ou superior](https://github.com/78/xiaozhi-esp32/releases) já compilado pelo Xiaoge

## Passo 2 Preparar o seu endereço OTA
Se você seguiu o tutorial e fez a implantação de todos os módulos, já deve ter um endereço OTA.

Agora, abra o seu endereço OTA no navegador; por exemplo, o meu endereço OTA é
```
https://2662r3426b.vicp.fun/xiaozhi/ota/
```

Se aparecer "Endpoint OTA operando normalmente, quantidade de clusters websocket: X", siga em frente.

Se aparecer "Endpoint OTA não está operando normalmente", provavelmente você ainda não configurou o endereço `Websocket` no Console de Gerenciamento (智控台). Nesse caso:

- 1. Entre no Console de Gerenciamento como superadministrador

- 2. No menu superior, clique em `Gerenciamento de Parâmetros` (参数管理)

- 3. Na lista, localize o item `server.websocket` e insira o seu endereço `Websocket`. Por exemplo, o meu é

```
wss://2662r3426b.vicp.fun/xiaozhi/v1/
```

Depois de configurar, atualize no navegador o endereço do seu endpoint OTA e veja se voltou ao normal. Se ainda não estiver normal, confirme novamente se o Websocket iniciou corretamente e se o endereço Websocket foi configurado.

## Passo 3 Entrar no modo de configuração de rede (配网模式)
Entre no modo de configuração de rede do aparelho; no topo da página, clique em "Opções Avançadas" (高级选项), insira ali o endereço `ota` do seu servidor e clique em salvar. Reinicie o dispositivo
![Consulte - configuração do endereço OTA](../upstream/docs/images/firmware-setting-ota.png)

## Passo 4 Acordar o xiaozhi e ver a saída dos logs

Acorde o xiaozhi e veja se os logs estão saindo normalmente.


## Perguntas frequentes
Abaixo, algumas perguntas frequentes para referência:

[1. Por que o xiaozhi reconhece minha fala com muitos caracteres em coreano, japonês e inglês](./FAQ.md)

[2. Por que aparece "Erro na tarefa de TTS: arquivo não existe"?](./FAQ.md)

[3. O TTS falha com frequência e frequentemente expira (timeout)](./FAQ.md)

[4. Consigo conectar ao servidor próprio via Wi-Fi, mas no modo 4G não conecta](./FAQ.md)

[5. Como melhorar a velocidade de resposta das conversas do xiaozhi?](./FAQ.md)

[6. Falo devagar e, nas minhas pausas, o xiaozhi sempre me interrompe](./FAQ.md)

[7. Quero controlar pelo xiaozhi luzes, ar-condicionado, ligar/desligar equipamentos à distância e outras ações](./FAQ.md)
