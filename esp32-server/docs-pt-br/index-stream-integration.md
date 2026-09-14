# Guia de Uso do IndexStreamTTS

## Preparação do ambiente
### 1. Clone o projeto
```bash
git clone https://github.com/Ksuriuri/index-tts-vllm.git
```
Entre no diretório descompactado
```bash
cd index-tts-vllm
```
Alterne para a versão especificada (usando a versão histórica compatível com o VLLM-0.10.2)
```bash
git checkout 224e8d5e5c8f66801845c66b30fa765328fd0be3
```

### 2. Crie e ative o ambiente conda
```bash
conda create -n index-tts-vllm python=3.12
conda activate index-tts-vllm
```

### 3. Instale o PyTorch — a versão necessária é a 2.8.0 (mais recente)
#### Verifique a versão máxima suportada pela GPU e a versão realmente instalada
```bash
nvidia-smi
nvcc --version
```
#### Versão máxima de CUDA suportada pelo driver
```bash
CUDA Version: 12.8
```
#### Versão realmente instalada do compilador CUDA
```bash
Cuda compilation tools, release 12.8, V12.8.89
```
#### O comando de instalação correspondente (por padrão o pytorch fornece a versão de driver 12.8)
```bash
pip install torch torchvision
```
É necessário o pytorch versão 2.8.0 (correspondente ao vllm 0.10.2); para as instruções específicas de instalação, consulte: [site oficial do pytorch](https://pytorch.org/get-started/locally/)

### 4. Instale as dependências
```bash
pip install -r requirements.txt
```

### 5. Baixe os pesos do modelo
### Opção 1: baixar os arquivos de pesos oficiais e depois convertê-los
Estes são os arquivos de pesos oficiais; basta baixá-los para qualquer caminho local; são suportados os pesos do IndexTTS-1.5
| HuggingFace                                                   | ModelScope                                                          |
|---------------------------------------------------------------|---------------------------------------------------------------------|
| [IndexTTS](https://huggingface.co/IndexTeam/Index-TTS)        | [IndexTTS](https://modelscope.cn/models/IndexTeam/Index-TTS)        |
| [IndexTTS-1.5](https://huggingface.co/IndexTeam/IndexTTS-1.5) | [IndexTTS-1.5](https://modelscope.cn/models/IndexTeam/IndexTTS-1.5) |

A seguir, usamos o método de download do ModelScope como exemplo
#### Atenção: o git precisa estar instalado e com o lfs inicializado/habilitado (se já estiver instalado, pode pular)
```bash
sudo apt-get install git-lfs
git lfs install
```
Crie o diretório do modelo e faça o clone do modelo
```bash
mkdir model_dir
cd model_dir
git clone https://www.modelscope.cn/IndexTeam/IndexTTS-1.5.git
```

#### Conversão dos pesos do modelo
```bash
bash convert_hf_format.sh /path/to/your/model_dir
```
Por exemplo: se o modelo IndexTTS-1.5 que você baixou está no diretório model_dir, execute o comando abaixo
```bash
bash convert_hf_format.sh model_dir/IndexTTS-1.5
```
Esta operação converte os pesos oficiais do modelo para um formato compatível com a biblioteca transformers, salvando-os na pasta vllm dentro do caminho dos pesos do modelo, para facilitar o carregamento dos pesos pela biblioteca vllm depois

### 6. Ajuste a API para adaptá-la a este projeto
Como os dados retornados pela API não são compatíveis com o projeto, é preciso ajustá-la para que retorne diretamente os dados de áudio
```bash
vi api_server.py
```
```bash
@app.post("/tts", responses={
    200: {"content": {"application/octet-stream": {}}},
    500: {"content": {"application/json": {}}}
})
async def tts_api(request: Request):
    try:
        data = await request.json()
        text = data["text"]
        character = data["character"]

        global tts
        sr, wav = await tts.infer_with_ref_audio_embed(character, text)

        return Response(content=wav.tobytes(), media_type="application/octet-stream")

    except Exception as ex:
        tb_str = ''.join(traceback.format_exception(type(ex), ex, ex.__traceback__))
        print(tb_str)
        return JSONResponse(
            status_code=500,
            content={
                "status": "error",
                "error": str(tb_str)
            }
        )
```

### 7. Escreva o script sh de inicialização (atenção: execute-o no ambiente conda correspondente)
```bash
vi start_api.sh
```
### Cole o conteúdo abaixo e digite :wq para salvar
#### No script, modifique /home/system/index-tts-vllm/model_dir/IndexTTS-1.5 para o caminho real da sua instalação
```bash
# Ativar o ambiente conda
conda activate index-tts-vllm
echo "激活项目conda环境"
sleep 2
# Localizar o PID do processo que ocupa a porta 11996
PID_VLLM=$(sudo netstat -tulnp | grep 11996 | awk '{print $7}' | cut -d'/' -f1)

# Verificar se o PID foi encontrado
if [ -z "$PID_VLLM" ]; then
  echo "没有找到占用11996端口的进程"
else
  echo "找到占用11996端口的进程，进程号为: $PID_VLLM"
  # Tentar primeiro um kill normal e aguardar 2 segundos
  kill $PID_VLLM
  sleep 2
  # Verificar se o processo ainda existe
  if ps -p $PID_VLLM > /dev/null; then
    echo "进程仍在运行，强制终止..."
    kill -9 $PID_VLLM
  fi
  echo "已终止进程 $PID_VLLM"
fi

# Localizar processos VLLM::EngineCore em execução
GPU_PIDS=$(ps aux | grep -E "VLLM|EngineCore" | grep -v grep | awk '{print $2}')

# Verificar se o PID foi encontrado
if [ -z "$GPU_PIDS" ]; then
  echo "没有找到VLLM相关进程"
else
  echo "找到VLLM相关进程，进程号为: $GPU_PIDS"
  # Tentar primeiro um kill normal e aguardar 2 segundos
  kill $GPU_PIDS
  sleep 2
  # Verificar se o processo ainda existe
  if ps -p $GPU_PIDS > /dev/null; then
    echo "进程仍在运行，强制终止..."
    kill -9 $GPU_PIDS
  fi
  echo "已终止进程 $GPU_PIDS"
fi

# Criar o diretório tmp (se não existir)
mkdir -p tmp

# Executar api_server.py em segundo plano, com log redirecionado para tmp/server.log
nohup python api_server.py --model_dir /home/system/index-tts-vllm/model_dir/IndexTTS-1.5 --port 11996 > tmp/server.log 2>&1 &
echo "api_server.py 已在后台运行，日志请查看 tmp/server.log"
```
Dê permissão de execução ao script e execute-o
```bash
chmod +x start_api.sh
./start_api.sh
```
Os logs serão gravados em tmp/server.log; para acompanhar os logs, use o comando abaixo
```bash
tail -f tmp/server.log
```
Se a memória de vídeo for suficiente, você pode adicionar ao script o parâmetro de inicialização ----gpu_memory_utilization para ajustar a proporção de ocupação da VRAM; o valor padrão é 0.25

## Configuração de vozes
O index-tts-vllm permite registrar vozes personalizadas por meio de arquivo de configuração, com suporte a voz única e vozes misturadas.
Configure as vozes personalizadas no arquivo assets/speaker.json na raiz do projeto
### Formato de configuração
```bash
{
    "nome_do_falante_1": [
        "caminho/do/arquivo_de_audio_1.wav",
        "caminho/do/arquivo_de_audio_2.wav"
    ],
    "nome_do_falante_2": [
        "caminho/do/arquivo_de_audio_3.wav"
    ]
}
```
### Atenção (após configurar a persona, é preciso reiniciar o serviço para registrar as vozes)
Depois de adicionar, é preciso cadastrar o falante correspondente no Console de Gerenciamento (智控台) (no caso de módulo único, troque o voice correspondente)
