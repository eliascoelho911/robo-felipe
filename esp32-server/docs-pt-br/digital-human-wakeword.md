# Como iniciar o digital human (digital-human)

## Visão geral

A página de teste integra funcionalidade de despertar por voz de alta precisão baseada em **Sherpa-ONNX**, com suporte a wake words customizadas e detecção em tempo real. Usa um modelo leve de detecção de palavras-chave e oferece resposta em milissegundos.

## Modelo de wake word

### Download do modelo (obrigatório)

**Importante**: o projeto não inclui os arquivos de modelo; é necessário baixá-los e configurá-los com antecedência.

### Endereços oficiais de download dos modelos

- **Lista oficial de modelos**: <https://csukuangfj.github.io/sherpa/onnx/kws/pretrained_models/index.html>
- **Modelo recomendado**: `sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01`

### Passos de download e configuração

#### 1. Baixar o pacote do modelo

```bash
# Método 1: download direto (recomendado)
cd main/digital-human/wakeword_runtime/
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01.tar.bz2

# Extrair
tar xvf sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01.tar.bz2

# Método 2: usando ModelScope
pip install modelscope
python -c "
from modelscope import snapshot_download
snapshot_download('pkufool/sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01', cache_dir='./models')
"
```

#### 2. Configurar os arquivos do modelo

Após o download, o pacote do modelo contém os seguintes arquivos:

```
sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01/
├── encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx    # prioriza velocidade
├── encoder-epoch-12-avg-2-chunk-16-left-64.onnx
├── encoder-epoch-99-avg-1-chunk-16-left-64.int8.onnx    # prioriza velocidade
├── encoder-epoch-99-avg-1-chunk-16-left-64.onnx         # prioriza precisão
├── decoder-epoch-12-avg-2-chunk-16-left-64.onnx
├── decoder-epoch-99-avg-1-chunk-16-left-64.onnx         # prioriza precisão
├── joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx     # prioriza velocidade
├── joiner-epoch-12-avg-2-chunk-16-left-64.onnx
├── joiner-epoch-99-avg-1-chunk-16-left-64.int8.onnx     # prioriza velocidade
├── joiner-epoch-99-avg-1-chunk-16-left-64.onnx          # prioriza precisão
├── tokens.txt                    # Tabela de mapeamento de tokens (obrigatório)
├── keywords_raw.txt              # Pode vir no pacote do modelo (opcional, o runtime não depende dele)
├── keywords.txt                  # Já pronto
├── test_wavs/                    # Áudios de teste (opcional)
├── configuration.json            # Metadados do modelo (opcional)
└── README.md                     # Documentação (opcional)
```

#### 3. Escolher o esquema de configuração

**Esquema 1: priorizando a precisão (recomendado)**

```bash
cd sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01

# Cria o diretório de modelos
mkdir -p ../models

# Copia o trio fp32 do epoch-99 que prioriza a precisão
cp encoder-epoch-99-avg-1-chunk-16-left-64.onnx ../models/encoder.onnx
cp decoder-epoch-99-avg-1-chunk-16-left-64.onnx ../models/decoder.onnx
cp joiner-epoch-99-avg-1-chunk-16-left-64.onnx ../models/joiner.onnx

# Copia os arquivos complementares
cp tokens.txt ../models/tokens.txt
# keywords_raw.txt, se vier no pacote do modelo, pode ser mantido; o runtime não depende dele
```

**Esquema 2: priorizando a velocidade**

```bash
cd sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01

# Cria o diretório de modelos
mkdir -p ../models

# Copia o trio int8 do epoch-99 que prioriza a velocidade
cp encoder-epoch-99-avg-1-chunk-16-left-64.int8.onnx ../models/encoder.onnx
cp decoder-epoch-99-avg-1-chunk-16-left-64.onnx ../models/decoder.onnx
cp joiner-epoch-99-avg-1-chunk-16-left-64.int8.onnx ../models/joiner.onnx

# Copia os arquivos complementares
cp tokens.txt ../models/tokens.txt
```

**Pontos de atenção**:

- **Não misture fp32 com int8**: os três arquivos de modelo devem manter a mesma precisão
- **Prefira o epoch-99**: tem treinamento mais completo que o epoch-12 e maior precisão
- **Arquivos obrigatórios**: `encoder.onnx` + `decoder.onnx` + `joiner.onnx` + `tokens.txt` + `keywords.txt`

