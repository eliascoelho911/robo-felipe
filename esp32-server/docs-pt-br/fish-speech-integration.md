Entre no AutoDL e alugue uma máquina
Escolha a imagem:
```
PyTorch / 2.1.0 / 3.10(ubuntu22.04) / cuda 12.1
```

Depois de ligar a máquina, configure a aceleração acadêmica
```
source /etc/network_turbo
```

Entre no diretório de trabalho
```
cd autodl-tmp/
```

Clone o projeto
```
git clone https://gitclone.com/github.com/fishaudio/fish-speech.git ; cd fish-speech
```

Instale as dependências
```
pip install -e.
```

Se der erro, instale o portaudio
```
apt-get install portaudio19-dev -y
```

Depois da instalação, execute
```
pip install torch==2.3.1 torchvision==0.18.1 torchaudio==2.3.1 --index-url https://download.pytorch.org/whl/cu121
```

Baixe o modelo
```
cd tools
python download_models.py 
```

Depois de baixar o modelo, inicie o servidor da API
```
python -m tools.api_server --listen 0.0.0.0:6006 
```

Depois, use o navegador para abrir a página de instâncias do AutoDL
```
https://autodl.com/console/instance/list
```

Como na imagem abaixo, clique no botão `Serviço Personalizado` (自定义服务) da máquina que você acabou de criar para habilitar o encaminhamento de porta
![Serviço personalizado](../upstream/docs/images/fishspeech/autodl-01.png)

Depois de configurar o encaminhamento de porta, abra o endereço `http://localhost:6006/` no seu computador local e o endpoint do fish-speech estará acessível
![Prévia do serviço](../upstream/docs/images/fishspeech/autodl-02.png)


Se o seu deployment é de módulo único, a configuração essencial é a seguinte
```
selected_module:
  TTS: FishSpeech
TTS:
  FishSpeech:
    reference_audio: ["config/assets/wakeup_words.wav",]
    reference_text: ["哈啰啊，我是小智啦，声音好听的台湾女孩一枚，超开心认识你耶，最近在忙啥，别忘了给我来点有趣的料哦，我超爱听八卦的啦",]
    api_key: "123"
    api_url: "http://127.0.0.1:6006/v1/tts"
```

Depois, reinicie o serviço
