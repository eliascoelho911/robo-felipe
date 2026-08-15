# Provedores de Voz Self-Hosted para o Robô Felipe (i5/8GB, CPU only)

> **Ticket de pesquisa** (issue 09) que investiga a alternativa self-hosted à
> stack cloud do [ticket 01](./cloud-voice-providers.md) (Deepgram +
> gpt-4o-mini + Azure TTS, ~$0,10–0,60/mês, < 4 s end-to-end). A arquitetura
> base (ADR-002: relay smartphone; ADR-006: PCM 16 kHz/16-bit/mono, KWS local)
> permanece — a diferença é *para onde* o relay orquestra os serviços de voz:
> nuvem vs **box i5/8GB na LAN**. Não reverte o ticket 01; adiciona uma opção
> de produto (privacidade total, offline, hobby).

| | |
|---|---|
| **Data da pesquisa** | 2026-08-15 |
| **Status** | Concluída — aguardando decisão de produto |
| **Alimenta** | Issue 09 (resolução), ADR-006 (provedor), ADR-002 (relay) |
| **Confiança de benchmarks** | **Média.** Benchmarks de CPU confirmados em fontes primárias: faster-whisper (i7-12700K) e Llama 3.2 (ARM CPU/OnePlus 12 via ExecuTorch). **Números em i5 (4ª–8ª gen) são extrapolados** desses pontos e marcados **estimado — verificar**. Modelos/vozes PT-BR e suporte multilíngue verificados em cards oficiais (HuggingFace) fetchados em 2026-08-15. |
| **Risco maior** | **Qualidade de PT-BR em LLMs pequenos (1–4B)** é o ponto mais incerto — mitigado por modelos com suporte oficial a Português (Qwen2.5, Llama 3.2). |

## Metodologia e fontes

Documentação e cards de modelo verificados em **fontes primárias** (fetchadas
em 2026-08-15): READMEs do GitHub (whisper.cpp, openai/whisper, faster-whisper,
vosk-api, ollama, llama.cpp, sherpa-onnx, coqui-ai/TTS, rhasspy/piper);
`alphacephei.com/vosk/models` (WER e tamanhos); `rhasspy/piper` `VOICES.md`
(lista de vozes PT-BR); cards do HuggingFace (Qwen/Qwen2.5-3B-Instruct,
microsoft/Phi-3-mini-4k-instruct, meta-llama/Llama-3.2-3B-Instruct,
facebook/mms-tts, distil-whisper); e docs via Context7 (faster-whisper,
ollama, piper). Onde o número veio de hardware diferente do alvo (i7-12700K,
OnePlus 12/ARM), **escalonei para i5 4-core e marquei como estimado**. Não
fabriquei benchmarks: se não encontrei, escrevi "estimado — verificar" com o
raciocínio. Páginas JS-renderizadas ou com timeout (pricing de nuvem, alguns
benchmarks comunitários) não foram usadas; **prefira re-validar empiricamente
no hardware real antes de amarrar** (ver seção 9).

## Premissas de hardware

- **CPU:** Intel i5 (~4ª–8ª gen), **2–4 cores, ~2,4–3,4 GHz**, **sem GPU CUDA**.
  Instrução vetorial: AVX (4ª gen), AVX2 (4ª+ gen Haswell), **não** AVX512/AMX
  (só Xeon Sapphire Rapids). A iGPU integrada serve só para QuickSync —
  **não acelera ML**. `llama.cpp` usa AVX2 para CPU; `whisper.cpp`/OpenBLAS
  também.
- **RAM:** **8 GB total, ~6 GB livres** após SO. Modelos precisam caber com
  folga para ASR/NLP/TTS residentes ou em pipeline serial.
- **Disco:** SSD, espaço para ~5–10 GB de modelos.
- **Rede:** **LAN** — robô + relay (smartphone) + box na mesma rede. **Zero
  latência de WAN** (~5 ms de hop vs ~100–300 ms de RTT de nuvem por hop).
- **Box já existe** e presume-se sempre ligado (home server/NAS) — o custo
  marginal de adicionar voz é ~eletricidade (ver seção 8).

## Perfil de uso (mesmo do ticket 01)

- **~50 comandos/dia**, **~1.500/mês**. Enunciado **~3–5 s** → **~100 min de
  áudio/mês**. LLM: prompt ~150 tokens + resposta curta ~25 tokens (≤12
  palavras, constrain no system prompt). TTS: ~280 chars/resposta → **~420k
  chars/mês**. **Alvo de latência: < 4 s** ponta-a-ponta ("Hey Felipe,
  [comando]" → voz). **Idioma: PT-BR**; usuário: criança de 8 anos (voz aguda,
  vocabulário simples, sotaque possivelmente irregular).

---

## 1. ASR (Speech-to-Text) self-hosted, PT-BR, CPU

### Tabela comparativa

