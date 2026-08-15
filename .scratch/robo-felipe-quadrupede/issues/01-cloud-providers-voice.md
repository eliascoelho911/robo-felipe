# 01 — Provedores de nuvem para ASR, NLP e TTS (PT-BR)

## Type
research

## Status
closed

## Assignee
research-subagent

## Blocked by
none

## Resolution

Pesquisa completa em `docs/research/cloud-voice-providers.md`.

**Stack recomendada** (custo × latência × PT-BR):
- **ASR:** Deepgram Nova-3 — streaming sub-300ms, aceita `linear16` 16kHz
  direto do relay (zero transcode), $200 de crédito free.
- **NLP:** regras (comandos de movimento, <10ms, grátis) + **gpt-4o-mini**
  para chit-chat (~$0,09/mês). Gemini Flash como alternativa.
- **TTS:** Azure Neural TTS — maior catálogo pt-BR com estilos amigáveis,
  500k chars/mês free que cobre o uso.

**Custo @ 50 cmds/dia: ~$0,10–0,60/mês** (essencialmente grátis).

Stack "100% grátis": Azure STT + Gemini Flash + Azure TTS = $0/mês.

## Question

Quais provedores de nuvem usar para ASR (speech-to-text), NLP/LLM
(intenção + resposta), e TTS (text-to-speech), dados os requisitos:

- **Língua:** português brasileiro (PT-BR).
- **Latência alvo:** < 4s end-to-end ("Hey Felipe, [comando]" → resposta
  falada). ADR-006 estima 2.5–4s típicos.
- **Usuário:** criança de 8 anos — voz aguda, vocabulário simples,
  sotaque possivelmente irregular.
- **Custo:** projeto de hobby — orçamento baixo, uso intermitente.
- **Streaming vs batch:** ASR streaming é preferível (menor latência),
  mas batch é aceitável para comandos curtos (1–3s de áudio).
- **TTS:** voz natural, preferencialmente em PT-BR (não PT-PT).
- **NLP:** precisa interpretar intenções flexíveis ("dá um passinho e
  depois dança") e responder em PT-BR natural. Pode ser LLM ou
  classificador de intents.

Candidatos (ver ADR-006):
- **ASR:** Google Speech-to-Text (streaming), Azure Speech, OpenAI
  Whisper API (batch), Deepgram (streaming, baixa latência).
- **NLP/LLM:** OpenAI (GPT-4o-mini?), Claude (Haiku?), Gemini, ou
  regra simples por intents.
- **TTS:** Google TTS, Azure Neural TTS, ElevenLabs, OpenAI TTS.

Investigar e comparar: qualidade em PT-BR, latência, custo por
requisição, complexidade de integração no app Android (relay), e
suporte a streaming. O relay abstrai o provedor (ADR-006) — o ESP32
não sabe quem faz ASR/TTS.
