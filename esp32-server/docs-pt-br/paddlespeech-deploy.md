# Integração do PaddleSpeech TTS com o serviço xiaozhi

## Pontos principais
- Vantagens: implantação local offline, velocidade alta
- Desvantagens: até 25/09/2025, o modelo padrão é o modelo em chinês e não suporta conversão de inglês em fala. Se o texto contiver inglês, nada será falado; para suportar chinês e inglês ao mesmo tempo, é preciso treinar um modelo próprio.

## 1. Requisitos do ambiente básico
Sistema operacional: Windows / Linux / WSL 2

Versão do Python: 3.9 ou superior (ajuste conforme o tutorial oficial do Paddle)

Versão do Paddle: a versão oficial mais recente   ```https://www.paddlepaddle.org.cn/install```

Ferramenta de gerenciamento de dependências: conda ou venv

## 2. Iniciar o serviço paddlespeech
### 1. Clone o código-fonte do repositório oficial do paddlespeech
```bash 
git clone https://github.com/PaddlePaddle/PaddleSpeech.git
```
### 2. Crie o ambiente virtual
```bash

conda create -n paddle_env python=3.10 -y
conda activate paddle_env
```
### 3. Instale o paddle
Conforme a arquitetura da CPU ou da GPU, crie o ambiente seguindo as versões de Python suportadas oficialmente pelo Paddle  
```
https://www.paddlepaddle.org.cn/install
```

### 4. Entre no diretório paddlespeech
```bash
cd PaddleSpeech
```
### 5. Instale o paddlespeech
```bash
pip install pytest-runner -i https://pypi.tuna.tsinghua.edu.cn/simple

# use qualquer um dos comandos abaixo
pip install paddlepaddle -i https://mirror.baidu.com/pypi/simple
pip install paddlespeech -i https://pypi.tuna.tsinghua.edu.cn/simple
```
### 6. Use o comando para baixar automaticamente o modelo de voz
```bash
paddlespeech tts --input "你好，这是一次测试"
```
Este passo baixa automaticamente o cache dos modelos para o diretório local .paddlespeech/models

### 7. Modifique a configuração tts_online_application.yaml
Consulte o diretório ```"PaddleSpeech\demos\streaming_tts_server\conf\tts_online_application.yaml"```
Abra o arquivo ```tts_online_application.yaml``` com um editor e defina ```protocol``` como ```websocket```

### 8. Inicie o serviço
```yaml
paddlespeech_server start --config_file ./demos/streaming_tts_server/conf/tts_online_application.yaml
# comando de início padrão oficial:
paddlespeech_server start --config_file ./conf/tts_online_application.yaml
```
Conforme o diretório real do seu ```tts_online_application.yaml```, ajuste o comando de início; ao ver o log abaixo, o serviço subiu com sucesso
```
Prefix dict has been built successfully.
[2025-08-07 10:03:11,312] [   DEBUG] __init__.py:166 - Prefix dict has been built successfully.
INFO:     Started server process [2298]
INFO:     Waiting for application startup.
INFO:     Application startup complete.
INFO:     Uvicorn running on http://0.0.0.0:8092 (Press CTRL+C to quit)
```

## 3. Modifique o arquivo de configuração do xiaozhi
### 1. ```main/xiaozhi-server/core/providers/tts/paddle_speech.py```

### 2. ```main/xiaozhi-server/data/.config.yaml```
Usando a implantação de módulo único
```yaml
selected_module:
  TTS: PaddleSpeechTTS
TTS:
  PaddleSpeechTTS:
      type: paddle_speech
      protocol: websocket 
      url:  ws://127.0.0.1:8092/paddlespeech/tts/streaming  # URL do serviço de TTS, apontando para o servidor local [websocket padrão ws://127.0.0.1:8092/paddlespeech/tts/streaming]
      spk_id: 0  # ID do locutor; 0 normalmente representa o locutor padrão
      sample_rate: 24000  # taxa de amostragem [websocket padrão 24000, http padrão 0 = seleção automática]
      speed: 1.0  # velocidade da fala; 1.0 é velocidade normal, >1 acelera, <1 desacelera
      volume: 1.0  # volume; 1.0 é volume normal, >1 aumenta, <1 diminui
      save_path:   # caminho para salvar
```
### 3. Inicie o serviço xiaozhi
```py
python app.py
```
Depois de iniciar o `python start.py` em `main/digital-human`, abra `http://127.0.0.1:8006/index.html`, teste a conexão e o envio de mensagem e observe se o lado do paddlespeech emite logs

Referência do log de saída:
```
INFO:     127.0.0.1:44312 - "WebSocket /paddlespeech/tts/streaming" [accepted]
INFO:     connection open
[2025-08-07 11:16:33,355] [    INFO] - sentence: 哈哈，怎么突然找我聊天啦？
[2025-08-07 11:16:33,356] [    INFO] - The durations of audio is: 2.4625 s
[2025-08-07 11:16:33,356] [    INFO] - first response time: 0.1143045425415039 s
[2025-08-07 11:16:33,356] [    INFO] - final response time: 0.4777836799621582 s
[2025-08-07 11:16:33,356] [    INFO] - RTF: 0.19402382942625715
[2025-08-07 11:16:33,356] [    INFO] - Other info: front time: 0.06514096260070801 s, first am infer time: 0.008037090301513672 s, first voc infer time: 0.04112648963928223 s,
[2025-08-07 11:16:33,356] [    INFO] - Complete the synthesis of the audio streams
INFO:     connection closed

```
