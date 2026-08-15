# 09 — Provedores de voz self-hosted (ASR, NLP, TTS) em hardware i5/8GB

## Type
research

## Status
closed

## Assignee
research-subagent

## Blocked by
none

## Resolution

Pesquisa completa em `docs/research/selfhosted-voice-providers.md`.

**Self-hosted É viável** em i5/8GB CPU-only com PT-BR e latência < 4s —
mas com ressalvas. **Não reverte o ticket 01; adiciona uma opção.**

**Stack recomendada (🥇):**
- **ASR:** faster-whisper `small` int8 (~1,5 GB, ~1 s/3s) — **mesma
  qualidade PT-BR do Whisper cloud**; `vad_filter`, `language="pt"`.
- **NLP:** regras (movimento, <10ms) + **Qwen2.5-3B Q4_K_M via Ollama**
  (~2,2 GB, ~19 tok/s, TTFT ~0,7s) — português oficial (29+ langs),
  forte em JSON/role-play. Llama-3.2-3B como alternativa forte.
- **TTS:** **Piper `pt_BR-cadu-medium`** (~0,2–0,4 GB, streaming PCM,
  ~0,1–0,3s 1º chunk). 4 vozes PT-BR (cadu/edresson/faber/jeff);
  `edresson-low` = 16kHz (zero resample no relay).
- **Arquitetura:** **Opção A** — box na LAN via HTTP (sem TLS),
  relay smartphone inalterado (preserva ADR-002); box substitui só a
  nuvem. 3 serviços residentes ≈ ~4–4,1 GB (cabe em 6 GB livres).
- **Latência estimada:** ~2,8–3,6s (3B); ~1,8–2,5s com 1,5B (MVP ultra-fast).

**Stack 🥈 ultra-fast/low-RAM (ponto de partida do MVP):** whisper.cpp
base Q5_0 + Qwen2.5-1.5B + Piper edresson-low = ~1,8 GB serviços,
latência ~1,8–2,5s.

**Conclusão contra-intuitiva de custo:** self-hosted **NÃO é mais barato**
que cloud para este volume — ~$3–5/mês de eletricidade (box i5 ~40W idle)
vs ~$0,10–0,60/mês de API. **A menos que a box já exista e já fique
ligada** (NAS/home server) — aí custo marginal ≈ $0. **A decisão é de
produto/valores (privacidade, offline, controle), não de custo.**

**Riscos principais:**
1. **PT-BR em LLMs pequenos (1–4B)** — maior incerteza; mitigado por
   Qwen2.5/Llama-3.2 (português oficial); **validar empiricamente**.
2. **Benchmarks de CPU extrapolados** de i7-12700K (ASR) e ARM OnePlus 12
   (LLM) → estimados para i5, marcados claramente.
3. **Piper repo arquivado** (06/10/2025) → migrar para `piper1-gpl`
   ou `sherpa-onnx` (vozes Piper continuam funcionando).
4. **Sem filtros de segurança out-of-the-box** vs Claude/Gemini/moderation
   API do cloud — precisa system prompt rígido + opc. Llama Guard 3-1B.
5. **Sem voz "filhote/criança"** no Piper (vozes adultas naturais) —
   trade-off de expressividade vs ElevenLabs/Azure casual-style.

**vs cloud (ticket 01):** latência comparável; ASR PT-BR igual (mesmo
Whisper); LLM/TTS PT-BR melhores no cloud; ganha privacidade total,
offline e zero risco de provider; perde em persona de voz e safety.
**Híbrido é o melhor dos dois mundos:** box em casa, cloud quando fora
(Opção A mantém ambos — relay só troca a base URL).

**Pontos em aberto a verificar empiricamente** (ver seção 8 do doc):
tokens/s em i5 real, latência ASR em i5 real, Piper em i5 real, PT-BR
do Qwen2.5-3B, RAM residente concorrente, cold-start do Ollama, runtime
Piper de futuro, license MMS-TTS.

## Question

Quais stacks de voz self-hosted (ASR + NLP/LLM + TTS) rodam em hardware
modesto — **Intel i5, 8 GB RAM, CPU only (sem GPU)** — com qualidade e
latência comparáveis à stack cloud do ticket 01 (Deepgram + gpt-4o-mini +
Azure TTS, ~$0,10–0,60/mês, latência < 4s end-to-end), suportando **PT-BR**?

A stack cloud do ticket 01 está decidida, mas há motivação para investigar
self-hosting:
- **Privacidade total** — áudio do sobrinho nunca sai de casa.
- **Custo recorrente zero** — só eletricidade (~$0,03/mês idle?).
- **Offline/robustez** — funciona sem internet (apenas LAN).
- **Controle** — sem risco de provider mudar preço/descontinuar.
- **Hobby** — o autor já tem o hardware.

Premissas de hardware:
- **CPU:** Intel i5 (gen não especificada — assumir i5 de ~4ª–8ª gen,
  2–4 cores, ~2–3 GHz). Sem GPU CUDA; talvez iGPU integrada (útil só
  para QuickSync, não para ML).
- **RAM:** 8 GB total, ~6 GB livres após SO. Modelos precisam caber com
  folga para ASR/NLP/TTS concorrentes ou em pipeline serial.
