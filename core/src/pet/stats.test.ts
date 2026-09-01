import { describe, expect, it } from 'vitest';
import {
  applyDeltas,
  applyStat,
  decay,
  HEALTH_WEIGHTS,
  healthOf,
  initialStats,
  moodOf,
  STAT_NAMES,
  type Stats,
} from './stats.js';

describe('stats', () => {
  it('tem 17 stats editáveis + health derivado', () => {
    expect(STAT_NAMES.length).toBe(17);
    const stats = initialStats();
    // health não está entre as stats editáveis
    expect('health' in stats).toBe(false);
  });

  it('health é a soma ponderada das stats vitais (todas em 80 → 80)', () => {
    const stats = initialStats();
    expect(healthOf(stats)).toBe(80);
  });

  it('health cai quando fullness cai (peso 0.25)', () => {
    const stats = applyStat(initialStats(), 'fullness', -80);
    expect(healthOf(stats)).toBeLessThan(80);
  });

  it('health não muda quando uma stat sem peso muda', () => {
    const stats = applyStat(initialStats(), 'serenity', -50);
    expect(healthOf(stats)).toBe(80);
  });

  it('pesos de health somam 1.0', () => {
    const total = Object.values(HEALTH_WEIGHTS).reduce((a, b) => a + b, 0);
    expect(total).toBeCloseTo(1, 10);
  });

  it('decay reduz stats proporcional ao tempo e é puro', () => {
    const stats = initialStats();
    const antes = stats.fullness;
    const decaidas = decay(stats, 0, 3_600_000); // 1 hora
    // fullness decay = 15/24 por hora ≈ 0.625
    expect(decaidas.fullness).toBeCloseTo(antes - 15 / 24, 5);
    // a entrada original não mutou (pure)
    expect(stats.fullness).toBe(antes);
  });

  it('decay diferente por tier (daily vs weekly)', () => {
    const stats = initialStats();
    const umaHora = decay(stats, 0, 3_600_000);
    // fullness (daily) decai mais rápido que cleanliness (weekly)
    expect(umaHora.fullness).toBeLessThan(umaHora.cleanliness);
  });

  it('applyStat respeita [0, 100]', () => {
    const stats = initialStats();
    expect(applyStat(stats, 'fullness', 1000).fullness).toBe(100);
    expect(applyStat(stats, 'fullness', -1000).fullness).toBe(0);
  });

  it('applyDeltas aplica múltiplos incrementos', () => {
    const stats = initialStats();
    const result = applyDeltas(stats, { fullness: 10, energy: -20 });
    expect(result.fullness).toBe(90);
    expect(result.energy).toBe(60);
    // a entrada original não mutou
    expect(stats.fullness).toBe(80);
  });

  describe('moodOf', () => {
    it('retorna hungry quando fullness < 25', () => {
      const stats = applyDeltas(initialStats(), { fullness: -60 });
      expect(moodOf(stats)).toBe('hungry');
    });

    it('retorna tired quando energy < 20', () => {
      const stats = applyDeltas(initialStats(), { fullness: 10, energy: -65 });
      expect(moodOf(stats)).toBe('tired');
    });

    it('retorna playful quando playfulness > 60 e energy > 40', () => {
      const stats = { ...initialStats(), playfulness: 90, energy: 70 };
      expect(moodOf(stats)).toBe('playful');
    });

    it('retorna playful quando stats altas (playfulness > 60 e energy > 40)', () => {
      // initialStats() deixa tudo em 80 → playful
      expect(moodOf(initialStats())).toBe('playful');
    });

    it('retorna happy quando nenhuma condição especial Matches', () => {
      // stats medianas sem triggers de mood
      const stats: Stats = {
        ...initialStats(),
        fullness: 50,
        energy: 50,
        cleanliness: 50,
        playfulness: 40,
        curiosity: 40,
        affection: 40,
        mischievousness: 40,
      };
      expect(moodOf(stats)).toBe('happy');
    });
  });
});