| Opção | PT-BR | Latência em i5 CPU (enunciado ~3 s) | RAM (rodando) | Tamanho do modelo | Streaming | Notas |
|---|---|---|---|---|---|---|
| **faster-whisper** (CTranslate2, `small` **int8**) | ✅ Multilíngue; `language="pt"` enviesa p/ PT-BR com prompt brasileiro | **~0,8–1,2 s** (estimado — ver raciocínio) | **~1,5 GB** (small int8) | 466 MB (small) | ⚠️ Batch por janela; **streaming parcial** via `Whisper-Streaming`/`WhisperLive` (comunidade) | **Melhor precisão PT-BR por MB de RAM.** VAD Silero embutido. Benchmark confirmado: small int8 em **i7-12700K/8 threads = 1m42s p/ 13 min de áudio (RTF ≈ 0,13, ~7,6× tempo real), 1477 MB** ([README faster-whisper](https://github.com/SYSTRAN/faster-whisper)). |
| **whisper.cpp** (`base`/`small` Q5_0) | ✅ Mesmo modelo multilíngue do Whisper | **~0,6–1,0 s** (estimado; `base` Q5_0 ~1,6× mais rápido que `small`) | **~388 MB** (base Q5_0) / **~852 MB** (small) | base 142 MB, small 466 MB | ✅ `whisper-stream` (mic real-time, janelas de 0,5 s); `whisper-server` (HTTP, API compatível c/ OpenAI) | Port C++ puro, AVX/OpenBLAS, **zero dependência**. `whisper-server` dá endpoint HTTP de ASR pronto p/ o relay chamar na LAN. RAM table oficial: tiny ~273 MB, base ~388 MB, small ~852 MB, medium ~2,1 GB. |
| **Vosk** (`vosk-model-small-pt-0.3`) | ⚠️ Suporta PT, **mas precisão ruim** | **~0,1–0,3 s** (sub-tempo-real, streaming nativo) | **~300 MB** (small, ~31 MB em disco) | **31 MB** (small) | ✅ **Streaming nativo**, zero-latência, reconfigurável | **WER PT-BR = 68,92 (coraa dev) / 32,60 (cv test)** — inaceitável para voz de criança. Modelo grande `pt-fb` (1,6 GB): WER 54/27. **Vosk é rápido e minúsculo, mas a qualidade PT-BR é a pior do grupo.** |
| **Sherpa-ONNX** (Whisper ONNX, int8) | ✅ Via Whisper multilíngue (`pt`) | ~0,7–1,1 s (estimado, similar faster-whisper) | ~0,7–1,0 GB (Whisper small int8) | ~466 MB (small) | ✅ Streaming (Zipformer) + não-streaming (Whisper); **VAD + ASR + TTS + KWS no mesmo runtime** | "Next-gen Kaldi" em ONNX, **x86_64**, 12 linguagens de binding (C++, Python, Java, Kotlin…). Vantagem: **um toolkit só** faz ASR (Whisper) + TTS (Piper) + VAD (Silero) na LAN. PT-BR de streaming via Zipformer **não listado** (apenas Whisper não-streaming). |
| **Coqui STT** (fork Mozilla DeepSpeech) | ⚠️ Sem modelo PT-BR decente; **sem manutenção ativa** (Coqui encerrou 2024) | — | — | — | — | **Não é candidato sério.** Sem modelo PT-BR competitivo e o projeto STT legado está estagnado. Vosk/Whisper dominam o espaço offline. |
| **distil-whisper** | ❌ **Inglês apenas** ("currently only available for English speech recognition") | — | — | — | — | **Eliminado para PT-BR.** Distil é 6× mais rápido que large-v3, mas só EN. |

### Notas (ASR)

- **Recomendado: faster-whisper `small` int8** como padrão de qualidade, ou
  **whisper.cpp `base` Q5_0** se RAM/latência apertar. Ambos usam o mesmo
  modelo Whisper multilíngue, então **PT-BR = mesma qualidade do Whisper na
  nuvem** (OpenAI `whisper-1` / `gpt-4o-mini-transcribe` rodam o mesmo
  checkpoint large/small). Vantagem decisiva do self-hosted aqui: **a precisão
  de ASR não degrada vs cloud** — só a velocidade (CPU vs GPU) e a latência
  de rede melhoram (LAN).
- **Raciocínio da estimativa de latência:** o benchmark confirmado é small int8
  em **i7-12700K (12C/20T)** = 102 s p/ 13 min de áudio → ~0,4 s p/ 3 s de
  áudio naquele i7. Um i5 4-core antigo é ~2–3× mais lento em throughput
  multithread → **estimado ~0,8–1,2 s** para enunciado de 3 s (incl. encoder +
  decoder + overhead Python). Para `base` (menor), ~0,6 s. **Verificar no
  hardware real** — é o número mais incerto da ASR.
- **Whisper e voz de criança:** mesmos gotchas do ticket 01 — F0 alto,
  articulação imatura, pausas no meio da palavra. Mitigações no self-hosted:
  `vad_filter=True` (Silero embutido no faster-whisper) corta silêncio antes do
  encoder; `condition_on_previous_text=False` reduz alucinação em silêncio; o
  **VAD local do ESP32 (ADR-005)** já entrega só o trecho falado. Whisper
  small/base erra mais que large em voz infantil, mas aceita bem vocabulário
  de comandos curtos do robô (*ande, dança, senta, vira, pula, late*).
- **Streaming parcial em CPU:** faster-whisper é batch-centric (gera só ao
  fim da janela), mas há wrappers comunitários listados no README
  (`Whisper-Streaming`/`WhisperLive`) que implementam política de streaming
  auto-adaptativa. Para o robô, o fluxo natural é: ESP32 captura janela de
  ~3–5 s após wake word → relay envia PCM ao box → ASR batch → texto. **Não
  há ganho claro de streaming para enunciados curtos**; o VAD do ESP32 já
  define o fim de fala.
- **PT-BR vs PT-PT:** Whisper é multilíngue (detecta `pt`, sem código de
  região). Passar `language="pt"` + um prompt com ortografia/expressões
  brasileiras enviesa p/ PT-BR. Vosk tem `pt` genérico (treinado em PT-BR pelo
  FalaBrasil).

---

## 2. NLP / LLM self-hosted, PT-BR, CPU, 8 GB RAM

### Tabela comparativa

| Opção (Q4_K_M via Ollama/llama.cpp) | PT-BR | Tokens/s em CPU (decode) | TTFT (CPU) | RAM (modelo + KV, ctx 1024) | Tamanho (GGUF Q4) | Notas |
|---|---|---|---|---|---|---|
| **Qwen2.5-3B-Instruct** | ✅ **29+ idiomas, português oficial**; forte em JSON estruturado e role-play (persona) | **~15–25** (estimado i5; ver raciocínio) | **~0,7–1,0 s** (estimado) | **~2,0 GB** (modelo) + ~0,2 GB KV = **~2,2 GB** | ~2,0 GB | **Melhor PT-BR em 3B.** Card confirma "Multilingual support for over 29 languages, including … Portuguese". Suporte explícito a "structured outputs especially JSON" e role-play — perfeito p/ `{intent, action, response}` e persona "Felipe". 3,09B params, GQA (16Q/2KV), ctx 32K. License: qwen-research (comercial sob condições). |
| **Qwen2.5-1.5B-Instruct** | ✅ Mesma família multilíngue (português) | **~40–60** (estimado) | ~0,3–0,5 s | **~1,0 GB** + ~0,1 GB = **~1,1 GB** | ~1,0 GB | **Mais rápido e leve** que o 3B; PT-BR um degrau abaixo em sutileza, mas suficiente p/ frases curtas de robô. **Candidato a "ultra-fast"** se a latência do 3B apertar. |
| **Llama-3.2-3B-Instruct** | ✅ **Português oficial** (8 idiomas: EN/DE/FR/IT/**PT**/HI/ES/TH) | **~19,7** (confirmado ARM CPU int4) → **~15–25** i5 (estimado) | **0,7 s** (confirmado ARM int4) | ~2,4 GB (SpinQuant int4) → Q4_K_M ~2,2 GB | ~2,0–2,5 GB | **Benchmark confirmado** (card Meta, OnePlus 12/ExecuTorch/ARM): 3B SpinQuant int4 = **19,7 tok/s decode, TTFT 0,7 s, RSS 3726 MB**. MMLU-PT = **54,5** (3B) vs 62,1 (Llama 3.1 8B). License: Llama 3.2 Community. **Alternativa forte ao Qwen.** |
| **Llama-3.2-1B-Instruct** | ✅ Português oficial (mesmo card) | **~50,2** (confirmado ARM int4) → **~40–55** i5 | **0,3 s** (confirmado) | ~1,1–1,9 GB (SpinQuant: model 1083 MB, RSS 1921 MB) | ~1,1 GB | **Confirmado ARM int4: 50,2 tok/s, TTFT 0,3 s.** MMLU-PT = 39,8 (mais fraco, mas muito rápido). Útil p/ respostas curtas com folga de latência total. |
| **Phi-3-mini-4k-instruct** (3,8B) | ⚠️ **"Trained primarily on English — non-English worse"** (card oficial) | ~12–20 (estimado) | ~0,8–1,2 s | ~2,5 GB Q4 | ~2,5 GB | **License MIT** (atrativa), mas **card alerta: PT-BR degrada**. Multilingual score 56,7 vs Llama 3.2-8B 66,6. **Não recomendado para PT-BR.** |
| **Gemma-2-2B** | ⚠️ Multilíngue no tokenizer, mas treino majoritariamente EN | ~20–30 (estimado) | ~0,5–0,8 s | ~1,6 GB Q4 | ~1,6 GB | PT-BR possível porém qualidade inconsistente; menos fiável que Qwen/Llama-3.2. |
| **Classificador de intents por regra** (no relay, **zero LLM**) | ✅ Totalmente controlável | — (< 10 ms) | < 10 ms | ~0 (roda no app) | — | **Permanece válido e recomendado** (já no ticket 01). Resolve os ~18 comandos de movimento ("ande", "dança", "senta", "vira", "pula", "late", "brinca") em **< 10 ms e grátis**, despachando servos direto ao ESP32. **LLM só p/ chit-chat** ("Felipe, por que você é fofinho?"). |
| **vLLM / text-generation-inference** | — | — | — | — | — | **GPU-first.** vLLM tem suporte CPU experimental (lento, não recomendado p/ i5); TGI similar. **Para box CPU-only, use llama.cpp/Ollama (que envolve llama.cpp)** — não vLLM/TGI. |

### Notas (NLP/LLM)

- **O risco maior do self-hosting é a qualidade de PT-BR em LLMs pequenos.**
  Mitigação: escolher modelos com **suporte oficial a Português**. Dois
  candidatos sólidos em 3B:
  1. **Qwen2.5-3B-Instruct** — multilíngue 29+ langs, **português na lista
     oficial**, melhor em JSON/role-play (persona "cachorro-robô Felipe"),
     produz respostas curtas e coloquiais. **Recomendado como primário.**
  2. **Llama-3.2-3B-Instruct** — português entre os 8 idiomas suportados,
     MMLU-PT 54,5, benchmark de CPU confirmado. **Alternativa forte.**
  - Para economia máxima de RAM/latência: **Qwen2.5-1.5B** ou **Llama-3.2-1B**
    (50 tok/s, ~1 GB RAM). PT-BR aceitável para frases de robô.
- **Raciocínio da estimativa tokens/s em i5:** o card da Meta dá números
  **confirmados em ARM CPU** (OnePlus 12, Snapdragon 8 Gen 3, ExecuTorch int4):
  Llama 3.2 3B SpinQuant = **19,7 tok/s**, TTFT 0,7 s; 1B = **50,2 tok/s**,
  TTFT 0,3 s. Um i5 4-core x86 com AVX2 + `llama.cpp` Q4_K_M fica **na mesma
  ordem de grandeza** (x86 desktop é comparável ao prime-core do Snapdragon 8
  Gen 3, e usa 4 cores reais). Estimado **~15–25 tok/s (3B)** e **~40–55 tok/s
  (1B)** em i5. **Re-validar no hardware real** — essa é a extrapolação mais
  importante do documento.
- **Orçamento de latência do LLM:** a resposta do robô deve ser **curta** —
  system prompt fixa "frases de máx. 12 palavras" → ~20–25 tokens. Assim:
  - **3B:** ~0,7 s TTFT + ~1,2 s decode (25 tok @ 19,7) ≈ **~1,9 s** ✓ cabe no
    orçamento de 4 s (sobram ~2 s para ASR+TTS).
  - **1B/1.5B:** ~0,3 s TTFT + ~0,5 s decode ≈ **~0,8 s** ✓ folga enorme.
  - **Se o LLM gerasse 60 tokens** (resposta longa): 3B = ~3,7 s **sozinho** →
    **estoura o orçamento** com ASR+TTS. Daí a importância de **(a) regras
    para comandos e (b) respostas curtas** — exatamente como no ticket 01.
- **JSON estruturado:** Qwen2.5 explicitamente melhora "generating structured
  outputs especially JSON" — combina com o contrato `{intent, action,
  response}` do relay. Llama 3.2-3B também segue formato JSON com prompt
  adequado. Alternativa robusta: **GBNF grammar** do `llama.cpp`/Ollama força
  JSON sintaticamente válido (sem alucinação de formato) — útil em CPU para
  garantir parse confiável.
- **Segurança p/ criança de 8 anos:** self-hosted **não tem** moderation API
  grátis (OpenAI) nem filtros do Gemini/Claude. Mitigação: system prompt rígido
  (persona do filhote, só conteúdo apropriado, recusar o que extrapolar) +
  opcionalmente **Llama Guard 3-1B** (1B, roda em CPU) como pós-filtro. É
  uma desvantagem real vs cloud (onde Claude/Gemini são conservadores
  out-of-the-box) — o hobby/autor precisa aceitar calibrar o prompt.
- **vLLM / TGI em CPU:** ambos são **GPU-first**. vLLM tem um backend CPU
  experimental (lento, não otimizado para i5 desktop); TGI similar. Para
  **servidor único em CPU**, o certo é **llama.cpp** (`llama serve`, API
  compatível com OpenAI) ou **Ollama** (que envolve llama.cpp, expõe
  `:11434/api/chat` com `stream:true`). Ambos rodam bem em CPU-only com
  quantização Q4_K_M.

---

## 3. TTS (Text-to-Speech) self-hosted, PT-BR, CPU

### Tabela comparativa

| Opção | PT-BR | Latência em i5 CPU (1º áudio) | RAM (rodando) | Tamanho do modelo | Streaming | Notas |
|---|---|---|---|---|---|---|
| **Piper** (VITS via ONNX) — `pt_BR-cadu-medium` / `faber-medium` / `jeff-medium` / `edresson-low` | ✅ **4 vozes PT-BR** (cadu, edresson, faber, jeff) | **~0,1–0,3 s** (estimado; projetado p/ RPi4 → i5 é ≥ rápido) | **~100–400 MB** (modelo ONNX 60–100 MB + runtime) | low ~30 MB, medium ~60–100 MB | ✅ **`--output-raw`** (PCM cru no stdout) e `synthesize_stream_raw()` (16-bit PCM mono) | **Candidato líder.** Projetado p/ **edge/CPU** (RPi 4). Vozes PT-BR confirmadas em `VOICES.md`. Saída: **low = 16 kHz, medium = 22,05 kHz** (medium precisa de resample p/ 16 kHz do ESP32; **edresson-low já é 16 kHz → zero resample**, mas qualidade menor). ⚠️ **repo `rhasspy/piper` arquivado em 06/10/2025** → desenvolvimento ativo mudou para **`OHF-Voice/piper1-gpl`** (vozes `rhasspy/piper-voices` v1.0.0 continuam funcionando). |
| **MMS-TTS** (`facebook/mms-tts-por`, VITS) | ✅ Código **`por`** (Português) | ~0,3–1 s (estimado; fairseq/VITS em CPU) | ~0,3–0,6 GB | ~100 MB (gerador) | ⚠️ Gera áudio completo (não por chunk); on-the-fly com Coqui | **Cobertura massiva (1.107 idiomas)** mas treinado em dados limitados por idioma → **qualidade PT-BR abaixo do Piper** (treinado em datasets específicos brasileiros). ⚠️ **License CC-BY-NC 4.0 — não-comercial.** |
| **Coqui TTS / XTTSv2** | ✅ **YourTTS tem `language="pt-br"`**; XTTSv2 (16 idiomas) — verificar se PT incluído | **< 200 ms** (claim XTTSv2 streaming); porém CPU de XTTS é **lento p/ áudio completo** (segundos) | ~1–2 GB (XTTS é grande) | ~0,5–1 GB | ✅ XTTSv2 stream < 200 ms (claim) | Clonagem de voz (precisa de clip de referência). **Coqui (empresa) encerrou em 2024**; repo `coqui-ai/TTS` é **comunitário** (branch `dev` vivo). XTTS em CPU estoura o orçamento de 4 s para frases; YourTTS mais leve. License MPL-2.0. **Útil só se quiser voz clonada personalizada.** |
| **eSpeak-NG** | ✅ Voz `pt-br` | **< 100 ms** | **< 50 MB** | < 10 MB | ✅ (síntese por fonema) | **Formant synthesis — robótico.** Zero footprint, instantâneo, mas **soa mecânico**. Para um brinquedo de criança pode ser "charmoso" (robô falso), mas destoa de um "cachorro" natural. **Útil como fallback offline/emergência**, não como voz principal. |
| **VITS/VITS2 treino próprio** | ✅ (se treinar em dataset PT-BR) | Similar ao Piper | ~0,3 GB | ~100 MB | ⚠️ | **Piper já é VITS treinado p/ PT-BR** — treinar do zero é reinventar o Piper e exige GPU. **Fora de escopo p/ i5.** |
| **StyleTTS2 / OpenVoice** | ⚠️ (depende) | Lento em CPU (segundos) | ~1+ GB | — | ⚠️ | Otimizados p/ GPU; em CPU estouram a latência. **Não práticos no alvo.** |

### Notas (TTS)

- **Recomendado: Piper `pt_BR-cadu-medium`** como voz principal (voz brasileira
  natural, projetada para CPU/RPi, latência baixíssima, streaming de PCM cru).
  - **Alternativa econômica de formato:** `pt_BR-edresson-low` — **16 kHz**,
    mesma taxa do PCM do ESP32 (ADR-006), **zero resample** no relay. Qualidade
    menor ("low"), mas para frases curtas de robô é aceitável e economiza um
    passo de conversão.
  - **Vozes medium (22,05 kHz)**: o relay precisa resample 22050→16000 antes de
    enviar ao ESP32 (o I2S do ESP32 espera 16 kHz, ADR-006). Resample em
    Android/Kotlin é trivial (`MediaCodec`/linear interpolation) e barato.
- **Streaming de PCM:** Piper suporta `--output-raw` (CLI) e
  `synthesize_stream_raw()` (Python), emitindo **PCM 16-bit mono** — quase o
  formato do link ESP32↔relay (só difere na sample rate se for medium). O relay
  pode começar a reenviar PCM ao ESP32 **antes** de toda a síntese terminar →
  **latência de 1º áudio ~100–300 ms** (estimado; Piper em RPi4 gera em ~RTF
  0,1–0,3, i5 é mais rápido). **Verificar empiricamente.**
- **Repo arquivado:** `rhasspy/piper` foi arquivado em **06/10/2025** ("now
  read-only"), com desenvolvimento migrado para **`OHF-Voice/piper1-gpl`**.
  Os modelos de voz `rhasspy/piper-voices` (v1.0.0) **continuam disponíveis e
  funcionando**. Para projeto novo, planejar migração do runtime para
  `piper1-gpl` (ou `sherpa-onnx`, que roda vozes Piper via ONNX e é ativamente
  mantido). **Não é bloqueador — é um sinal de manutenção a monitorar.**
- **Persona de filhote:** as 4 vozes PT-BR do Piper (cadu, edresson, faber,
  jeff) são vozes adultas masculinas/femininas naturais, **não "voz de
  filhote/criança"**. Para um cachorro-robô, voz aguda-amigável exigiria
  treino/clonagem (Coqui YourTTS/XTTS) — fora do alcance do Piper "low/medium"
  pronto. **Trade-off real do self-hosted:** qualidade natural boa, mas
  **sem a expressividade/persona "filhote" do ElevenLabs/Azure casual-style**
  (ver seção 8).
- **MMS-TTS `por`:** PT-BR suportado mas qualidade inferior ao Piper (dados de
  treino limitados por idioma no MMS); **CC-BY-NC 4.0** trava uso comercial —
  hobby OK, mas anotar se o projeto for a produto.
- **eSpeak-NG:** honestamente, para uma criança de 8 anos o robô com voz
  mecânica pode ser divertido ("é um robô de verdade!"), mas perde o caráter de
  "cachorro". Manter como **fallback offline de emergência** (se a box cair,
  o relay pode gerar eSpeak-NG no próprio smartphone — sem dependência da box).

---

## 4. Arquitetura: como a box i5/8GB se encaixa na topologia

### Opção A — Box substitui a nuvem (recomendada, preserva ADR-002)

O relay (smartphone) **continua** terminando TLS e orquestrando
ASR→NLP→TTS, mas em vez de chamar a nuvem (HTTPS/WAN), chama a **box na LAN
via HTTP (sem TLS)** — exatamente como o ESP32 já fala com o relay (ADR-002).
A box vira um "relay fixo mais capaz" (a alternativa Raspberry Pi do ADR-002,
mas com i5/8GB é ordens de grandeza mais capaz que um RPi).

```
ESP32 (WS server, PCM 16k mono)
   │  PCM up/down (binary frames, HTTP/WS — sem TLS, LAN)
   ▼
[Smartphone relay]  ── HTTP (LAN, sem TLS) ──►  [Box i5/8GB]
   • TLS p/ descoberta/auth (só p/ setup)          ├─ ASR:  faster-whisper server / whisper-server :8080
   • orquestra ASR→NLP→TTS                         ├─ NLP:  Ollama :11434  (Qwen2.5-3B Q4_K_M)
   • resample 22050→16000 (Piper medium)          └─ TTS:  Piper HTTP wrapper  (pt_BR-cadu-medium)
   • re-envia PCM ao ESP32                             (todos os 3 rodam na box, na LAN)
```

- **Vantagem:** **não muda ADR-002** (TLS continua no smartphone; o link
  ESP32↔relay e relay↔box são HTTP na LAN). **Portabilidade mantida** — o
  robô funciona fora de casa **se** a box estiver acessível (VPN/Tailscale
  para a LAN de casa) ou com fallback para a stack cloud (ticket 01) quando
  fora. **Híbrido é o melhor dos dois mundos**: nuvem quando fora, box quando
  em casa.
- **Implementação:** expor 3 endpoints HTTP na box:
  - ASR: `POST /v1/audio/transcriptions` (API compatível OpenAI) via
    `whisper-server` (whisper.cpp) ou `speaches` (faster-whisper).
  - NLP: `POST /v1/chat/completions` (compatível OpenAI) via Ollama
    (`llama serve`/Ollama já falam essa API).
  - TTS: pequeno wrapper HTTP em torno do Piper (`POST /tts` → stream PCM).
  - O relay apenas troca a base URL (`https://api.openai.com` →
    `http://192.168.x.x:11434`) — **mesma lógica de orquestração** do ticket
    01. **Abstração limpa: o relay não sabe se fala com nuvem ou box.**

