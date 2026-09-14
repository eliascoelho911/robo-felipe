# Guia de uso da ferramenta de teste de desempenho de reconhecimento de fala (ASR), modelo de linguagem (LLM), síntese de voz não streaming, síntese de voz em streaming e modelo de visão

1.Crie o diretório data dentro do diretório main/xiaozhi-server
2.Crie o arquivo .config.yaml dentro do diretório data
3.No arquivo .data/config.yaml, escreva os parâmetros do seu reconhecimento de fala, modelo de linguagem, síntese de voz em streaming e modelo de visão
Por exemplo:
```
LLM:
  ChatGLMLLM:
    # Define o tipo de API do LLM
    type: openai
    # glm-4-flash é gratuito, mas ainda é preciso se registrar e preencher o api_key
    # Você encontra seu api key aqui: https://bigmodel.cn/usercenter/proj-mgmt/apikeys
    model_name: glm-4-flash
    url: https://open.bigmodel.cn/api/paas/v4/
    api_key: sua chat-glm web key

TTS:

VLLM:

ASR:
```
4.No diretório main/xiaozhi-server, execute performance_tester.py: 
```
python performance_tester.py
```
