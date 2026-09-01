import { describe, expect, it } from 'vitest';
import { type Action, Batch, PlanoDeAcoes } from './index.js';

describe('Batch', () => {
  const batchValido = {
    version: 1,
    batchId: 'f47ac10b-58cc-4372-a567-0e02b2c3d479',
    platformId: 'android-sobrinho',
    petId: 'felipe-tamagotchi',
    triggers: [
      {
        id: '6ec0bd7f-11c0-43dc-a75b-2a90c20d8b1c',
        kind: 'voice',
        timestamp: 1725000000000,
        payload: { audioRef: 'blob://...' },
      },
    ],
  };

  it('aceita um Batch válido', () => {
    expect(Batch.parse(batchValido)).toEqual(batchValido);
  });

  it('rejeita Batch sem triggers', () => {
    expect(() => Batch.parse({ ...batchValido, triggers: [] })).toThrow();
  });

  it('rejeita Batch com versão diferente de 1', () => {
    expect(() => Batch.parse({ ...batchValido, version: 2 })).toThrow();
  });
});

describe('Plano de Ações', () => {
  const plano: PlanoDeAcoes = {
    version: 1,
    batchId: 'f47ac10b-58cc-4372-a567-0e02b2c3d479',
    actions: [
      { kind: 'speak', text: 'Oi, Sobrinho!' },
      { kind: 'express_emotion', emotion: 'happy' },
    ],
  };

  it('aceita um Plano válido com Ações múltiplas', () => {
    expect(PlanoDeAcoes.parse(plano)).toEqual(plano);
  });

  it('rejeita Plano sem Ações', () => {
    expect(() => PlanoDeAcoes.parse({ ...plano, actions: [] })).toThrow();
  });

  it('discrimina Ações por kind', () => {
    const actions = plano.actions as Action[];
    expect(actions[0]?.kind).toBe('speak');
    expect(actions[1]?.kind).toBe('express_emotion');
  });

  it('rejeita intensidade fora de [0,1] em get_dizzy', () => {
    expect(() =>
      PlanoDeAcoes.parse({
        ...plano,
        actions: [{ kind: 'get_dizzy', intensity: 1.5 }],
      }),
    ).toThrow();
  });

  it('aceita sleep action', () => {
    const withSleep = {
      ...plano,
      actions: [{ kind: 'sleep', durationMs: 5000 }],
    };
    expect(PlanoDeAcoes.parse(withSleep)).toEqual(withSleep);
  });

  it('aceita state snapshot opcional no Plano', () => {
    const withState = {
      ...plano,
      state: {
        stage: 'Filhote',
        mood: 'brincalhão',
        health: 80,
        sickness: 0,
        ageDays: 3,
        stats: { fullness: 75 },
        lastInteraction: 1725000000000,
      },
    };
    expect(PlanoDeAcoes.parse(withState)).toEqual(withState);
  });

  it('aceita Plano sem state (opcional)', () => {
    expect(PlanoDeAcoes.parse(plano)).toEqual(plano);
  });
});
