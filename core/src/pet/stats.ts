// As 18 stats do pet Tamagotchi (ADR-023): 17 editáveis + `health` derivado.
// `health` nunca é editado direto — é calculado a partir das stats vitais.
// O decay é função pura de timestamp (injetar relógio em testes; nunca
// chamar Date.now() direto na lógica de domínio).

export const STAT_NAMES = [
  'satiety',
  'happiness',
  'energy',
  'cleanliness',
  'socialization',
  'fun',
  'discipline',
  'affection',
  'courage',
  'creativity',
  'curiosity',
  'patience',
  'mentalWellbeing',
  'hydration',
  'mood',
  'attention',
  'experience',
] as const;

export type StatName = (typeof STAT_NAMES)[number];
export type Stats = Record<StatName, number>;

// Stats cuja média forma `health` (bem-estar físico/mental do pet).
export const VITAL_STATS = [
  'satiety',
  'energy',
  'cleanliness',
  'hydration',
  'mentalWellbeing',
] as const satisfies readonly StatName[];

// Decay por hora — tiers mistos (ADR-023). experience nunca decai.
export const DECAY_RATES: Readonly<Record<StatName, number>> = {
  satiety: 2.5,
  happiness: 1.5,
  energy: 2.0,
  cleanliness: 1.0,
  socialization: 0.8,
  fun: 1.8,
  discipline: 0.3,
  affection: 0.6,
  courage: 0.4,
  creativity: 0.5,
  curiosity: 0.5,
  patience: 0.7,
  mentalWellbeing: 0.9,
  hydration: 2.2,
  mood: 1.5,
  attention: 1.2,
  experience: 0,
};

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value));
}

export function initialStats(): Stats {
  const stats = {} as Stats;
  for (const name of STAT_NAMES) {
    stats[name] = name === 'experience' ? 0 : 80;
  }
  return stats;
}

// health é derivado — média das stats vitais. Nunca editado direto.
export function healthOf(stats: Stats): number {
  const vitals = VITAL_STATS.map((n) => stats[n]);
  const sum = vitals.reduce((acc, v) => acc + v, 0);
  return Math.round(sum / vitals.length);
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
    if (name === 'experience') continue;
    const rate = DECAY_RATES[name];
    result[name] = clamp(result[name] - rate * elapsedHours, 0, 100);
  }
  return result;
}

// Aplica um incremento a uma stat, clamp em [0, 100].
export function applyStat(stats: Stats, name: StatName, delta: number): Stats {
  return { ...stats, [name]: clamp((stats[name] ?? 0) + delta, 0, 100) };
}
