# 06 — Vocabulário de voz: 18 ações → frases PT-BR

## Type
grilling

## Status
open

## Assignee
unclaimed

## Blocked by
none

## Question

Como mapear as 18 ações do `servo_dog_ctrl` (ver ADR-013) para frases
naturais em PT-BR que o sobrinho (8 anos) usaria para comandar o cão?

As 18 ações (DOGE_STATE_*):
1. INSTALLATION (calibração — não é comando de voz)
2. IDLE (parar)
3. FORWARD (andar frente)
4. BACKWARD (andar trás)
5. TURN_RIGHT (girar direita)
6. TURN_LEFT (girar esquerda)
7. LAY_DOWN (deitar)
8. BOW (curvar/reverenciar)
9. LEAN_BACK (recostar)
10. BOW_LEAN (curvar e recostar)
11. SWAY_BACK_FORTH (balançar frente-trás)
12. SWAY (balançar esquerda-direita)
13. SHAKE_HAND (dar a pata)
14. POKE (cutucar)
15. SHAKE_BACK_LEGS (chacoalhar patas traseiras)
16. JUMP_FORWARD (pular frente)
17. JUMP_BACKWARD (pular trás)
18. RETRACT_LEGS (recolher pernas)

Questões de design:
- **Vocabulário natural vs comandos fixos:** "Felipe, dá a pata!" vs
  "Felipe, shake hand". ADR-006 escolheu linguagem natural (ASR de
  vocabulário livre + NLP/LLM para interpretar intenção).
- **Sinônimos:** "anda" vs "vai em frente" vs "anda pra frente" —
  como o NLP mapeia múltiplas frases para a mesma ação?
- **Ações compostas:** "dá um passinho e depois dança" → sequência de
  DOG_STATE_FORWARD + DOG_STATE_SWAY. O NLP/LLM precisa decompor.
- **Respostas de TTS:** o que o cão "diz" ao executar cada ação?
  ("Ok, andando!" / "Aqui está a patinha!" / "Deitando...")
- **Personalidade:** o cão tem um nome (Felipe), uma personalidade
  (brincalhão? preguiçoso? corajoso?) que molda o tom das respostas?

Recomendação preliminar: definir 12-15 frases-canônicas (uma por
ação não-técnica), 2-3 sinônimos cada, e um prompt de sistema para o
LLM que mapeia texto livre → enum DOG_STATE_* + resposta de TTS.
