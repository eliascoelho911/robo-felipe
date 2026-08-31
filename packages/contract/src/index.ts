// Contrato canônico do Robô Felipe: Batch (Plataforma → Core) e Plano de
// Ações (Core → Plataforma). Este pacote é a fonte da verdade compartilhada
// entre o Core (TS) e a Plataforma Android (via JSON Schema gerado).
// Ver ADR-018 (Core + contrato) e CONTEXT.md (linguagem ubiquitástica).

import { z } from 'zod';

// --- Trigger -------------------------------------------------------------
// Evento detectado pela Plataforma, com timestamp e payload. Iniciais:
// `voz`, `sacudida`, `toque_de_botao` (CONTEXT.md, "Trigger").

export const TriggerKind = z.enum(['voz', 'sacudida', 'toque_de_botao']);
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
// Efeito que a Plataforma sabe executar (CONTEXT.md, "Ação"): `falar`,
// `dancar`, `expressar_emocao`, `ficar_tonto`. Discriminated union para
// validação tipo-segura por kind.

export const FalarAction = z.object({
  kind: z.literal('falar'),
  texto: z.string().min(1),
});
export type FalarAction = z.infer<typeof FalarAction>;

export const DancarAction = z.object({
  kind: z.literal('dancar'),
  // duração da dança em milissegundos
  duracaoMs: z.number().int().positive(),
});
export type DancarAction = z.infer<typeof DancarAction>;

export const Emocao = z.enum(['feliz', 'triste', 'sonolento', 'entediado', 'animado']);
export type Emocao = z.infer<typeof Emocao>;

export const ExpressarEmocaoAction = z.object({
  kind: z.literal('expressar_emocao'),
  emocao: Emocao,
});
export type ExpressarEmocaoAction = z.infer<typeof ExpressarEmocaoAction>;

export const FicarTontoAction = z.object({
  kind: z.literal('ficar_tonto'),
  // 0..1
  intensidade: z.number().min(0).max(1),
});
export type FicarTontoAction = z.infer<typeof FicarTontoAction>;

export const Action = z.discriminatedUnion('kind', [
  FalarAction,
  DancarAction,
  ExpressarEmocaoAction,
  FicarTontoAction,
]);
export type Action = z.infer<typeof Action>;

// --- Plano de Ações ------------------------------------------------------
// Resposta do Core — lista ordenada de uma ou mais Ações (CONTEXT.md,
// "Plano de Ações"). Referencia o batchId que originou o plano.

export const PlanoDeAcoes = z.object({
  version: z.literal(1),
  batchId: z.string().uuid(),
  acoes: z.array(Action).min(1),
});
export type PlanoDeAcoes = z.infer<typeof PlanoDeAcoes>;
