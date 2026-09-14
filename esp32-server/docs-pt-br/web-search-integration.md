# Guia de uso do plugin de busca na web

## Apresentação da função

O plugin de busca na web `web_search` suporta buscar informações na internet em tempo real durante a conversa e devolver os resultados. O plugin suporta duas fontes de busca: Metaso (秘塔) e Tavily; você pode escolher uma delas conforme a necessidade.

## Guia de solicitação da API Key

Atualmente adaptamos a `busca Metaso (秘塔搜索)` e a `busca Tavily`.
- Busca Tavily: 1000 chamadas gratuitas por mês.
- Busca Metaso: possui fontes de dados nacionais de boa qualidade.

## Guia de solicitação da API Key

### Método 1: usar a busca Metaso

- Acesse a [API do Metaso](https://metaso.cn/search-api/api-keys), registre-se e faça login
- Na página de gerenciamento de chaves de API, clique em "Criar nova Key"
- Copie a API Key gerada (com prefixo `mk-`); esta é a informação essencial para a configuração

### Método 2: usar a busca Tavily

- Acesse o [console do Tavily](https://app.tavily.com/home), registre-se e faça login
- No console, crie uma API Key
- Copie a API Key gerada (com prefixo `tvly-`); esta é a informação essencial para a configuração

## Formas de configuração

### Método 1. Implantação com o Console de Gerenciamento (智控台) (recomendado)

- Faça login no Console de Gerenciamento
- Entre na página "Configuração de Papéis" (配置角色) e selecione o agente a ser configurado
- Clique no botão "Editar funções" e, na área de parâmetros à direita, localize o plugin "Busca na web" (联网搜索)
- Marque "Busca na web" (联网搜索)
- Preencha a fonte de busca (`metaso` ou `tavily`) e informe a `API Key` correspondente no campo de configuração
- Salve a configuração e, depois, salve a configuração do agente

### Método 2. Implantação de módulo único do xiaozhi-server

No `data/.config.yaml`, configure:

- Preencha a fonte de busca em `provider`; os valores possíveis são `metaso` ou `tavily`
- Preencha a API Key solicitada em `api_key`

```yaml
plugins:
  web_search:
    provider: "metaso"
    api_key: "sua API Key"
```

Se você quiser personalizar a quantidade de resultados retornados e a descrição da ferramenta, configure também `max_results` e `description`:

```yaml
plugins:
  web_search:
    provider: "metaso"
    description: "联网搜索工具。当用户明确需要联网搜索问题时使用此工具。"
    max_results: 5
    api_key: "sua API Key"
```

Ao mesmo tempo, garanta que `web_search` está habilitado na lista `functions`:

```yaml
plugins:
  functions:
    - web_search
```

Depois de configurar, reinicie o serviço para que a configuração entre em vigor.
