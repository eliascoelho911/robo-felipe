# 03: xiaozhi-server config + API keys + docker

**What to build:** O `esp32-server/` (fork do `xinnan-tech/xiaozhi-esp32-server`)
está configurado e rodando via Docker Compose. O `config/config.yaml`
existente já tem `selected_module` completo (GroqASR, OpenAILLM/gpt-4o-mini,
EdgeTTS, ChatGLMVLLM, SileroVAD, nomem, function_call), persona Robô Felipe
pt-BR, `exit_commands` pt-BR, `wakeup_words` ["Felipe","ei Felipe"], e
`CORE_HTTP_URL` apontando para o Core. Os placeholders de API keys
(`SUBSTITUA_POR_*_API_KEY`) são preenchidos com secrets reais (OpenAI,
Groq, ChatGLM). O `docker-compose.override.yml` sobe o servidor com as
variáveis de ambiente corretas.

O handshake WSS do protocolo xiaozhi deve responder: o servidor escuta
em `ws://localhost:8000/xiaozhi/v1/` (voz) e `http://localhost:8003` (visão
+ OTA). O `server.auth.enabled` pode ficar `false` no MVP (LAN/localhost).

Este ticket é paralelo ao 01 (não depende do Core) — só precisa do servidor
de voz rodando e respondendo handshake.

**Blocked by:** None (can start immediately, paralelo com 01).

**Status:** ready-for-agent

- [ ] API keys reais preenchidas em `esp32-server/config/config.yaml` (ou via env vars no docker-compose): OpenAI (gpt-4o-mini), Groq (whisper-large-v3-turbo), ChatGLM (glm-4v-flash).
- [ ] `docker compose -f esp32-server/docker-compose.override.yml up` sobe o xiaozhi-server sem erros.
- [ ] Log de startup mostra: "WebSocket地址是 ws://localhost:8000/xiaozhi/v1/", "视觉分析接口是 http://localhost:8003/mcp/vision/explain".
- [ ] `selected_module` confirma: ASR=GroqASR, LLM=OpenAILLM (gpt-4o-mini), TTS=EdgeTTS, VLLM=ChatGLMVLLM, VAD=SileroVAD, Memory=nomem, Intent=function_call.
- [ ] System prompt persona Robô Felipe pt-BR ativo (não a default "小智" taiwanesa).
- [ ] `exit_commands` = ["tchau", "até logo", "adeus"].
- [ ] Demoable: `wscat ws://localhost:8000/xiaozhi/v1/` recebe handshake `{"type":"hello","transport":"websocket","session_id":"..."}`.
- [ ] Demoable: enviar áudio Opus (push-to-talk) recebe transcrição + resposta TTS (validação manual com cliente de teste).