### Opção B — Box É o relay (sem smartphone) — **não recomendada**

A box assume TLS + orquestração + UI. **Muda ADR-002** (perde portabilidade,
perde fallback 4G/5G, perde "robô funciona fora de casa", exige UI nova na
box). Só faz sentido se o robô for **estacionário em casa** e o autor quiser
dispensar o smartphone — mas aí o ticket 01 (cloud) já é mais simples. **A
Opção A domina.**

### Serviços concorrentes ou em série? Orçamento de RAM

A pipeline por requisição é **serial** (ASR termina → NLP → TTS), mas para
**latência mínima** convém manter os três **residentes/quentes** (senão o
load do modelo a cada request soma 1–3 s). Orçamento de RAM com tudo
residente (alvo: ~6 GB livres):

| Serviço | RAM residente (estimado) |
|---|---|
| ASR — faster-whisper `small` int8 | ~1,5 GB |
| NLP — Qwen2.5-3B Q4_K_M (ctx 1024) | ~2,2 GB |
| TTS — Piper `pt_BR-cadu-medium` (ONNX) | ~0,2–0,4 GB |
| **Total dos 3 serviços** | **~3,9–4,1 GB** |
| SO + overhead | ~2 GB |
| **Total no box** | **~5,9–6,1 GB** → **cabe em 8 GB, mas apertado** |

