# Guia de integração do componente de memória PowerMem

## Introdução

O [PowerMem](https://www.powermem.ai/) é um componente de memória para agentes open-source do OceanBase: por meio de um LLM local, ele resume memórias e faz busca inteligente, oferecendo gerenciamento de memória eficiente para agentes de IA.

Sobre os custos: o PowerMem em si é open-source e gratuito; o custo real depende do LLM e do banco de dados que você escolher:
- SQLite + LLM gratuito (como o glm-4-flash da Zhipu) = **totalmente gratuito**
- LLM em nuvem ou banco de dados em nuvem = cobrado conforme o serviço correspondente

> 💡 **Dica para o melhor desempenho**: o PowerMem combinado com o OceanBase libera o máximo de desempenho; o SQLite é recomendado apenas quando os recursos são escassos.

- **GitHub**: https://github.com/oceanbase/powermem
- **Site oficial**: https://www.powermem.ai/
- **Exemplos de uso**: https://github.com/oceanbase/powermem/tree/main/examples

## Recursos

- **Resumo local**: resumo e extração de memórias localmente, via LLM
- **Perfil do usuário**: extração automática de informações do usuário (nome, profissão, interesses etc.) via `UserMemory`, atualizando o perfil continuamente
- **Esquecimento inteligente**: com base na curva de esquecimento de Ebbinghaus, "esquece" automaticamente informações obsoletas e ruído
- **Vários backends de armazenamento**: suporta OceanBase (recomendado, melhor desempenho), SeekDB (recomendado, armazenamento integrado para aplicações de IA), PostgreSQL, SQLite (alternativa leve)
- **Suporte a vários LLMs**: Qwen (Tongyi Qianwen), Zhipu (glm-4-flash gratuito), OpenAI etc.
- **Busca inteligente**: capacidade de busca semântica baseada em busca vetorial
- **Implantação privada**: suporte completo à implantação privada local
- **Operações assíncronas**: gerenciamento de memória assíncrono e eficiente

## Instalação

O PowerMem já foi adicionado às dependências do projeto; se precisar instalar manualmente:

```bash
pip install powermem
```

## Configuração

### Configuração básica

Configure o PowerMem no `config.yaml`:

```yaml
selected_module:
  Memory: powermem

Memory:
  powermem:
    type: powermem
    # Se deve ativar o recurso de perfil do usuário
    # Perfil do usuário suporta: oceanbase, seekdb, sqlite (powermem 0.3.0+)
    enable_user_profile: true
    
    # ========== Configuração do LLM ==========
    llm:
      provider: openai  # opções: qwen, openai, zhipu etc.
      config:
        api_key: sua-chave-de-api-do-LLM
        model: qwen-plus
        # openai_base_url: https://api.openai.com/v1  # opcional, endereço de serviço personalizado
    
    # ========== Configuração do Embedding ==========
    embedder:
      provider: openai  # opções: qwen, openai etc.
      config:
        api_key: sua-chave-de-api-do-modelo-de-embedding
        model: text-embedding-v4
        openai_base_url: https://dashscope.aliyuncs.com/compatible-mode/v1
        # embedding_dims: 1024  # dimensão do vetor; configure se não for 1536
    
    # ========== Configuração do Database ==========
    vector_store:
      provider: sqlite  # opções: oceanbase (recomendado), seekdb (recomendado), postgres, sqlite (leve)
      config: {}  # SQLite não precisa de configuração adicional
```

### Detalhes dos parâmetros de configuração

#### Configuração do LLM

| Parâmetro | Descrição | Valores possíveis |
|------|------|--------|
| `llm.provider` | Provedor do LLM | `qwen`, `openai`, `zhipu` etc. |
| `llm.config.api_key` | Chave de API | - |
| `llm.config.model` | Nome do modelo | conforme o provedor |
| `llm.config.openai_base_url` | Endereço de serviço personalizado (opcional) | - |

#### Configuração do Embedding

| Parâmetro | Descrição | Valores possíveis |
|------|------|--------|
| `embedder.provider` | Provedor do modelo de embedding | `qwen`, `openai` etc. |
| `embedder.config.api_key` | Chave de API | - |
| `embedder.config.model` | Nome do modelo | conforme o provedor |
| `embedder.config.openai_base_url` | Endereço de serviço personalizado (opcional) | - |

#### Configuração do Database

| Parâmetro | Descrição | Valores possíveis |
|------|------|--------|
| `vector_store.provider` | Tipo de backend de armazenamento | `oceanbase` (recomendado), `seekdb` (recomendado), `postgres`, `sqlite` (leve) |
| `vector_store.config` | Configuração de conexão do banco de dados | conforme o provider |

### Modos de memória

O PowerMem suporta dois modos de memória:

| Modo | Configuração | Função | Requisitos de armazenamento |
|------|------|------|----------|
| **Memória comum** | `enable_user_profile: false` | Armazenamento e busca das memórias das conversas | Suporta todos os bancos de dados |
| **Perfil do usuário** | `enable_user_profile: true` | Memória + extração automática do perfil do usuário | oceanbase, seekdb, sqlite |

> 📌 **Nota de versão**: a partir da versão PowerMem 0.3.0+, o recurso de perfil do usuário suporta três backends de armazenamento: OceanBase, SeekDB e SQLite.

### Usando o Qwen/Tongyi Qianwen (recomendado)

1. Acesse a [plataforma Alibaba Cloud Bailian](https://bailian.console.aliyun.com/) e registre uma conta
2. Obtenha a chave de API na página de [gerenciamento de API Keys](https://bailian.console.aliyun.com/?apiKey=1#/api-key)
3. Configure como a seguir:

```yaml
Memory:
  powermem:
    type: powermem
    enable_user_profile: true
    llm:
      provider: qwen
      config:
        api_key: sk-xxxxxxxxxxxxxxxx
        model: qwen-plus
    embedder:
      provider: openai
      config:
        api_key: sk-xxxxxxxxxxxxxxxx
        model: text-embedding-v4
        openai_base_url: https://dashscope.aliyuncs.com/compatible-mode/v1
    vector_store:
      provider: sqlite
      config: {}
```

### Usando o LLM gratuito da Zhipu (solução totalmente gratuita)

A Zhipu oferece o modelo gratuito glm-4-flash; combinado com o SQLite, é possível usar tudo de graça:

1. Acesse a [plataforma aberta Zhipu AI](https://bigmodel.cn/) e registre uma conta
2. Obtenha a chave de API na página de [API Keys](https://bigmodel.cn/usercenter/proj-mgmt/apikeys)
3. Configure como a seguir:

```yaml
Memory:
  powermem:
    type: powermem
    enable_user_profile: true
    llm:
      provider: openai  # usa o modo de compatibilidade openai
      config:
        api_key: xxxxxxxxxxxxxxxx.xxxxxxxxxxxxxxxx
        model: glm-4-flash
        openai_base_url: https://open.bigmodel.cn/api/paas/v4/
    embedder:
      provider: openai
      config:
        api_key: xxxxxxxxxxxxxxxx.xxxxxxxxxxxxxxxx
        model: embedding-3
        openai_base_url: https://open.bigmodel.cn/api/paas/v4/
    vector_store:
      provider: sqlite
      config: {}
```

### Usando o OpenAI

```yaml
Memory:
  powermem:
    type: powermem
    enable_user_profile: true
    llm:
      provider: openai
      config:
        api_key: sk-xxxxxxxxxxxxxxxx
        model: gpt-4o-mini
        openai_base_url: https://api.openai.com/v1
    embedder:
      provider: openai
      config:
        api_key: sk-xxxxxxxxxxxxxxxx
        model: text-embedding-3-small
        openai_base_url: https://api.openai.com/v1
    vector_store:
      provider: sqlite
      config: {}
```

### Usando o OceanBase (solução de melhor desempenho)

O OceanBase é o melhor par do PowerMem e libera o máximo de desempenho:

1. Implante o banco de dados OceanBase (suporta implantação local open-source ou uso do serviço em nuvem)
   - Implantação open-source: https://github.com/oceanbase/oceanbase
   - Serviço em nuvem: https://www.oceanbase.com/
2. Configure como a seguir:

```yaml
Memory:
  powermem:
    type: powermem
    enable_user_profile: true
    llm:
      provider: qwen
      config:
        api_key: sk-xxxxxxxxxxxxxxxx
        model: qwen-plus
    embedder:
      provider: openai
      config:
        api_key: sk-xxxxxxxxxxxxxxxx
        model: text-embedding-v4
        openai_base_url: https://dashscope.aliyuncs.com/compatible-mode/v1
    vector_store:
      provider: oceanbase
      config:
        host: 127.0.0.1
        port: 2881
        user: root@test
        password: your_password
        db_name: powermem
        collection_name: memories  # valor padrão
        embedding_model_dims: 1536  # dimensão do vetor de embedding, parâmetro obrigatório
```

## Isolamento de memória por dispositivo

O PowerMem usa automaticamente o ID do dispositivo (`device_id`) como `user_id` para isolar as memórias. Isso significa:

- Cada dispositivo tem um espaço de memória independente
- As memórias entre dispositivos diferentes ficam totalmente isoladas
- Várias conversas do mesmo dispositivo podem compartilhar o contexto de memória

## Perfil do usuário (UserMemory)

O PowerMem fornece a classe `UserMemory`, que extrai automaticamente informações de perfil do usuário a partir das conversas.

> 📌 **Nota de versão**: a partir da versão PowerMem 0.3.0+, o recurso de perfil do usuário suporta três backends de armazenamento: OceanBase, SeekDB e SQLite.

### Ativando o perfil do usuário

Basta definir `enable_user_profile: true` na configuração para ativar:

```yaml
Memory:
  powermem:
    type: powermem
    enable_user_profile: true  # ativa o perfil do usuário
    llm:
      provider: qwen
      config:
        api_key: sk-xxxxxxxxxxxxxxxx
        model: qwen-plus
    embedder:
      provider: openai
      config:
        api_key: sk-xxxxxxxxxxxxxxxx
        model: text-embedding-v4
        openai_base_url: https://dashscope.aliyuncs.com/compatible-mode/v1
    vector_store:
      provider: sqlite  # perfil do usuário suporta: oceanbase, seekdb, sqlite
      config: {}
```

### Capacidades do perfil do usuário

| Capacidade | Descrição |
|------|------|
| **Extração de informações** | Extrai automaticamente nome, idade, profissão, interesses etc. das conversas |
| **Atualização contínua** | Refina o perfil do usuário à medida que as conversas avançam |
| **Busca com perfil** | Combina o perfil do usuário com a busca de memórias, melhorando a relevância da busca |
| **Esquecimento inteligente** | Com base na curva de esquecimento de Ebbinghaus, esmaece informações obsoletas |

### Como funciona

Com o perfil do usuário ativado, ao consultar a memória o xiaozhi retorna automaticamente:
1. **Perfil do usuário**: informações básicas, interesses etc. do usuário
2. **Memórias relevantes**: memórias históricas relacionadas à conversa atual

> ✅ **Nota de versão**: a partir da versão PowerMem 0.3.0+, o recurso de perfil do usuário suporta três backends de armazenamento: OceanBase, SeekDB e SQLite.

## Comparação com outros componentes de memória

| Recurso | PowerMem | mem0ai | mem_local_short |
|------|----------|--------|-----------------|
| Modo de operação | Resumo local | API em nuvem | Resumo local |
| Local de armazenamento | DB local/nuvem | Nuvem | YAML local |
| Custo | Depende do LLM e do DB | 1000 chamadas/mês grátis | Totalmente gratuito |
| Busca inteligente | ✅ busca vetorial | ✅ busca vetorial | ❌ retorna tudo |
| Perfil do usuário | ✅ UserMemory | ❌ | ❌ |
| Esquecimento inteligente | ✅ curva do esquecimento | ❌ | ❌ |
| Implantação privada | ✅ suporta | ❌ apenas nuvem | ✅ suporta |
| Bancos de dados suportados | OceanBase (recomendado)/SeekDB/PostgreSQL/SQLite | - | arquivo YAML |

## Perguntas frequentes

### 1. Erro de chave de API

Se aparecer o erro `API key is required`, verifique:
- se `llm_api_key` e `embedding_api_key` foram preenchidos corretamente
- se a chave de API é válida

### 2. Modelo inexistente

Se aparecer erro de modelo inexistente, confirme:
- se os nomes `llm_model` e `embedding_model` estão corretos
- se o serviço do modelo correspondente está habilitado

### 3. Timeout de conexão

Se houver timeout de conexão, tente:
- verificar a conexão de rede
- se você usa proxy, configurar `llm_base_url` e `embedding_base_url`

## Teste e verificação

É possível testar em um ambiente virtual se o PowerMem funciona corretamente:

```bash
# Ativar o ambiente virtual
source .venv/bin/activate

# Testar a importação do PowerMem
python -c "from powermem import AsyncMemory; print('PowerMem 导入成功')"

# Testar a importação do UserMemory (recurso de perfil do usuário)
python -c "from powermem import UserMemory; print('UserMemory 导入成功')"
```

## Mais recursos

- [Documentação oficial do PowerMem](https://www.powermem.ai/)
- [Repositório GitHub do PowerMem](https://github.com/oceanbase/powermem)
- [Exemplos de uso do PowerMem](https://github.com/oceanbase/powermem/tree/main/examples)
- [Site oficial do OceanBase](https://www.oceanbase.com/)
- [OceanBase GitHub](https://github.com/oceanbase/oceanbase)
- [SeekDB GitHub](https://github.com/oceanbase/seekdb) (banco de dados de busca nativo para IA)
- [Plataforma Alibaba Cloud Bailian](https://bailian.console.aliyun.com/)

