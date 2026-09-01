import { describe, expect, it } from 'vitest';
import {
  applyDeltas,
  applyStat,
  decay,
  HEALTH_WEIGHTS,
  healthOf,
  initialStats,
  moodOf,
  STAGE_DECAY_MULTIPLIER,
  STAT_NAMES,
  type Stats,
  sicknessOf,
} from './stats.js';

describe('stats', () => {
  it('tem 18 stats editáveis + health derivado', () => {
    expect(STAT_NAMES.length).toBe(18);
    const stats = initialStats();
    expect('health' in stats).toBe(false);
  });

  it('inclui happiness como stat emotional', () => {
    expect(STAT_NAMES).toContain('happiness');
  });

  it('health é a soma ponderada das stats vitais (todas em 80 → 80)', () => {
    expect(healthOf(initialStats())).toBe(80);
  });

  it('health cai quando fullness cai (peso 0.25)', () => {
    const stats = applyStat(initialStats(), 'fullness', -80);
    expect(healthOf(stats)).toBeLessThan(80);
  });

  it('health não muda quando uma stat sem peso muda', () => {
    const stats = applyStat(initialStats(), 'happiness', -50);
    expect(healthOf(stats)).toBe(80);
  });

  it('pesos de health somam 1.0', () => {
    const total = Object.values(HEALTH_WEIGHTS).reduce((a, b) => a + b, 0);
    expect(total).toBeCloseTo(1, 10);
  });

  it('decay reduz stats proporcional ao tempo e é puro', () => {
    const stats = initialStats();
    const antes = stats.fullness;
    const decaidas = decay(stats, 0, 3_600_000);
    // fullness decay = 15/24 por hora ≈ 0.625
    expect(decaidas.fullness).toBeCloseTo(antes - 15 / 24, 5);
    expect(stats.fullness).toBe(antes);
  });

  it('decay diferente por tier (daily vs weekly)', () => {
    const stats = initialStats();
    const umaHora = decay(stats, 0, 3_600_000);
    expect(umaHora.fullness).toBeLessThan(umaHora.cleanliness);
  });

  it('decay respeita multiplicador de estágio (Filhote 1.3×, Adulto 0.8×)', () => {
    const stats = initialStats();
    const umaHoraFilhote = decay(stats, 0, 3_600_000, STAGE_DECAY_MULTIPLIER.Filhote);
    const umaHoraJovem = decay(stats, 0, 3_600_000, STAGE_DECAY_MULTIPLIER.Jovem);
    const umaHoraAdulto = decay(stats, 0, 3_600_000, STAGE_DECAY_MULTIPLIER.Adulto);
    expect(umaHoraFilhote.fullness).toBeLessThan(umaHoraJovem.fullness);
    expect(umaHoraAdulto.fullness).toBeGreaterThan(umaHoraJovem.fullness);
  });

  it('applyStat respeita [0, 100]', () => {
    const stats = initialStats();
    expect(applyStat(stats, 'fullness', 1000).fullness).toBe(100);
    expect(applyStat(stats, 'fullness', -1000).fullness).toBe(0);
  });

  it('applyStat aplica asymptotic damping (delta positivo perto do topo resiste)', () => {
    const statsHigh = { ...initialStats(), fullness: 95 };
    const statsMid = { ...initialStats(), fullness: 50 };
    // mesmo delta de +20 — perto de 100 resiste mais que perto de 50
    const highResult = applyStat(statsHigh, 'fullness', 20).fullness;
    const midResult = applyStat(statsMid, 'fullness', 20).fullness;
    const highGain = highResult - 95;
    const midGain = midResult - 50;
    expect(highGain).toBeLessThan(midGain);
  });

  it('applyStat damping: delta negativo perto de 0 resiste', () => {
    const statsLow = { ...initialStats(), fullness: 5 };
    const statsMid = { ...initialStats(), fullness: 50 };
    const lowResult = applyStat(statsLow, 'fullness', -20).fullness;
    const midResult = applyStat(statsMid, 'fullness', -20).fullness;
    const lowLoss = 5 - lowResult;
    const midLoss = 50 - midResult;
    expect(lowLoss).toBeLessThan(midLoss);
  });

  it('applyDeltas aplica múltiplos incrementos', () => {
    const stats = initialStats();
    const result = applyDeltas(stats, { fullness: 10, energy: -20 });
    expect(result.fullness).toBeGreaterThan(80);
    expect(result.energy).toBeLessThan(80);
    expect(stats.fullness).toBe(80);
  });

  describe('moodOf', () => {
    it('retorna hungry quando fullness < 25', () => {
      const stats: Stats = { ...initialStats(), fullness: 20 };
      expect(moodOf(stats)).toBe('hungry');
    });

    it('retorna tired quando energy < 20', () => {
      const stats: Stats = { ...initialStats(), fullness: 50, energy: 15 };
      expect(moodOf(stats)).toBe('tired');
    });

    it('retorna playful quando stats altas (playfulness > 60 e energy > 40)', () => {
      const stats: Stats = { ...initialStats(), playfulness: 90, energy: 70 };
      expect(moodOf(stats)).toBe('playful');
    });

    it('retorna happy quando nenhuma condição especial Matches', () => {
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

  describe('sicknessOf', () => {
    it('retorna 0 quando health >= 30', () => {
      expect(sicknessOf(80)).toBe(0);
      expect(sicknessOf(30)).toBe(0);
    });

    it('retorna > 0 quando health < 30', () => {
      expect(sicknessOf(20)).toBeGreaterThan(0);
      expect(sicknessOf(0)).toBeCloseTo(10, 0);
    });
  });
});
