# Guia de Integração do Servidor Open Source do XiaoZhi ESP32 com o HomeAssistant

[TOC]

-----

## Introdução

Este documento guiará você sobre como integrar dispositivos ESP32 com o HomeAssistant.

## Pré-requisitos

- `HomeAssistant` já instalado e configurado
- O modelo que escolhi desta vez foi: o ChatGLM gratuito, que suporta chamada de funções (function call)

## Operações antes de começar (obrigatórias)

### 1. Obter as informações do endereço de rede do HA

Acesse o endereço de rede do seu Home Assistant; por exemplo, o endereço do meu HA é 192.168.4.7 e a porta é a padrão 8123; então abra no navegador

```
http://192.168.4.7:8123
```

> Método manual para descobrir o endereço IP do HA **(apenas quando o xiaozhi-esp32-server e o HA estiverem implantados no mesmo dispositivo de rede [por exemplo, o mesmo wi-fi])**:
>
> 1. Entre no Home Assistant (interface web).
>
> 2. Clique em **Configurações (Settings)** no canto inferior esquerdo → **Sistema (System)** → **Rede (Network)**.
>
> 3. Role até o final, na área `URL do Home Assistant (Home Assistant website)`, dentro de `Rede local (local network)`, clique no botão `olho` para ver o endereço IP em uso (como `192.168.1.10`) e a interface de rede. Clicando em `Copiar link (copy link)` você copia diretamente.
>
>    ![image-20250504051716417](../upstream/docs/images/image-ha-integration-01.png)

Ou, se você já configurou um endereço OAuth do Home Assistant de acesso direto, também pode acessar diretamente no navegador

```
http://homeassistant.local:8123
```

### 2. Acesse o `Home Assistant` para obter a chave de desenvolvedor

Acesse o `HomeAssistant`, clique no `avatar no canto inferior esquerdo -> Perfil`, alterne para a aba `Segurança`, role até o final em `Tokens de acesso de longa duração` e gere a api_key, copiando-a e guardando-a; todos os métodos a seguir precisam usar esta api key, e ela é exibida apenas uma vez (dica: você pode salvar a imagem do QR code gerado; mais tarde, basta escanear o QR code para extrair a api key novamente).

## Método 1: recurso de chamada do HA construído em conjunto pela comunidade do XiaoZhi

### Descrição da função

- Se você precisar adicionar novos dispositivos mais tarde, este método exige reiniciar manualmente o `servidor xiaozhi-esp32-server` para atualizar as informações dos dispositivos **(importante**).

- Você precisa garantir que a integração `Xiaomi Home` já esteja configurada no HomeAssistant e que os dispositivos do Mi Home (米家) tenham sido importados para o `HomeAssistant`.

- Você precisa garantir que o Console de Gerenciamento (智控台) do `xiaozhi-esp32-server` esteja funcionando normalmente.

- O meu Console de Gerenciamento do `xiaozhi-esp32-server` e o `HomeAssistant` estão implantados na mesma máquina, em outra porta, e a versão é a `0.3.10`

  ```
  http://192.168.4.7:8002
  ```


### Passos de configuração

#### 1. Acesse o `HomeAssistant` e organize a lista de dispositivos a controlar

Acesse o `HomeAssistant`, clique em `Configurações` no canto inferior esquerdo, entre em `Dispositivos e Serviços` e clique em `Entidades` no topo.

Em seguida, pesquise nas entidades o interruptor que você deseja controlar; quando os resultados aparecerem, clique em um deles na lista, e será exibida a tela do interruptor.

Na tela do interruptor, tente clicar no interruptor para ver se ele liga/desliga conforme os nossos cliques. Se for possível operá-lo, significa que ele está conectado normalmente.

Depois, no painel do interruptor, encontre o botão de configurações; ao clicar nele, você poderá ver o `identificador da entidade` deste interruptor.

Abra um bloco de notas e organize um registro neste formato:

local + vírgula em inglês + nome do dispositivo + vírgula em inglês + `identificador da entidade` + ponto e vírgula em inglês

Por exemplo, eu estou na empresa e tenho uma luminária de brinquedo cujo identificador é switch.cuco_cn_460494544_cp1_on_p_2_1; então este registro seria escrito assim

```
Empresa,luminária de brinquedo,switch.cuco_cn_460494544_cp1_on_p_2_1;
```

Claro que, no final, talvez eu precise controlar duas luminárias; o meu resultado final seria:

```
Empresa,luminária de brinquedo,switch.cuco_cn_460494544_cp1_on_p_2_1;
Empresa,luminária de mesa,switch.iot_cn_831898993_socn1_on_p_2_1;
```

