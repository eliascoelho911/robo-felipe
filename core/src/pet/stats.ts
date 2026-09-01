// As 19 stats do pet Tamagotchi (ADR-023): 18 editáveis + `health` derivado.
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
  'happiness',
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

// Pesos da fórmula de health (ADR-023 §2). Somam 1.0. `happiness` não entra
// (contribui indiretamente via playfulness); stats sem peso têm peso 0.
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
  happiness: 0,
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
  happiness: 15 / HOURS_PER_DAY,
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

// Multiplicador de decay por estágio (ADR-023 §3): Filhote decai 1.3× mais
// rápido, Adulto 0.8× mais devagar, Jovem sem multiplicador.
export const STAGE_DECAY_MULTIPLIER: Record<PetStage, number> = {
  Filhote: 1.3,
  Jovem: 1.0,
  Adulto: 0.8,
};

export type PetStage = 'Filhote' | 'Jovem' | 'Adulto';

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
  estagio: PetStage;
  // epoch ms da criação do pet — para calcular ageDays na resposta
  createdAt: number;
}

// Decay puro: dadas as stats, o timestamp da última atualização, o timestamp
// atual e o multiplicador de estágio, devolve as stats decaídas em [0, 100].
export function decay(
  stats: Stats,
  lastUpdatedMs: number,
  nowMs: number,
  stageMultiplier = 1,
): Stats {
  const elapsedHours = (nowMs - lastUpdatedMs) / 3_600_000;
  if (elapsedHours <= 0) return { ...stats };
  const result = { ...stats };
  for (const name of STAT_NAMES) {
    const rate = DECAY_RATES[name];
    result[name] = clamp(result[name] - rate * elapsedHours * stageMultiplier, 0, 100);
  }
  return result;
}

// Asymptotic damping (ADR-023 §2): mudanças perto dos extremos resistem.
// Delta positivo: efetivo = delta * ((100 - current) / 100) ^ 0.7.
// Delta negativo: efetivo = delta * (current / 100) ^ 0.7.
function dampedDelta(current: number, delta: number): number {
  if (delta > 0) {
    return delta * ((100 - current) / 100) ** 0.7;
  }
  if (delta < 0) {
    return delta * (current / 100) ** 0.7;
  }
  return 0;
}

// Aplica um incremento a uma stat com asymptotic damping, clamp em [0, 100].
export function applyStat(stats: Stats, name: StatName, delta: number): Stats {
  const current = stats[name] ?? 0;
  const effective = dampedDelta(current, delta);
  return { ...stats, [name]: clamp(current + effective, 0, 100) };
}

// Aplica múltiplos incrementos de uma vez (para tools que mutam várias stats).
export function applyDeltas(stats: Stats, deltas: Partial<Record<StatName, number>>): Stats {
  let result = stats;
  for (const [name, delta] of Object.entries(deltas)) {
    result = applyStat(result, name as StatName, delta);
  }
  return result;
}

// Sickness derivado de health baixo (ADR-023 §2). 0 se health >= 30;
// escala até 10 quando health = 0. Não persistido — calculado na resposta.
export function sicknessOf(health: number): number {
  if (health >= 30) return 0;
  return Math.round(((30 - health) / 30) * 10 * 10) / 10;
}

// Mood derivado das stats (ADR-023 §6). Prioridade top-down; MVP deriva 8
// moods de stats persistidas (hungry, tired, dirty, playful, curious,
// excited, mischievous, happy). sad/sleepy/bored/dizzy/scared precisam de
// sickness ou flags — não no MVP do Core interno.
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