- **Disco:** assumir SSD com espaço para modelos (1–10 GB típico).
- **Rede:** LAN (robô + relay + servidor na mesma rede — zero latência
  de WAN). O servidor pode ser o "relay fixo" (Raspberry Pi do ADR-002
  alternative, mas com i5/8GB é muito mais capaz que um RPi).

Investigar e comparar, por etapa:

### 1. ASR (Speech-to-Text) self-hosted, PT-BR, CPU
- **faster-whisper** (CTranslate2, Whisper quantizado) — whisper-small/medium
  em CPU, latência, RAM, PT-BR quality.
- **whisper.cpp** (C++ port, quantização INT4/INT8) — smallest/model distil.
- **Vosk** (Kaldi-based, offline, pequeno) — modelo PT-BR (~50MB), qualidade
  vs Whisper, latência.
- **Sherpa-ONNX** (next-gen Kaldi, ONNX) — modelos PT-BR, streaming?
- **Coqui STT** (Mozilla fork) — ainda mantido? PT-BR model?
- Latência alvo: streaming ou < 500ms batch para enunciado de 3s.
- RAM footprint de cada um (rodando, não só modelo).

### 2. NLP/LLM self-hosted, PT-BR, CPU, 8GB RAM
- **llama.cpp** (CPU, quantizado GGUF) com modelos pequenos:
  - Qwen2.5-1.5B / 3B (Q4_K_M ~1–2GB RAM)
  - Phi-3-mini (3.8B, ~2.5GB Q4)
  - Llama-3.2-1B / 3B (~1–2GB Q4)
  - Gemma-2-2B (~1.6GB Q4)
- **Ollama** como runtime (wraps llama.cpp, fácil deploy).
- **vLLM / text-generation-inference** — rodam em CPU? Ou exigem GPU?
- Qualidade PT-BR dos modelos pequenos (1–4B) — eles falam português
  brasileiro natural? Ou só inglês/instruções PT?
- Latência TTFT (time-to-first-token) em CPU i5 — quantos tokens/s?
  Precisa de ~60 tokens de resposta em < 500ms para o alvo.
- Alternativa: classificador de intents por regras (zero LLM, <10ms,
  já recomendado no ticket 01) — permanece válido e recomendado.
- Alternativa: modelo server-side no estilo `sentence-transformers`
  para intent classification (ML leve, não LLM generativo).

### 3. TTS (Text-to-Speech) self-hosted, PT-BR, CPU
- **Piper** (VITS, ONNX, ultra-rápido em CPU) — vozes PT-BR disponíveis?
  Qualidade? Latência?
- **Coqui TTS / XTTS** — PT-BR, clonagem de voz, latência em CPU?
- **MMS-TTS** (Meta Massively Multilingual Speech) — PT-BR suportado?
- **eSpeak / eSpeak-NG** — robótico, mas zero footprint. Aceitável?
- **VITS / VITS2** treino próprio PT-BR — factível?
- **StyleTTS2 / OpenVoice** — CPU-viável?
- Latência: precisa gerar áudio em < 500ms (streaming chunk) para não
  estourar o alvo de 4s end-to-end.
- RAM footprint.

### 4. Arquitetura de orquestração
- Como o box i5/8GB se encaixa na topologia do Robô Felipe?
  Opção A: o box substitui a nuvem — relay (smartphone) faz TLS só para
  a descoberta; ASR/NLP/TTS rodam no box na LAN (HTTP, sem TLS, como
  já faz o ESP32). O box vira um "relay fixo mais capaz" (Raspberry Pi
  do ADR-002 alternative, mas com i5/8GB).
  Opção B: o box É o relay (sem smartphone) — mas isso muda a arquitetura
  (ADR-002 escolheu smartphone para portabilidade).
- Os três serviços (ASR + NLP + TTS) cabem concorrentemente em 6GB
  livres, ou precisam rodar serial na pipeline (um por vez)?
- Latência total end-to-end na LAN (sem WAN hop) vs cloud.

### 5. Trade-offs vs cloud (ticket 01)
- **Custo:** eletricidade do box 24/7 (~$?) vs $0,10–0,60/mês de API.
  O box serve para outras coisas além do robô (custo já amortizado)?
- **Latência:** LAN-only deve ser mais rápido (sem WAN round-trip).
- **Qualidade PT-BR:** Whisper self-hosted = mesma qualidade do Whisper
  cloud. TTS Piper vs Azure Neural — qual soa melhor em PT-BR?
- **Manutenção:** box precisa de updates, monitoramento, não bricka.
- **Hobby:** box já existe; setup é diversão, não overhead.

### 6. Stack recomendada self-hosted (se viável)
Com custo, latência, RAM, qualidade PT-BR, e integração com o relay.

## Contexto

Ticket 01 (cloud) decidiu: Deepgram Nova-3 + regras/gpt-4o-mini + Azure
Neural TTS. Esta pesquisa investiga a alternativa self-hosted para
possível adoção se a motivação (privacidade/custo/offline/hobby)
justificar. Não reverte o ticket 01 — adiciona uma opção de produto.

A arquitetura base (ADR-002: relay smartphone, ADR-006: PCM 16kHz/16-bit/
mono) permanece — a diferença é para *onde* o relay orquestra os serviços
de voz (nuvem vs box LAN).