- **Cabe concorrentemente** com `small`+`3B`+`Piper`. Se apertar, trocar ASR
  por `base` int8 (~0,5 GB) → total ~3,1 GB serviços (~5,1 GB no box, folga
  confortável). Ou LLM por `Qwen2.5-1.5B` (~1,1 GB) → ~3,0 GB serviços.
- **Alternativa serial (poupa RAM, paga latência):** unload do ASR e TTS entre
  requests (Ollama já mantém o LLM quente sozinho); recarregar Piper é
  ~100–300 ms, faster-whisper ~500 ms. Para hobby com folga de RAM, **prefira
  residente**.

### Latência end-to-end estimada na LAN (sem hop de WAN)

| Etapa | Self-hosted (estimado i5) | Cloud (ticket 01) |
|---|---|---|
| Rede (LAN vs WAN, 3 hops) | **~15 ms** (LAN) | ~300–900 ms (WAN RTT) |
| ASR | ~0,8–1,2 s (small int8) | ~0,3 s (Deepgram streaming) |
| NLP (25 tokens de resposta) | ~1,9 s (3B Q4) / ~0,8 s (1,5B Q4) | ~0,5 s (gpt-4o-mini) |
| TTS (1º áudio) | ~0,1–0,3 s (Piper) | ~0,3–0,5 s (Azure) |
| **Total (3B)** | **~2,8–3,6 s** ✓ | **~1,5–2,5 s** (cloud doc) |
| **Total (1,5B)** | **~1,8–2,5 s** ✓✓ | — |

