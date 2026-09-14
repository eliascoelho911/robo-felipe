# Tutorial de uso das Fontes de Contexto (上下文源)

## Visão geral

A `Fonte de Contexto` (上下文源) é uma forma de adicionar 【fontes de dados】 ao contexto do prompt de sistema do Xiaozhi.

A `Fonte de Contexto`, no momento em que o Xiaozhi é despertado, obtém dados de sistemas externos e os injeta dinamicamente no prompt de sistema (System Prompt) do modelo de linguagem.
Fazendo com que ele perceba o estado de alguma coisa do mundo no instante do despertar.

Existe uma diferença fundamental em relação ao MCP e à memória: a `Fonte de Contexto` força o Xiaozhi a perceber os dados do mundo; a `Memória (Mem)` faz com que ele saiba o que foi conversado antes; o `MCP(functionc all)` é usado quando é necessário invocar alguma capacidade/conhecimento.

Com essa funcionalidade, no instante em que o Xiaozhi é despertado, ele "percebe":
- O estado dos sensores de saúde corporal (temperatura corporal, pressão arterial, oxigenação do sangue etc.)
- Dados em tempo real de sistemas de negócio (carga do servidor, pendências, informações de ações etc.)
- Qualquer informação textual que possa ser obtida via API HTTP

**Atenção**: esta funcionalidade serve apenas para o Xiaozhi perceber o estado das coisas no momento do despertar; se você quiser que ele obtenha o estado em tempo real depois de despertar, recomenda-se combinar esta funcionalidade com chamadas a ferramentas MCP.

## Como funciona

1. **Configurar as fontes**: o usuário configura um ou mais endereços de API HTTP.
2. **Disparo da requisição**: quando o sistema monta o Prompt, se encontrar o placeholder `{{ dynamic_context }}` no template, ele faz requisições a todas as APIs configuradas.
3. **Injeção automática**: o sistema formata automaticamente os dados retornados pelas APIs como uma lista Markdown e substitui o placeholder `{{ dynamic_context }}`.

## Especificação da interface

Para que o Xiaozhi consiga interpretar os dados corretamente, sua API precisa atender à seguinte especificação:

- **Método da requisição**: `GET`
- **Cabeçalhos**: o sistema adiciona automaticamente o campo `device-id` ao Request Header.
- **Formato da resposta**: deve retornar JSON contendo os campos `code` e `data`.

### Exemplo de resposta

**Caso 1: retorno de pares chave-valor**
```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "客厅温度": "26℃",
    "客厅湿度": "45%",
    "大门状态": "已关闭"
  }
}
```
*Efeito da injeção:*
```markdown
<context>
- **客厅温度：** 26℃
- **客厅湿度：** 45%
- **大门状态：** 已关闭
</context>
```

**Caso 2: retorno de lista**
```json
{
  "code": 0,
  "data": [
    "您有10个待办事项",
    "当前汽车的行驶速度是100km每小时"
  ]
}
```
*Efeito da injeção:*
```markdown
<context>
- 您有10个待办事项
- 当前汽车的行驶速度是100km每小时
</context>
```

## Guia de configuração

### Opção 1: configuração pelo Console de Gerenciamento (智控台) (deployment de módulo completo)

1. Faça login no Console de Gerenciamento (智控台) e acesse a página **Configuração de Papéis** (角色配置).
2. Localize o item de configuração **Fonte de Contexto** (botão "Editar fontes").
3. Clique em **Adicionar** e informe o endereço da sua API.
4. Se a API exigir autenticação, você pode adicionar `Authorization` ou outro Header na seção **Cabeçalhos da requisição**.
5. Salve a configuração.

### Opção 2: configuração via arquivo de configuração (deployment de módulo único)

Edite o arquivo `xiaozhi-server/data/.config.yaml` e adicione a seção de configuração `context_providers`:

```yaml
# Configuração das fontes de contexto
context_providers:
  - url: "http://api.example.com/data"
    headers:
      Authorization: "Bearer your-token"
  - url: "http://another-api.com/data"
```

## Habilitando a funcionalidade

