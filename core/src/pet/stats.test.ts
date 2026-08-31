import { describe, expect, it } from 'vitest';
import { applyStat, decay, healthOf, initialStats, STAT_NAMES } from './stats.js';

describe('stats', () => {
  it('tem 17 stats editáveis + health derivado', () => {
    expect(STAT_NAMES.length).toBe(17);
    const stats = initialStats();
    // health não está entre as stats editáveis
    expect('health' in stats).toBe(false);
  });

  it('health é a média das 5 stats vitais', () => {
    const stats = initialStats();
    // todas começam em 80 → health = 80
    expect(healthOf(stats)).toBe(80);
  });

  it('health cai quando uma stat vital cai', () => {
    const stats = applyStat(initialStats(), 'satiety', -80);
    expect(healthOf(stats)).toBeLessThan(80);
  });

  it('decay reduz stats proporcional ao tempo e é puro', () => {
    const stats = initialStats();
    const antes = stats.satiety;
    const decaidas = decay(stats, 0, 3_600_000); // 1 hora
    // satiety decay = 2.5/hora
    expect(decaidas.satiety).toBeCloseTo(antes - 2.5, 5);
    // a entrada original não mutou (pure)
    expect(stats.satiety).toBe(antes);
  });

  it('experience nunca decai', () => {
    const stats = initialStats();
    const decaidas = decay(stats, 0, 10 * 3_600_000); // 10 horas
    expect(decaidas.experience).toBe(0);
  });

  it('applyStat respeita [0, 100]', () => {
    const stats = initialStats();
    expect(applyStat(stats, 'satiety', 1000).satiety).toBe(100);
    expect(applyStat(stats, 'satiety', -1000).satiety).toBe(0);
  });
});