- **Self-hosted é comparável à cloud** e pode ser **mais rápido com LLM 1,5B**
  (Piper em CPU é mais rápido que Azure no 1º chunk; sem WAN). Com **3B fica
  no limite do orçamento de 4 s**, mas ainda dentro — desde que as respostas
  sejam curtas (~25 tokens) e regras cuidem dos comandos de movimento.
- **Estimados a verificar:** ASR em i5 (escalado de i7-12700K), tokens/s de
  Qwen2.5-3B em i5 (escalado de Llama-3.2-3B ARM), latência do Piper em i5.
  Os números são **directionalmente sólidos** (conservadores), mas só a
  medição no hardware real fecha a conta (ver seção 9).

---

## 5. Stack recomendada self-hosted

### 🥇 Stack primária (melhor equilíbrio PT-BR × latência × RAM)

| Etapa | Provedor / modelo | Por quê |
|---|---|---|
| **ASR** | **faster-whisper `small` int8** (ou `whisper-server` base Q5_0 se RAM apertar), `language="pt"`, `vad_filter=True`, `condition_on_previous_text=False` | Mesma qualidade PT-BR do Whisper cloud; int8 = ~1,5 GB RAM, RTF ~0,13 em i7 (i5 ~1 s p/ 3 s); VAD embutido. |
| **NLP** | **Regras (1ª etapa) + Qwen2.5-3B-Instruct Q4_K_M** via Ollama (`:11434`, `stream:true`, ctx 1024, `max_tokens` 25–30, GBNF p/ JSON) | Regras resolvem comandos de movimento em < 10 ms; Qwen2.5-3B cobre chit-chat PT-BR natural + JSON estruturado + role-play de persona, em ~2,2 GB RAM. |
| **TTS** | **Piper `pt_BR-cadu-medium`** (voz principal); `pt_BR-edresson-low` (16 kHz, zero resample) como econômica | Projetado p/ CPU/RPi; 4 vozes PT-BR prontas; streaming de PCM cru; ~0,2–0,4 GB RAM. |