Por padrão, o arquivo de template do prompt de sistema (`data/.agent-base-prompt.txt`) já vem com o placeholder `{{ dynamic_context }}` pré-inserido, e você não precisa adicioná-lo manualmente.

**Exemplo:**

```markdown
<context>
【重要！以下信息已实时提供，无需调用工具查询，请直接使用：】
- **设备ID：** {{device_id}}
- **当前时间：** {{current_time}}
...
{{ dynamic_context }}
</context>
```

**Atenção**: se você não quiser usar esta funcionalidade, pode optar por **não configurar nenhuma fonte de contexto** ou **remover** o placeholder `{{ dynamic_context }}` do arquivo de template do prompt.

## Apêndice: exemplo de serviço Mock para testes

Para facilitar seus testes e desenvolvimento, fornecemos um script simples de Mock Server em Python. Você pode executar este script para simular a interface da API localmente.

**mock_api_server.py**

```python
import http.server
import socketserver
import json
from urllib.parse import urlparse, parse_qs

# Define o número da porta
PORT = 8081

class MockRequestHandler(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        # Faz o parsing do caminho e dos parâmetros
        parsed_path = urlparse(self.path)
        path = parsed_path.path
        query = parse_qs(parsed_path.query)

        response_data = {}
        status_code = 200

        print(f"收到请求: {path}, 参数: {query}")

        # Case 1: simula dados de saúde (retorna dicionário Dict)
        # Estilo de parâmetro no caminho: /health
        # device_id obtido do Header
        if path == "/health":
            device_id = self.headers.get("device-id", "unknown_device")
            print(f"device_id: {device_id}")
            response_data = {
                "code": 0,
                "msg": "success",
                "data": {
                    "测试设备ID": device_id,
                    "心率": "80 bpm",
                    "血压": "120/80 mmHg",
                    "状态": "良好"
                }
            }

        # Case 2: simula lista de notícias (retorna lista List)
        # Sem parâmetros: /news/list
        elif path == "/news/list":
            response_data = {
                "code": 0,
                "msg": "success",
                "data": [
                    "今日头条：Python 3.14 发布",
                    "科技新闻：AI 助手改变生活",
                    "本地新闻：明日有大雨，记得带伞"
                ]
            }

        # Case 3: simula boletim do tempo (retorna String)
        # Sem parâmetros: /weather/simple
        elif path == "/weather/simple":
            response_data = {
                "code": 0,
                "msg": "success",
                "data": "今日晴转多云，气温 20-25 度，空气质量优，适合出行。"
            }

        # Case 4: simula detalhes do dispositivo (estilo de parâmetros Query)
        # Estilo de parâmetro: /device/info
        # device_id obtido do Header
        elif path == "/device/info":
            device_id = self.headers.get("device-id", "unknown_device")
            response_data = {
                "code": 0,
                "msg": "success",
                "data": {
                    "查询方式": "Header参数",
                    "设备ID": device_id,
                    "电量": "85%",
                    "固件": "v2.0.1"
                }
            }
        
        # Case 5: 404 Not Found
        else:
            status_code = 404
            response_data = {"error": "接口不存在"}

        # Envia a resposta
        self.send_response(status_code)
        self.send_header('Content-type', 'application/json; charset=utf-8')
        self.end_headers()
        self.wfile.write(json.dumps(response_data, ensure_ascii=False).encode('utf-8'))

# Inicia o serviço
# Permite reuso de endereço para evitar erro ao reiniciar rapidamente
socketserver.TCPServer.allow_reuse_address = True
with socketserver.TCPServer(("", PORT), MockRequestHandler) as httpd:
    print(f"==================================================")
    print(f"Mock API Server 已启动: http://localhost:{PORT}")
    print(f"可用接口列表:")
    print(f"1. [字典] http://localhost:{PORT}/health")
    print(f"2. [列表] http://localhost:{PORT}/news/list")
    print(f"3. [文本] http://localhost:{PORT}/weather/simple")
    print(f"4. [参数] http://localhost:{PORT}/device/info")
    print(f"==================================================")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\n服务已停止")
```
