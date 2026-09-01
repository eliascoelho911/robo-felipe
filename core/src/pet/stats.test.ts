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

  it('decay com elapsed=0 não muda stats', () => {
    const stats = initialStats();
    const decaidas = decay(stats, 1_000_000, 1_000_000);
    expect(decaidas).toEqual(stats);
  });

  it('decay com 1 dia (86400s) aplica decay significativo em daily', () => {
    const stats = initialStats();
    const decaidas = decay(stats, 0, 86_400_000);
    // fullness daily: 15/dia → 80 - 15 = 65
    expect(decaidas.fullness).toBeCloseTo(65, 0);
  });

  it('decay com 30 dias leva stats perto de 0', () => {
    const stats = initialStats();
    const decaidas = decay(stats, 0, 30 * 86_400_000);
    // 30 dias de decay: daily perde 15*30=450, clamped em 0
    expect(decaidas.fullness).toBe(0);
    expect(decaidas.energy).toBe(0);
    // monthly perde 5 em 30 dias
    expect(decaidas.maturity).toBeCloseTo(75, 0);
  });

  it('cada tier decai na rate correta em 1 hora', () => {
    const stats = initialStats();
    const decaidas = decay(stats, 0, 3_600_000);
    // daily: 15/24 por hora
    expect(decaidas.fullness).toBeCloseTo(80 - 15 / 24, 2);
    // weekly: 10/(7*24) por hora
    expect(decaidas.cleanliness).toBeCloseTo(80 - 10 / (7 * 24), 2);
    // monthly: 5/(30*24) por hora
    expect(decaidas.maturity).toBeCloseTo(80 - 5 / (30 * 24), 2);
    // very-slow: 2/(30*24) por hora
    expect(decaidas.serenity).toBeCloseTo(80 - 2 / (30 * 24), 2);
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