Essa sequência de caracteres, que chamamos de “string da lista de dispositivos”, precisa ser guardada; será útil daqui a pouco.

#### 2. Acesse o `Console de Gerenciamento`

![image-20250504051716417](../upstream/docs/images/image-ha-integration-06.png)

Usando a conta de administrador, acesse o `Console de Gerenciamento`. Em `Gerenciamento de Agentes (智能体管理)`, encontre o seu agente e clique em `Configurar Persona (配置角色)`.

Defina o reconhecimento de intenção como `Reconhecimento de Intenção por LLM Externo (外挂的大模型意图识别)` ou `Chamada de Função Autônoma do LLM (大模型自主函数调用)`. Nesse momento, você verá à direita um `Editar Funções`. Ao clicar no botão `Editar Funções`, será exibido o painel de `Gerenciamento de Funções (功能管理)`.

No painel de `Gerenciamento de Funções`, você precisa marcar `Consulta de Estado de Dispositivos do HomeAssistant (HomeAssistant设备状态查询)` e `Alteração de Estado de Dispositivos do HomeAssistant (HomeAssistant设备状态修改)`.

Depois de marcar, em `Funções Selecionadas (已选功能)` clique em `Consulta de Estado de Dispositivos do HomeAssistant` e, em `Configuração de Parâmetros`, configure o endereço do seu `HomeAssistant`, a chave (api key) e a string da lista de dispositivos.

Depois de editar, clique em `Salvar Configuração`; o painel de `Gerenciamento de Funções` será ocultado; então clique novamente para salvar a configuração do agente.

Após salvar com sucesso, basta despertar o dispositivo para operar.

#### 3. Desperte o dispositivo para realizar o controle

Tente dizer ao esp32: “ligue a luz XXX”

## Método 2: o XiaoZhi usa o assistente de voz do Home Assistant como ferramenta LLM

### Descrição da função

