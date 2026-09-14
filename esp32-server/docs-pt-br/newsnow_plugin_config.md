# Guia de configuração das fontes de notícias do plugin get_news_from_newsnow

## Visão geral

O plugin `get_news_from_newsnow` agora permite configurar dinamicamente as fontes de notícias pela interface web de gerenciamento, sem precisar alterar código. Você pode configurar fontes de notícias diferentes para cada agente no Console de Gerenciamento (智控台).

## Formas de configuração

### 1. Pela interface web de gerenciamento (recomendada)

1. Faça login no Console de Gerenciamento
2. Entre na página "Configuração de Papéis" (角色配置)
3. Selecione o agente a ser configurado
4. Clique no botão "Editar funções"
5. Na área de parâmetros à direita, localize o plugin "Agregador de notícias newsnow" (newsnow新闻聚合)
6. No campo "Configuração das fontes de notícias", informe nomes em chinês separados por ponto e vírgula

### 2. Pelo arquivo de configuração

No `config.yaml`, configure:

```yaml
plugins:
  get_news_from_newsnow:
    url: "https://newsnow.busiyi.world/api/s?id="
    news_sources: "澎湃新闻;百度热搜;财联社;微博;抖音"
```

## Formato de configuração das fontes de notícias

A configuração das fontes usa nomes em chinês separados por ponto e vírgula, no formato:

```
nome-em-chines-1;nome-em-chines-2;nome-em-chines-3
```

### Exemplo de configuração

```
澎湃新闻;百度热搜;财联社;微博;抖音;知乎;36氪
```

## Fontes de notícias suportadas

O plugin aceita os seguintes nomes em chinês de fontes de notícias:

- 澎湃新闻
- 百度热搜
- 财联社
- 微博
- 抖音
- 知乎
- 36氪
- 华尔街见闻
- IT之家
- 今日头条
- 虎扑
- 哔哩哔哩
- 快手
- 雪球
- 格隆汇
- 法布财经
- 金十数据
- 牛客
- 少数派
- 稀土掘金
- 凤凰网
- 虫部落
- 联合早报
- 酷安
- 远景论坛
- 参考消息
- 卫星通讯社
- 百度贴吧
- 靠谱新闻
- e mais...

## Configuração padrão

Se nenhuma fonte de notícias for configurada, o plugin usa a seguinte configuração padrão:

```
澎湃新闻;百度热搜;财联社
```

## Instruções de uso

1. **Configure as fontes**: informe os nomes em chinês das fontes de notícias, separados por ponto e vírgula, na interface web ou no arquivo de configuração
2. **Chame o plugin**: você pode dizer “播报新闻” (anuncie as notícias) ou “获取新闻” (obtenha as notícias)
3. **Especifique a fonte**: você pode dizer “播报澎湃新闻” (anuncie as notícias do The Paper) ou “获取百度热搜” (obtenha os trending do Baidu)
4. **Obtenha os detalhes**: você pode dizer “详细介绍这条新闻” (detalhe esta notícia)

## Como funciona

1. O plugin aceita o nome em chinês como parâmetro (por exemplo, “澎湃新闻”)
2. Conforme a lista de fontes configurada, converte o nome em chinês no ID em inglês correspondente (por exemplo, "thepaper")
3. Usa o ID em inglês para chamar a API e obter os dados das notícias
4. Devolve o conteúdo das notícias ao usuário

## Observações

1. Os nomes em chinês configurados devem ser exatamente iguais aos definidos em CHANNEL_MAP
2. Depois de alterar a configuração, é preciso reiniciar o serviço ou recarregar a configuração
3. Se uma fonte de notícias configurada for inválida, o plugin usa automaticamente as fontes padrão
4. Entre múltiplas fontes, use ponto e vírgula em inglês (;) — não use o ponto e vírgula chinês (；)
