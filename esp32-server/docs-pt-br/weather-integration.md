# Guia de uso do plugin de clima

## Visão geral

O plugin de clima `get_weather` é uma das funções centrais do assistente de voz xiaozhi ESP32; ele permite consultar por voz as informações do tempo em todo o país. O plugin é baseado na API do QWeather (和风天气) e oferece condições atuais e previsão do tempo para 7 dias.

## Guia de solicitação da API Key

### 1. Registro da conta QWeather

1. Acesse o [console do QWeather](https://console.qweather.com/)
2. Crie a conta e complete a verificação de e-mail
3. Entre no console

### 2. Criação do projeto para obter a API Key

1. No console, clique em ["Gerenciamento de Projetos"](https://console.qweather.com/project?lang=zh) → "Criar Projeto"
2. Preencha as informações do projeto:
   - **Nome do projeto**: por exemplo, "Assistente de Voz Xiaozhi"
3. Clique em Salvar
4. Depois de criar o projeto, clique em "Criar Credencial" dentro dele
5. Preencha as informações da credencial:
    - **Nome da credencial**: por exemplo, "Assistente de Voz Xiaozhi"
    - **Método de autenticação**: selecione "API Key"
6. Clique em Salvar
7. Copie a `API Key` da credencial — esta é a primeira informação essencial de configuração

### 3. Obtenção do API Host

1. No console, clique em ["Configurações"](https://console.qweather.com/setting?lang=zh) → "API Host"
2. Veja o endereço de `API Host` exclusivo atribuído a você — esta é a segunda informação essencial de configuração

Com os passos acima, você obtém as duas informações importantes de configuração: `API Key` e `API Host`

## Formas de configuração (escolha uma)

### Método 1. Se você fez a implantação com o Console de Gerenciamento (智控台) (recomendado)

1. Entre no Console de Gerenciamento
2. Abra a página "Configuração de Papel" (角色配置)
3. Selecione o Agente (智能体) que deseja configurar
4. Clique no botão "Editar Funções"
5. Na área de parâmetros à direita, localize o plugin "Consulta de Clima" (天气查询)
6. Marque "Consulta de Clima"
7. Cole a primeira informação essencial copiada, a `API Key`, no campo `Chave de API do plugin de clima` (天气插件 API 密钥)
8. Cole a segunda informação essencial copiada, o `API Host`, no campo `API Host do desenvolvedor` (开发者 API Host)
9. Salve a configuração e, depois, salve a configuração do Agente

### Método 2. Se você usa apenas o deployment de módulo único do xiaozhi-server

Configure em `data/.config.yaml`:

1. Cole a primeira informação essencial copiada, a `API Key`, em `api_key`
2. Cole a segunda informação essencial copiada, o `API Host`, em `api_host`
3. Preencha a sua cidade em `default_location`, por exemplo `Guangzhou`

```yaml
plugins:
  get_weather:
    api_key: "sua API Key do QWeather"
    api_host: "seu endereço de API Host do QWeather"
    default_location: "sua cidade padrão de consulta"
```

