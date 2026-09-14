# Guia de uso do plugin de chamada entre dispositivos

## Visão geral

A função de chamada entre dispositivos permite a comunicação bidirecional, pelo canal de voz/dados, entre dois dispositivos já configurados. Quando o dispositivo A chama o dispositivo B, o sistema realiza o fluxo a seguir:

```
Dispositivo A → Verificação de autorização → Gateway MQTT → Despertar remoto do dispositivo B → Estabelecimento de conexão → Chamada estabelecida
```
## Pré-requisitos para usar esta função
1. Você precisa ter pelo menos dois dispositivos, e o modelo de cada um deve ser `ESP32-S3`, pois apenas o `ESP32-S3` suporta a função de despertar remoto.
2. Seus dispositivos precisam ter `dois microfones`. Se o seu dispositivo tiver apenas `um microfone`, você ainda pode experimentar a função, mas haverá uma forte sensação de travamento.
3. Você deve implantar este projeto usando a [implantação completa (todos os módulos)](Deployment_all.md), pois precisará do Console de Gerenciamento (智控台) para gerenciar as permissões e a comunicação dos dispositivos.
4. Você deve instalar e configurar o [serviço de gateway MQTT](mqtt-gateway-integration.md) em versão posterior a `27 de maio de 2026`; se o gateway MQTT já estiver implantado, confirme que a versão do código é posterior a `27 de maio de 2026`.

Estas são as condições obrigatórias para usar esta função; detalharemos cada uma a seguir.

## Passos de configuração

### Primeiro passo: ativar a função de agenda de contatos (通讯录)

1. Confirme que a versão do seu Console de Gerenciamento (智控台) é `0.9.4` ou superior.
2. Faça login no painel do Console de Gerenciamento
3. Entre em **Configuração de funções do sistema**
4. Na lista de funções à esquerda, marque **Agenda de contatos (通讯录)**
5. Clique em **Salvar configuração** para confirmar

### Segundo passo: configurar as permissões de chamada entre dispositivos

1. No menu superior do Console de Gerenciamento, clique em **Agenda de contatos (通讯录)**
2. Na lista de dispositivos sob os agentes à esquerda, selecione o seu dispositivo A (a busca aceita endereço MAC ou apelido)
3. No painel de detalhes à direita, localize a configuração de apelido do dispositivo B de destino, por exemplo **"Xiao Wang"**
4. Marque a caixa de seleção **Permissão de chamada** do dispositivo B
5. Clique em **Salvar**

**Observação sobre autorização mútua:** para que o dispositivo A e o dispositivo B se comuniquem entre si, é preciso configurar a permissão do outro lado em cada Console de Gerenciamento. Por exemplo:

- Ao marcar o dispositivo B na configuração do dispositivo A → o dispositivo A pode se comunicar com o dispositivo B
- Ao marcar o dispositivo A na configuração do dispositivo B → o dispositivo B pode se comunicar com o dispositivo A

### Terceiro passo: adicionar a ferramenta de chamada na configuração do agente

1. No menu superior do Console de Gerenciamento, clique em **Gerenciamento de agentes**
2. No agente em que você acabou de configurar os contatos do dispositivo, clique em **Editar papel**
3. No painel de detalhes à direita, clique em **Editar funções**
4. Marque a ferramenta **Chamada de dispositivo para dispositivo (设备呼叫设备)**
5. Clique em **Salvar configuração** para confirmar
6. Clique novamente em **Salvar configuração** na tela externa e reinicie o dispositivo em seguida

### Quarto passo: adicionar a ferramenta de despertar remoto no lado do firmware

