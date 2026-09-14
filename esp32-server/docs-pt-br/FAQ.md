# Perguntas Frequentes ❓

### 1. Por que, do que eu falo, o Xiaozhi reconhece muito coreano, japonês e inglês? 🇰🇷

Sugestão: verifique se `models/SenseVoiceSmall` já possui o arquivo `model.pt`; caso não tenha, é preciso baixá-lo — veja aqui [Baixar os arquivos do modelo de reconhecimento de fala](Deployment.md#模型文件)

### 2. Por que aparece "Erro na tarefa de TTS: arquivo não existe"? 📁

Sugestão: verifique se você instalou corretamente as bibliotecas `libopus` e `ffmpeg` com o `conda`.

Caso não tenha instalado, instale:

```
conda install conda-forge::libopus
conda install conda-forge::ffmpeg
```

### 3. O TTS falha com frequência e costuma dar timeout ⏰

Sugestão: se o `EdgeTTS` falha com frequência, primeiro verifique se você está usando proxy (VPN). Se estiver, tente desativá-lo e testar novamente;  
se você usa o TTS Doubao do Volcano Engine, quando as falhas forem frequentes recomenda-se usar a versão paga, pois a versão de teste suporta apenas 2 conexões simultâneas.

### 4. Consigo conectar ao servidor auto-hospedado via Wi-Fi, mas no modo 4G não conecta 🔐

Causa: no firmware do Xiaoge (虾哥), o modo 4G exige conexão segura.

Solução: atualmente há duas formas de resolver. Escolha uma:

1. Alterar o código. Resolva seguindo este vídeo: https://www.bilibili.com/video/BV18MfTYoE85

2. Configurar certificado SSL com nginx. Veja o tutorial: https://icnt94i5ctj4.feishu.cn/docx/GnYOdMNJOoRCljx1ctecsj9cnRe

### 5. Como aumentar a velocidade de resposta das conversas do Xiaozhi? ⚡

A configuração padrão deste projeto é a de menor custo; recomenda-se que iniciantes primeiro usem os modelos gratuitos padrão para resolver o problema de "funcionar", e só depois otimizarem para "funcionar rápido".  
Se quiser aumentar a velocidade de resposta, experimente trocar os componentes. A partir da versão `0.5.2`, o projeto suporta configuração em streaming, o que comparado às versões anteriores reduz o tempo de resposta em cerca de `2.5 segundos`, melhorando significativamente a experiência de uso.

| Nome do módulo | Configuração inicial totalmente gratuita | Configuração em streaming |
|:---:|:---:|:---:|
| ASR (reconhecimento de fala) | FunASR (local) | 👍XunfeiStreamASR (iFlytek em streaming) |
| LLM (modelo grande) | glm-4-flash (Zhipu) | 👍qwen-flash (Alibaba Bailian) |
| VLLM (modelo visual) | glm-4v-flash (Zhipu) | 👍qwen3.5-flash (Alibaba Bailian) |
| TTS (síntese de voz) | EdgeTTS (Microsoft) | 👍HuoshanDoubleStreamTTS (Volcano em streaming) |
| Intent (reconhecimento de intenção) | function_call (chamada de função) | function_call (chamada de função) |
| Memory (função de memória) | mem_local_short (memória local de curto prazo) | mem_local_short (memória local de curto prazo) |

Se você se importa com o tempo gasto por cada componente, consulte o [Relatório de teste de desempenho dos componentes do Xiaozhi](https://github.com/xinnan-tech/xiaozhi-performance-research) e reproduza os testes no seu ambiente seguindo os métodos descritos no relatório.

### 6. Eu falo devagar e nas pausas o Xiaozhi sempre me interrompe 🗣️

Sugestão: encontre a seção abaixo no arquivo de configuração e aumente o valor de `min_silence_duration_ms` (por exemplo, para `1000`):

```yaml
VAD:
  SileroVAD:
    threshold: 0.5
    model_dir: models/snakers4_silero-vad
    min_silence_duration_ms: 700  # se as pausas ao falar forem longas, aumente este valor
```

### 7. Tutoriais de implantação
1. [Como fazer a implantação mais minimalista](Deployment.md)<br/>
2. [Como fazer a implantação com todos os módulos](Deployment_all.md)<br/>
3. [Como implantar o gateway MQTT para habilitar o protocolo MQTT+UDP](mqtt-gateway-integration.md)<br/>
4. [Como baixar automaticamente o código mais recente do projeto, compilar e iniciar](dev-ops-integration.md)<br/>
5. [Como integrar com Nginx](https://github.com/xinnan-tech/xiaozhi-esp32-server/issues/791)<br/>
6. [Como compilar sua própria imagem Docker após modificar o código](docker-build.md)<br/>

### 8. Tutoriais de compilação de firmware
1. [Como compilar o firmware do Xiaozhi você mesmo](firmware-build.md)<br/>
2. [Como alterar o endereço OTA com base no firmware já compilado pelo Xiaoge](firmware-setting.md)<br/>
3. [Como configurar a atualização OTA automática do firmware na implantação de módulo único](ota-upgrade-guide.md)<br/>

### 9. Tutoriais de extensões
1. [Como habilitar o registro por número de celular no Console de Gerenciamento (智控台)](ali-sms-integration.md)<br/>
2. [Como integrar o Home Assistant para controle de casa inteligente](homeassistant-integration.md)<br/>
3. [Como habilitar o modelo de visão para reconhecer objetos por foto](mcp-vision-integration.md)<br/>
4. [Como implantar o endpoint MCP](mcp-endpoint-enable.md)<br/>
5. [Como conectar endpoints MCP](mcp-endpoint-integration.md)<br/>
6. [Como obter informações do dispositivo com um método MCP](mcp-get-device-info.md)<br/>
7. [Como habilitar o reconhecimento de voiceprint (声纹识别)](voiceprint-integration.md)<br/>
8. [Guia de configuração da fonte do plugin de notícias](newsnow_plugin_config.md)<br/>
9. [Guia de integração da base de conhecimento RAGFlow](ragflow-integration.md)<br/>
10. [Como implantar a fonte de contexto](context-provider-integration.md)<br/>
11. [Como integrar a memória inteligente PowerMem](powermem-integration.md)<br/>
12. [Como configurar o plugin de clima para consultar a previsão do tempo](weather-integration.md)<br/>
13. [Como habilitar o plugin de chamada do dispositivo](device-call-guide.md)<br/>
14. [Como habilitar a busca na internet](web-search-integration.md)<br/>

### 10. Tutoriais de humano digital
1. [Como iniciar o humano digital (digital-human)](digital-human-wakeword.md)<br/>
2. [Como implantar o humano digital (digital-human) em um minipc N100](all-in-one-digital-human-setup.md)<br/>

### 11. Tutoriais de clonagem de voz e de implantação de voz local
1. [Como clonar vozes no Console de Gerenciamento](huoshan-streamTTS-voice-cloning.md)<br/>
2. [Como implantar e integrar a voz local index-tts](index-stream-integration.md)<br/>
3. [Como implantar e integrar a voz local fish-speech](fish-speech-integration.md)<br/>
4. [Como implantar e integrar a voz local PaddleSpeech](paddlespeech-deploy.md)<br/>

### 12. Tutoriais de teste de desempenho
1. [Guia de teste de velocidade de cada componente](performance_tester.md)<br/>
2. [Resultados de testes públicos periódicos](https://github.com/xinnan-tech/xiaozhi-performance-research)<br/>

### 13. Para outras dúvidas, fale conosco 💬

Você pode registrar a sua questão nos [issues](https://github.com/xinnan-tech/xiaozhi-esp32-server/issues).