### Estrutura final dos arquivos de modelo

Após a configuração, os arquivos de modelo devem ficar no diretório `wakeword_runtime/models/`, com caminho completo `main/digital-human/wakeword_runtime/models/`:

```
wakeword_runtime/models/
├── encoder.onnx      # Modelo encoder (após renomear)
├── decoder.onnx      # Modelo decoder (após renomear)
├── joiner.onnx       # Modelo joiner (após renomear)
├── tokens.txt        # Tabela de mapeamento de tokens pinyin (versão de 228 linhas)
├── keywords.txt      # Arquivo de configuração de palavras-chave (gerado automaticamente na primeira inicialização)
└── keywords_raw.txt  # Opcional, o runtime não depende dele
```

## Como iniciar

Execute no diretório `main/digital-human`:

```bash
pip install -r wakeword_runtime/requirements.txt
python start.py
```

Endereços padrão após a inicialização:

- Página: `http://127.0.0.1:8006/index.html`
- Event bridge: `ws://127.0.0.1:8006/wakeword-ws`
- Health check: `http://127.0.0.1:8006/health`

Como parar:

- Pressione `Ctrl+C` no terminal onde está rodando
- Isso interrompe ao mesmo tempo o serviço de página estática, o event bridge e o processo de detecção de wake word

## Sobre o arquivo de configuração

O arquivo de configuração fica em [main/digital-human/wakeword_runtime/config.json](../upstream/main/digital-human/wakeword_runtime/config.json).

Principais opções de configuração atuais:

```json
{
  "wakeword": {
    "enabled": true
  },
  "model_dir": "models",
  "audio": {
    "input_device": null,
    "sample_rate": 16000,
    "channels": 1
  },
  "detector": {
    "num_threads": 4,
    "provider": "cpu",
    "max_active_paths": 2,
    "keywords_score": 1.8,
    "keywords_threshold": 0.1,
    "num_trailing_blanks": 1,
    "cooldown_seconds": 1.5
  },
  "logging": {
    "level": "INFO",
    "dir": "logs",
    "file": "wakeword-runtime.log"
  }
}
```

Significado de cada campo:

| Parâmetro | Descrição |
| --- | --- |
| `wakeword.enabled` | Habilita ou não a detecção local de wake word |
| `model_dir` | Diretório onde ficam o modelo e a tabela de tokens |
| `audio.input_device` | Dispositivo de entrada de microfone; por padrão usa o dispositivo padrão do sistema |
| `audio.sample_rate` | Taxa de amostragem; padrão `16000` |
| `audio.channels` | Número de canais; padrão `1` |
| `detector.num_threads` | Número de threads do detector |
| `detector.provider` | Provider de inferência; atualmente geralmente `cpu` |
| `detector.max_active_paths` | Número de caminhos de busca |
| `detector.keywords_score` | Pontuação de reforço das palavras-chave |
| `detector.keywords_threshold` | Limiar de detecção |
| `detector.num_trailing_blanks` | Quantidade de blanks à direita |
| `detector.cooldown_seconds` | Tempo de cooldown entre disparos consecutivos |
| `logging.level` | Nível de log |
| `logging.dir` | Diretório de logs |
| `logging.file` | Nome do arquivo de log |

## Fluxo de uso recomendado

### Primeiro uso

1. Prepare os arquivos de modelo no diretório `models/` e o `tokens.txt`
2. Confirme que `models/keywords.txt` existe
3. No diretório `digital-human`, execute `python start.py`
4. Abra `http://127.0.0.1:8006/index.html` no navegador
5. Acesse a página de configurações e verifique a configuração de "Wake word"

### Alterar a wake word

1. Abra as configurações da página do digital human
2. Vá para a aba "Wake word"
3. Altere o estado de ativação ou a lista de wake words
4. Clique em "Aplicar wake word"
5. Decida, conforme o aviso exibido, se deseja reiniciar imediatamente

### Desabilitar a wake word

1. Mude "Habilitar detecção local de wake word" para desabilitado
2. Clique em "Aplicar wake word"
3. Recomenda-se reiniciar uma vez imediatamente

Após desabilitar:

- A página e o event bridge continuam disponíveis
- A detecção de wake word não continua em execução
