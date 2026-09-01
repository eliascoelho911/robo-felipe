// Contrato canônico do Robô Felipe: Batch (Plataforma → Core) e Plano de
// Ações (Core → Plataforma). Este pacote é a fonte da verdade compartilhada
// entre o Core (TS) e a Plataforma Android (via JSON Schema gerado).
// Ver ADR-018 (Core + contrato) e CONTEXT.md (linguagem ubiquitástica).

import { z } from 'zod';

// --- Trigger -------------------------------------------------------------
// Evento detectado pela Plataforma, com timestamp e payload. Iniciais:
// `voice`, `shake`, `button_press` (CONTEXT.md, "Trigger").

// Fase 1: `voice`, `shake`, `button`, `manual`. Fase 2 adiciona `proximity`,
// `rtc_wake` (ADR-023 §7, tabela de triggers não-vozeados).
export const TriggerKind = z.enum(['voice', 'shake', 'button', 'manual']);
export type TriggerKind = z.infer<typeof TriggerKind>;

export const Trigger = z.object({
  id: z.string().uuid(),
  kind: TriggerKind,
  // epoch em milissegundos
  timestamp: z.number().int().nonnegative(),
  // payload kind-specific; a Plataforma preenche conforme o tipo de Trigger
  payload: z.record(z.string(), z.unknown()),
});
export type Trigger = z.infer<typeof Trigger>;

// --- Batch ---------------------------------------------------------------
// Envelope versionado com um ou mais Triggers enviado ao Core (CONTEXT.md,
// "Batch"). A versão do contrato bumpa aqui; breaking changes exigem ADR.

export const Batch = z.object({
  version: z.literal(1),
  batchId: z.string().uuid(),
  // identifica a instância de Plataforma (Android hoje, CoreS3 amanhã)
  platformId: z.string(),
  // identifica o pet (o estado canônico vive no Core, ADR-023)
  petId: z.string(),
  triggers: z.array(Trigger).min(1),
});
export type Batch = z.infer<typeof Batch>;

// --- Ação ----------------------------------------------------------------
// Efeito que a Plataforma sabe executar (CONTEXT.md, "Ação"): `speak`,
// `dance`, `express_emotion`, `get_dizzy`, `sleep`. Discriminated union
// para validação tipo-segura por kind.

export const SpeakAction = z.object({
  kind: z.literal('speak'),
  text: z.string().min(1),
});
export type SpeakAction = z.infer<typeof SpeakAction>;

export const DanceAction = z.object({
  kind: z.literal('dance'),
  // duração da dança em milissegundos
  durationMs: z.number().int().positive(),
});
export type DanceAction = z.infer<typeof DanceAction>;

// 13 moods do ADR-023 §6, derivados das stats (sem sickness nem flags
// temporárias no MVP — só moods deriváveis de stats persistidas).
export const Emotion = z.enum([
  'happy',
  'sad',
  'sleepy',
  'bored',
  'excited',
  'hungry',
  'tired',
  'dirty',
  'dizzy',
  'scared',
  'playful',
  'curious',
  'mischievous',
]);
export type Emotion = z.infer<typeof Emotion>;

export const ExpressEmotionAction = z.object({
  kind: z.literal('express_emotion'),
  emotion: Emotion,
});
export type ExpressEmotionAction = z.infer<typeof ExpressEmotionAction>;

export const GetDizzyAction = z.object({
  kind: z.literal('get_dizzy'),
  // 0..1
  intensity: z.number().min(0).max(1),
});
export type GetDizzyAction = z.infer<typeof GetDizzyAction>;

export const SleepAction = z.object({
  kind: z.literal('sleep'),
  durationMs: z.number().int().positive(),
});
export type SleepAction = z.infer<typeof SleepAction>;

export const Action = z.discriminatedUnion('kind', [
  SpeakAction,
  DanceAction,
  ExpressEmotionAction,
  GetDizzyAction,
  SleepAction,
]);
export type Action = z.infer<typeof Action>;

// --- Pet state snapshot --------------------------------------------------
// Estado do pet embutido no Plano de Ações para evitar round-trip extra
// (ADR-023). Opcional — triggers simples podem não incluir.

export const Stage = z.enum(['Filhote', 'Jovem', 'Adulto']);
export type Stage = z.infer<typeof Stage>;

export const PetStateSnapshot = z.object({
  stage: Stage,
  mood: z.string(),
  health: z.number(),
  sickness: z.number(),
  ageDays: z.number(),
  stats: z.record(z.string(), z.number()),
  lastInteraction: z.number(),
});
export type PetStateSnapshot = z.infer<typeof PetStateSnapshot>;

// --- Plano de Ações ------------------------------------------------------
// Resposta do Core — lista ordenada de uma ou mais Ações (CONTEXT.md,
// "Plano de Ações"). Referencia o batchId que originou o plano. Pode
// incluir um snapshot do estado do pet para a UI da Plataforma.

// actions pode ser vazio — Batch com só Trigger `manual` gera Plano com
// actions: [] mas inclui state (snapshot only, Spec 02).
export const PlanoDeAcoes = z.object({
  version: z.literal(1),
  batchId: z.string().uuid(),
  actions: z.array(Action),
  state: PetStateSnapshot.optional(),
});
export type PlanoDeAcoes = z.infer<typeof PlanoDeAcoes>;