1. Partindo do código do [xiaozhi-esp32](https://github.com/78/xiaozhi-esp32), adicione a ferramenta MCP de despertar remoto; as versões suportadas vão da 2.1.0 à 2.2.6 (versão de 29 de maio de 2026)
2. No arquivo application.h, adicione a declaração da função de despertar remoto
    ```cpp
    void RemoteWakeup(const std::string& reason);
    ```
3. No arquivo application.cc, adicione a função de despertar remoto
    ```cpp
    void Application::RemoteWakeup(const std::string& reason){
        if (!protocol_) {
            return;
        }

        auto state = GetDeviceState();
        
        if (state == kDeviceStateIdle) {
            audio_service_.EncodeWakeWord();

            if (!protocol_->IsAudioChannelOpened()) {
                SetDeviceState(kDeviceStateConnecting);
                if (!protocol_->OpenAudioChannel()) {
                    audio_service_.EnableWakeWordDetection(true);
                    return;
                }
            }
            std::string wake_word = reason;
    #if CONFIG_USE_AFE_WAKE_WORD || CONFIG_USE_CUSTOM_WAKE_WORD
            // Encode and send the wake word data to the server
            while (auto packet = audio_service_.PopWakeWordPacket()) {
                protocol_->SendAudio(std::move(packet));
            }
            // Set the chat state to wake word detected
            protocol_->SendWakeWordDetected(wake_word);
            SetListeningMode(aec_mode_ == kAecOff ? kListeningModeAutoStop : kListeningModeRealtime);
    #else
            // Set flag to play popup sound after state changes to listening
            // (PlaySound here would be cleared by ResetDecoder in EnableVoiceProcessing)
            play_popup_on_listening_ = true;
            SetListeningMode(aec_mode_ == kAecOff ? kListeningModeAutoStop : kListeningModeRealtime);
    #endif
        } else if (state == kDeviceStateSpeaking) {
            AbortSpeaking(kAbortReasonWakeWordDetected);
            SetDeviceState(kDeviceStateIdle);
        } else if (state == kDeviceStateActivating) {
            SetDeviceState(kDeviceStateIdle);
        }
    }
    ```
4. No arquivo mcp_server.cc, adicione a ferramenta de despertar remoto
    ```cpp
    AddUserOnlyTool("self.remote_wakeup", "Remote wakeup function with configurable parameters",
        PropertyList({
            Property("reason", kPropertyTypeString, "Wakeup reason"),
        }),
        [this](const PropertyList& properties) -> ReturnValue {
            std::string reason = properties["reason"].value<std::string>();
            ESP_LOGI(TAG, "Wakeup reason=%s", reason.c_str());
            auto& app = Application::GetInstance();
            app.RemoteWakeup(reason);
            return true;
    ```
5. Conclua a gravação do firmware seguindo o [guia de compilação e gravação do firmware](firmware-build.md)
6. Independentemente de o seu dispositivo ter um ou dois microfones, ative a função AEC na etapa de compilação!
7. Independentemente de o seu dispositivo ter um ou dois microfones, ative a função AEC na etapa de compilação!
8. Independentemente de o seu dispositivo ter um ou dois microfones, ative a função AEC na etapa de compilação!

### Quinto passo: configurar o serviço de gateway MQTT

1. Implante o serviço de gateway MQTT; consulte o [documento de integração do gateway MQTT](mqtt-gateway-integration.md)
2. Se ele já estiver implantado, confirme que a versão do código é a de 27 de maio de 2026

## Explicação do fluxo de chamada

Prepare dois dispositivos; depois de configurar as permissões de comunicação no Console de Gerenciamento e adicionar a ferramenta de chamada ao agente, diga a um dos xiaozhi: "ligar para XXX", e observe se o dispositivo B responde.

## Perguntas frequentes

### P: O dispositivo B não responde à chamada?

- Verifique se o dispositivo B está online (status do dispositivo no Console de Gerenciamento)
- Confirme que o firmware do dispositivo B integrou corretamente a ferramenta de despertar remoto
- Verifique se a conexão com o gateway MQTT está normal
- Valide se a configuração de permissões mútuas está completa

### P: Aparece "sem permissão de chamada"?

- No Console de Gerenciamento, confirme que o dispositivo A marcou a permissão de chamada do dispositivo B
- Confirme que a configuração foi salva (não apenas alterada, sem salvar)

### P: Como confirmar que a função de agenda de contatos está ativada?

- Se o menu superior do Console de Gerenciamento exibir a entrada "Agenda de contatos", a função está ativada

### P: Peço para ligar para "Zhang Shan" (张山), mas ele sempre reconhece "Zhang San" (张三). O que fazer?
- Consulte a documentação do serviço ASR que você usa e confirme se ele suporta reconhecimento de hotwords (palavras-chave personalizadas).
- Se você usa o `FunASRServer`, adicione "张山" no `arquivo de hotwords` (热词文件) dentro do contêiner e reinicie o contêiner.
- Se você usa o serviço do `Volcano Engine` (火山引擎), adicione o `arquivo de hotwords` no `console do Volcano Engine`, depois volte à `página de configuração de modelos` do Console de Gerenciamento e configure o `nome do arquivo de hotwords` no TTS do Volcano Engine.
