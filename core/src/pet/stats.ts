// As 18 stats do pet Tamagotchi (ADR-023): 17 editáveis + `health` derivado.
// `health` nunca é editado direto — é calculado pela fórmula ponderada abaixo.
// O decay é função pura de timestamp (injetar relógio em testes; nunca
// chamar Date.now() direto na lógica de domínio).

import type { Emotion } from '@robo-felipe/contract';

export const STAT_NAMES = [
  // físicos (daily/weekly)
  'fullness',
  'energy',
  'cleanliness',
  'fitness',
  'comfort',
  // emocionais (daily/weekly/very-slow)
  'playfulness',
  'affection',
  'serenity',
  'fulfillment',
  // sociais (weekly/monthly)
  'sociability',
  'loyalty',
  // mentais / personalidade (monthly/very-slow)
  'curiosity',
  'intelligence',
  'maturity',
  'courage',
  'mischievousness',
  'focus',
] as const;

export type StatName = (typeof STAT_NAMES)[number];
export type Stats = Record<StatName, number>;

// Pesos da fórmula de health (ADR-023 §2). Somam 1.0.
export const HEALTH_WEIGHTS: Readonly<Record<StatName, number>> = {
  fullness: 0.25,
  fitness: 0.2,
  energy: 0.2,
  cleanliness: 0.15,
  comfort: 0.05,
  affection: 0.05,
  fulfillment: 0.025,
  focus: 0.025,
  intelligence: 0.025,
  playfulness: 0.025,
  // stats sem peso em health
  serenity: 0,
  sociability: 0,
  loyalty: 0,
  curiosity: 0,
  maturity: 0,
  courage: 0,
  mischievousness: 0,
};

// Decay por hora — tiers mistos (ADR-023 §5). Rates indicativos (~-15/dia,
// ~-10/semana, ~-5/mês, ~-2/mês very-slow).
const HOURS_PER_DAY = 24;
const HOURS_PER_WEEK = 7 * HOURS_PER_DAY;
const HOURS_PER_MONTH = 30 * HOURS_PER_DAY;

export const DECAY_RATES: Readonly<Record<StatName, number>> = {
  // daily (~-15/dia)
  fullness: 15 / HOURS_PER_DAY,
  energy: 15 / HOURS_PER_DAY,
  playfulness: 15 / HOURS_PER_DAY,
  // weekly (~-10/semana)
  cleanliness: 10 / HOURS_PER_WEEK,
  fitness: 10 / HOURS_PER_WEEK,
  comfort: 10 / HOURS_PER_WEEK,
  affection: 10 / HOURS_PER_WEEK,
  sociability: 10 / HOURS_PER_WEEK,
  // monthly (~-5/mês)
  maturity: 5 / HOURS_PER_MONTH,
  curiosity: 5 / HOURS_PER_MONTH,
  intelligence: 5 / HOURS_PER_MONTH,
  courage: 5 / HOURS_PER_MONTH,
  loyalty: 5 / HOURS_PER_MONTH,
  // very-slow (~-2/mês)
  serenity: 2 / HOURS_PER_MONTH,
  fulfillment: 2 / HOURS_PER_MONTH,
  focus: 2 / HOURS_PER_MONTH,
  mischievousness: 2 / HOURS_PER_MONTH,
};

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value));
}

export function initialStats(): Stats {
  const stats = {} as Stats;
  for (const name of STAT_NAMES) {
    stats[name] = 80;
  }
  return stats;
}

// health é derivado — soma ponderada das stats com peso (ADR-023 §2). Nunca
// editado direto por uma tool.
export function healthOf(stats: Stats): number {
  let sum = 0;
  for (const name of STAT_NAMES) {
    sum += stats[name] * HEALTH_WEIGHTS[name];
  }
  return Math.round(clamp(sum, 0, 100));
}

export interface PetState {
  petId: string;
  stats: Stats;
  // epoch ms da última atualização das stats
  lastUpdatedMs: number;
  estagio: 'Filhote' | 'Jovem' | 'Adulto';
}

// Decay puro: dadas as stats, o timestamp da última atualização e o
// timestamp atual, devolve as stats decaídas clamps em [0, 100].
export function decay(stats: Stats, lastUpdatedMs: number, nowMs: number): Stats {
  const elapsedHours = (nowMs - lastUpdatedMs) / 3_600_000;
  if (elapsedHours <= 0) return { ...stats };
  const result = { ...stats };
  for (const name of STAT_NAMES) {
    const rate = DECAY_RATES[name];
    result[name] = clamp(result[name] - rate * elapsedHours, 0, 100);
  }
  return result;
}

// Aplica um incremento a uma stat, clamp em [0, 100].
export function applyStat(stats: Stats, name: StatName, delta: number): Stats {
  return { ...stats, [name]: clamp((stats[name] ?? 0) + delta, 0, 100) };
}

// Aplica múltiplos incrementos de uma vez (para tools que mutam várias stats).
export function applyDeltas(stats: Stats, deltas: Partial<Record<StatName, number>>): Stats {
  let result = stats;
  for (const [name, delta] of Object.entries(deltas)) {
    result = applyStat(result, name as StatName, delta);
  }
  return result;
}

// Mood derivado das stats (ADR-023 §6). Prioridade top-down; sem sickness
// nem flags temporárias no MVP — só stats persistidas.
export function moodOf(stats: Stats): Emotion {
  if (stats.fullness < 25) return 'hungry';
  if (stats.energy < 20) return 'tired';
  if (stats.cleanliness < 20) return 'dirty';
  if (stats.playfulness > 60 && stats.energy > 40) return 'playful';
  if (stats.curiosity > 60) return 'curious';
  if (stats.affection > 60) return 'excited';
  if (stats.mischievousness > 60) return 'mischievous';
  return 'happy';
}