**Custo:** só **eletricidade do box** (~$3–5/mês se sempre ligado — ver seção 8;
~$0 marginal se a box já serve outra coisa). **API: $0.**
**Latência estimada:** **~2,8–3,6 s** (dentro do alvo < 4 s, com folga se usar
Qwen2.5-1.5B → ~1,8–2,5 s).
**RAM:** **~4–4,1 GB** dos 3 serviços residentes (+ 2 GB SO = ~6,1 GB em 8 GB;
trocar ASR por `base` ou LLM por `1.5B` dá folga).

### 🥈 Stack "ultra-fast / baixa RAM" (se latência/8GB apertar)

- **ASR:** whisper.cpp `base` Q5_0 (~0,5 GB, ~0,6 s)
- **NLP:** **Qwen2.5-1.5B Q4** (~1,1 GB, ~50 tok/s, TTFT ~0,3 s) — PT-BR aceitável
  para frases curtas de robô.
- **TTS:** Piper `pt_BR-edresson-low` (16 kHz, ~0,2 GB, zero resample).
- **Total:** ~1,8 GB serviços (~3,8 GB no box, **folga enorme em 8 GB**);
  **latência estimada ~1,8–2,5 s**. Ideal como **ponto de partida do MVP** —
  sobe rápido, valida a pipeline, depois escala LLM p/ 3B se PT-BR de chit-chat
  precisar de mais qualidade.

### 🥉 Stack "tudo-em-um via Sherpa-ONNX" (integração mínima)

- **Runtime único:** Sherpa-ONNX faz ASR (Whisper int8) + TTS (vozes Piper) +
  VAD (Silero) no mesmo processo ONNX, em x86_64, com bindings C++/Python/Java.
- **Vantagem:** **uma dependência só** na box, menos peças para orquestrar;
  mesmos modelos (Whisper + Piper) da stack primária.
- **Trade-off:** menos maduro para endpoint HTTP pronto (precisa de um wrapper
  server); LLM ainda precisa do Ollama separado. Considerar se a simplicidade
  de **um runtime** pesar mais que a conveniência dos servidores prontos.

### Stack "engajamento máximo" (voz de filhote/criança) — **fora do alcance i5**

Coqui XTTSv2 / YourTTS permitem **clonar uma voz de filhote**, mas em CPU o
tempo de síntese estoura 4 s. **Não é viável em i5.** Para voz de filhote,
permanece a rota cloud do ticket 01 (ElevenLabs com Startup Grant ou Azure
casual-style). O self-hosted troca expressividade por privacidade/offline.

---

## 6. Custo: eletricidade do box vs API do ticket 01

Premissas: box i5 sempre ligado, **~40 W médio** (idle ~30–50 W; o uso do robô
é intermitente, ~4 min de LLM ativo/dia). Tarifa **~$0,15/kWh**.

| Item | Custo/mês |
|---|---|
| Box i5/40 W sempre ligado | 40 W × 24 h × 30 d = **28,8 kWh** → **~$4,32/mês** |
| Box i5/30 W (idle baixo) | ~$3,24/mês |
| Box i5/50 W (idle alto) | ~$5,40/mês |
| **Stack cloud (ticket 01)** | **~$0,10–0,60/mês** (Azure TTS free; Deepgram $0 do crédito; LLM ~$0,09) |