- Este método tem uma desvantagem bastante séria — **com ele não é possível usar a capacidade dos plugins function_call do ecossistema open source do XiaoZhi**, porque usar o Home Assistant como ferramenta LLM do XiaoZhi transfere a capacidade de reconhecimento de intenção para o Home Assistant. Porém **este método permite experimentar as funções nativas de operação do Home Assistant, e a capacidade de conversação do XiaoZhi permanece inalterada**. Se isso realmente incomodar, você pode usar o [Método 3](##Método 3: usando o serviço MCP do Home Assistant (recomendado)), que também é suportado pelo Home Assistant e permite aproveitar ao máximo as suas funcionalidades.

### Passos de configuração:

#### 1. Configure o assistente de voz de LLM do Home Assistant.

**É necessário que você já tenha configurado previamente o assistente de voz ou a ferramenta de LLM do Home Assistant.**

#### 2. Obtenha o Agent ID do assistente de voz do Home Assistant.

1. Entre na página do Home Assistant. No menu à esquerda, clique em `Ferramentas do Desenvolvedor (开发者助手)`.
2. Dentro das `Ferramentas do Desenvolvedor` abertas, clique na aba `Ações` (operação 1 na imagem); na barra de opções `Ações` da página, localize ou digite `conversation.process (conversa – processar)` e selecione `Conversa (conversation): Processar` (operação 2 na imagem).

![image-20250504043539343](../upstream/docs/images/image-ha-integration-02.png)

3. Na página, marque a opção `Agente (agent)`; no `Agente de conversa (conversation agent)`, que passa a ficar aceso, selecione o nome do assistente de voz que você configurou no passo um; como mostra a imagem, o meu configurado foi o `ZhipuAi` e o selecionei.

![image-20250504043854760](../upstream/docs/images/image-ha-integration-03.png)

4. Depois de selecionar, clique em `Entrar no modo YAML` no canto inferior esquerdo do formulário.

![image-20250504043951126](../upstream/docs/images/image-ha-integration-04.png)

5. Copie o valor do agent-id; por exemplo, na imagem o meu é `01JP2DYMBDF7F4ZA2DMCF2AGX2` (apenas para referência).

![image-20250504044046466](../upstream/docs/images/image-ha-integration-05.png)

6. Vá para o arquivo `config.yaml` do servidor open source do XiaoZhi, o `xiaozhi-esp32-server`; na configuração do LLM, localize o Home Assistant e defina o endereço de rede do seu Home Assistant, a Api key e o agent_id recém-obtido.
7. No arquivo `config.yaml`, altere no atributo `selected_module` o `LLM` para `HomeAssistant` e o `Intent` para `nointent`.
8. Reinicie o servidor open source do XiaoZhi, o `xiaozhi-esp32-server`, e tudo funcionará normalmente.

## Método 3: usando o serviço MCP do Home Assistant (recomendado)

### Descrição da função

- É necessário que você já tenha instalado e integrado no Home Assistant a integração do HA — [Model Context Protocol Server](https://www.home-assistant.io/integrations/mcp_server/).

- Este método e o Método 2 são soluções fornecidas oficialmente pelo HA. A diferença em relação ao Método 2 é que você pode usar normalmente os plugins construídos em conjunto pela comunidade do servidor open source do XiaoZhi, o `xiaozhi-esp32-server`, além de poder usar livremente qualquer LLM que suporte function_call.

### Passos de configuração

#### 1. Instale a integração do serviço MCP do Home Assistant.

Site oficial da integração — [Model Context Protocol Server](https://www.home-assistant.io/integrations/mcp_server/)..

Ou siga o procedimento manual abaixo.

> - Vá para **[Configurações > Dispositivos e Serviços (Settings > Devices & Services.)](https://my.home-assistant.io/redirect/integrations)** na página do Home Assistant.
>
> - No canto inferior direito, selecione o botão **[Adicionar Integração (Add Integration)](https://my.home-assistant.io/redirect/config_flow_start?domain=mcp_server)**.
>
> - Na lista, selecione **Servidor do Protocolo de Contexto de Modelo (Model Context Protocol Server)**.
>
> - Siga as instruções na tela para concluir a configuração.

#### 2. Configure as informações de MCP do servidor open source do XiaoZhi


Entre no diretório `data` e localize o arquivo `.mcp_server_settings.json`.

Se o seu diretório `data` não tiver o arquivo `.mcp_server_settings.json`,
- copie o arquivo `mcp_server_settings.json` da raiz da pasta `xiaozhi-server` para o diretório `data` e renomeie-o para `.mcp_server_settings.json`
- ou [baixe este arquivo](https://github.com/xinnan-tech/xiaozhi-esp32-server/blob/main/main/xiaozhi-server/mcp_server_settings.json), salve-o no diretório `data` e renomeie-o para `.mcp_server_settings.json`


Modifique esta parte dentro de `"mcpServers"`:

```json
"Home Assistant": {
      "command": "mcp-proxy",
      "args": [
        "http://YOUR_HA_HOST/mcp_server/sse"
      ],
      "env": {
        "API_ACCESS_TOKEN": "YOUR_API_ACCESS_TOKEN"
      }
},
```

Atenção:

1. **Substituição da configuração:**
   - Substitua `YOUR_HA_HOST` dentro de `args` pelo endereço do seu serviço do HA; se o seu endereço de serviço já contém o prefixo https/http (por exemplo, `http://192.168.1.101:8123`), basta preencher `192.168.1.101:8123`.
   - Substitua o `YOUR_API_ACCESS_TOKEN` do `API_ACCESS_TOKEN` dentro de `env` pela api key (chave de desenvolvedor) que você obteve anteriormente.
2. **Se, dentro das chaves de `"mcpServers"`, depois da sua configuração adicionada não houver novas configurações de `mcpServers`, é preciso remover a vírgula final `,`**, caso contrário a análise pode falhar.

**O resultado final deve ficar como referência abaixo**:

```json
 "mcpServers": {
    "Home Assistant": {
      "command": "mcp-proxy",
      "args": [
        "http://192.168.1.101:8123/mcp_server/sse"
      ],
      "env": {
        "API_ACCESS_TOKEN": "abcd.efghi.jkl"
      }
    }
  }
```

#### 3. Configure as configurações de sistema do servidor open source do XiaoZhi

1. **Escolha qualquer LLM que suporte function_call como assistente de conversação LLM do XiaoZhi (mas não escolha o Home Assistant como ferramenta LLM)**; o modelo que escolhi desta vez foi o ChatGLM gratuito, que suporta chamada de funções (function call), mas às vezes a chamada não é muito estável; se você busca estabilidade, sugiro definir o LLM como: DoubaoLLM, com o model_name: doubao-1-5-pro-32k-250115.

2. Vá para o arquivo `config.yaml` do servidor open source do XiaoZhi, o `xiaozhi-esp32-server`, configure o seu LLM e ajuste o `Intent` da configuração `selected_module` para `function_call`.

3. Reinicie o servidor open source do XiaoZhi, o `xiaozhi-esp32-server`, e tudo funcionará normalmente.