- **Conclusão contra-intuitiva:** para este **baixo volume hobby**, o self-host
  **custa ~10–50× mais em eletricidade** que a API cloud (a hipótese do issue,
  "~$0,03/mês idle", está **~100× otimista** — um desktop i5 não chega a
  centavos). **A API cloud é mais barata** em $ absoluto.
- **Mas:** se a box **já existe e já está ligada** (NAS, home server, outro
  serviço), o custo **marginal** de adicionar voz é **~$0** — a eletricidade
  já é paga pelo outro uso. Nesse cenário self-host é "grátis".
- E se a box **dormir/acordar** só quando o robô for usado (wake-on-LAN,
  ~horas/dia), o custo cai proporcionalmente (~$0,5–1/mês) — mas cold-start do
  LLM (~10–30 s p/ carregar modelo) estraga a experiência. Para voz
  sempre-pronta, **box precisa ficar quente**.

---

## 7. Trade-offs: self-hosted vs cloud (ticket 01)

| Dimensão | Self-hosted (esta pesquisa) | Cloud (ticket 01) |
|---|---|---|
| **Custo ($/mês)** | ~$3–5 eletricidade (ou ~$0 se box já on) | ~$0,10–0,60 (API) |
| **Latência** | ~2–3,5 s (LAN; sem WAN) — comparável ou melhor c/ 1,5B | ~1,5–2,5 s (WAN, mas GPUs rápidas) |
| **Qualidade ASR PT-BR** | **= cloud** (mesmo Whisper) — igual | Excelente (Deepgram Nova-3, Azure Custom Speech p/ voz de criança) |
| **Qualidade LLM PT-BR** | **Boa em 3B** (Qwen2.5/Llama-3.2, oficial PT); **fraca em 1B** | Excelente (gpt-4o-mini, Claude Haiku, Gemini) |
| **Qualidade TTS PT-BR** | Boa e natural (Piper cadu/faber), **mas sem voz "filhote"/estilo casual** | Melhor catálogo + estilo *casual* (Azure), voz custom de filhote (ElevenLabs) |
| **Privacidade** | **Total — áudio nunca sai de casa** ✅✅ | Áudio vai à nuvem após wake word (VAD/KWS local limita) |
| **Offline (sem internet)** | **Funciona 100%** ✅ (só LAN) | Não funciona |
| **Manutenção** | Box precisa updates, monitoramento, não-brickar; **Piper repo arquivado** (migrar p/ piper1-gpl/sherpa-onnx) | Zero (provider cuida) |
| **Segurança p/ criança** | Sem filtros out-of-the-box; system prompt + Llama Guard 3-1B opcional | Claude/Gemini conservadores; OpenAI moderation grátis |
| **Hobby/fator diversão** | **Alto** ✅ (setup é entretenimento; box já existe) | Baixo (só cadastrar cartão) |
| **Portabilidade** | Robô funciona **em casa** (Opção A) ou com VPN; fora de casa cai p/ cloud | Funciona em qualquer lugar c/ internet |
| **Risco de provider** | **Zero** (sem preço/descontinuação de nuvem) | Preço muda, free tiers expiram (Deepgram $200, ElevenLabs grant) |

**Síntese:** self-hosted **não é mais barato** (a menos que a box já exista),
mas **ganha em privacidade, offline e controle**, e **empata em latência e
qualidade de ASR**. **Perde em qualidade de TTS (sem voz de filhote) e em
segurança out-of-the-box**. A decisão é de **produto/valores**, não de custo.

---

## 8. Pontos em aberto / a verificar empiricamente

1. **Tokens/s em i5 real** (maior incerteza): medir Qwen2.5-3B Q4_K_M e
   Llama-3.2-3B Q4_K_M via Ollama no i5 do autor — confirmar ~15–25 tok/s
   (3B) e ~40–55 tok/s (1,5B/1B). A extrapolação veio de ARM (OnePlus 12).
2. **Latência de ASR em i5 real**: medir faster-whisper `small`/`base` int8
   em enunciado de 3 s — confirmar ~0,8–1,2 s / ~0,6 s. A estimativa escalou
   o benchmark i7-12700K por ~2–3×.
3. **Piper em i5 real**: medir RTF e 1º-chunk de `pt_BR-cadu-medium` e
   `edresson-low` — confirmar ~0,1–0,3 s e qualidade subjetiva (ouvir as
   vozes em [rhasspy.github.io/piper-samples/]).
4. **PT-BR do Qwen2.5-3B/Llama-3.2-3B**: rodar ~20 prompts chit-chat de
   robô ("por que você late?", "dá um passinho e dança") e avaliar se a
   saída soa **português brasileiro natural** (não EN traduzido, não PT-PT).
   Este é o **risco de produto central** — só medindo se convence.
5. **RAM real residente**: subir faster-whisper + Ollama(3B) + Piper
   concorrentes e medir `free -h` — confirmar ~4–4,1 GB (cabe em 6 GB
   livres). Se estourar, trocar ASR por `base` ou LLM por 1.5B.
6. **Cold-start do LLM**: se a box usar Ollama com `keep_alive` (modelo
   descarrega após N min idle), o 1º request paga load (~10–30 s p/ 3B).
   Configurar `OLLAMA_KEEP_ALIVE=-1` (sempre quente) p/ voz sempre-pronta —
   mas consome RAM 100% do tempo.
7. **Manutenção do Piper**: confirmar que `piper1-gpl` (ou Sherpa-ONNX
  rodando vozes Piper) é o runtime de futuro — `rhasspy/piper` está
  arquivado. Decidir runtime antes de amarrar.
8. **MMS-TTS license**: confirmar CC-BY-NC 4.0 se o projeto for a produto —
  hobby OK, comercial não.
9. **Wireshark no link LAN**: confirmar que o relay→box sem TLS na LAN é
  aceitável (áudio do sobrinho em rede de casa). Consistente com ADR-002
  (ESP32↔relay já é sem TLS na LAN).

---

## Apêndice — Fontes (verificadas em 2026-08-15)

- **whisper.cpp** — `github.com/ggerganov/whisper.cpp` (tabela de RAM:
  tiny ~273 MB / base ~388 MB / small ~852 MB / medium ~2,1 GB; Q5_0;
  `whisper-stream` real-time; `whisper-server` API compatível OpenAI; OpenBLAS).
- **openai/whisper** — `github.com/openai/whisper` (tamanhos: tiny 39 M,
  base 74 M, small 244 M, medium 769 M, large 1550 M, turbo 809 M;
  multilíngue → PT-BR; turbo não traduz mas transcreve PT).
- **faster-whisper** — `github.com/SYSTRAN/faster-whisper` + Context7
  (`/systran/faster-whisper`): benchmark **small int8 em i7-12700K/8 threads
  = 1m42s p/ 13 min, 1477 MB**; `BatchedInferencePipeline`; `vad_filter` (Silero);
  wrappers de streaming (`Whisper-Streaming`, `WhisperLive`).
- **Vosk** — `github.com/alphacep/vosk-api` + `alphacephei.com/vosk/models`:
  **`vosk-model-small-pt-0.3` = 31 MB, WER 68,92/32,60**;
  `vosk-model-pt-fb-v0.1.1` = 1,6 GB, WER 54,34/27,70. Modelos small ~300 MB
  RAM, roda em RPi.
- **distil-whisper** — `huggingface.co/distil-whisper`: **"currently only
  available for English speech recognition"** (eliminado p/ PT-BR).
- **Piper** — `github.com/rhasspy/piper` (**arquivado 06/10/2025 →
  OHF-Voice/piper1-gpl**) + `VOICES.md`: **vozes PT-BR = cadu (medium),
  edresson (low), faber (medium), jeff (medium)**. Context7 (`/rhasspy/piper`):
  VITS/ONNX, otimizado p/ RPi4; `--output-raw` + `synthesize_stream_raw()`
  (16-bit PCM mono); **low = 16 kHz, medium = 22,05 kHz**.
- **Qwen2.5-3B-Instruct** — `huggingface.co/Qwen/Qwen2.5-3B-Instruct`:
  3,09B params, **"Multilingual support for over 29 languages, including …
  Portuguese"**; "structured outputs especially JSON"; role-play; ctx 32K;
  263 quantizações p/ llama.cpp/Ollama.
- **Llama-3.2-3B-Instruct** — `huggingface.co/meta-llama/Llama-3.2-3B-Instruct`:
  3,21B params; **idiomas oficiais: EN/DE/FR/IT/PT/HI/ES/TH**;
  **MMLU-PT 54,5 (3B) / 39,8 (1B)**; **benchmark CPU confirmado (ARM,
  OnePlus 12, ExecuTorch int4): 3B SpinQuant = 19,7 tok/s, TTFT 0,7 s,
  RSS 3726 MB; 1B SpinQuant = 50,2 tok/s, TTFT 0,3 s, RSS 1921 MB.**
- **Phi-3-mini-4k-instruct** — `huggingface.co/microsoft/Phi-3-mini-4k-instruct`:
  3,8B, **license MIT**, mas **"the Phi models are trained primarily on
  English text. Languages other than English will experience worse
  performance"** (evitar p/ PT-BR).
- **MMS-TTS** — `huggingface.co/facebook/mms-tts`: 1.107 idiomas, código
  **`por`** = Português; **CC-BY-NC 4.0**; arquitetura VITS (fairseq).
- **Sherpa-ONNX** — `github.com/k2-fsa/sherpa-onnx`: next-gen Kaldi em ONNX;
  ASR (streaming Zipformer + Whisper não-streaming) + TTS (Piper) + VAD
  (Silero) + KWS; x86_64/ARM/RPi/WASM; 12 linguagens de binding.
- **Coqui TTS** — `github.com/coqui-ai/TTS`: **MPL-2.0**; XTTSv2 16 idiomas
  < 200 ms stream; **YourTTS `language="pt-br"`**; 1.100 modelos Fairseq/MMS;
  Coqui (empresa) encerrou 2024 — repo comunitário no branch `dev`.
- **Ollama** — `github.com/ollama/ollama` + Context7 (`/ollama/ollama`):
  envolve llama.cpp; REST `:11434/api/chat` (`stream:true`); `ollama ps`
  (modelo em VRAM/RAM); `num_ctx`/`num_thread`/`use_mmap`; Flash Attention +
  KV cache q8_0 (½ RAM); métricas `eval_count`/`eval_duration` → tokens/s.
- **llama.cpp** — `github.com/ggerganov/llama.cpp`: AVX/AVX2/AVX512/AMX;
  quantização 1,5–8 bit; `llama serve` (API compatível OpenAI);
  `llama cli -hf <org>/<model>-GGUF`; gramáticas GBNF p/ JSON garantido.
- **ADR-002 / ADR-006** — `docs/decisions/002-…` (relay smartphone, TLS na
  LAN) e `006-…` (PCM 16 kHz/16-bit/mono, ASR/TTS remotos).
- **cloud-voice-providers.md** (ticket 01) — `docs/research/cloud-voice-providers.md`
  (Deepgram + gpt-4o-mini + Azure TTS, ~$0,10–0,60/mês, ~1,5–2,5 s).

### Não fetchado / estimado

- **Tokens/s de LLM em i5 x86**: extrapolado de Llama-3.2-3B **em ARM CPU**
  (OnePlus 12). Re-validar no i5 do autor.
- **Latência de ASR em i5**: escalado do benchmark i7-12700K (~2–3× mais lento).
- **RTF/1º-chunk do Piper em i5**: Piper projetado p/ RPi4, então i5 é ≥ rápido;
  valor ~0,1–0,3 s é estimado conservador.
- **Potência do box i5 (~40 W)**: estimativa de desktop sem dGPU; medir com
  wattímetro p/ confirmar o $/mês.
- **Whisper PT WER (~10–12% large-v3 em Common Voice)**: figura conhecida da
  comunidade/paper, não re-fetchada; irrelevante para a decisão (self-host =
  mesmo modelo do cloud).
