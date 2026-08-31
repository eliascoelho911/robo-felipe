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
        kind: 'voz',
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
    acoes: [
      { kind: 'falar', texto: 'Oi, Sobrinho!' },
      { kind: 'expressar_emocao', emocao: 'feliz' },
    ],
  };

  it('aceita um Plano válido com Ações múltiplas', () => {
    expect(PlanoDeAcoes.parse(plano)).toEqual(plano);
  });

  it('rejeita Plano sem Ações', () => {
    expect(() => PlanoDeAcoes.parse({ ...plano, acoes: [] })).toThrow();
  });

  it('discrimina Ações por kind', () => {
    const acoes = plano.acoes as Action[];
    expect(acoes[0]?.kind).toBe('falar');
    expect(acoes[1]?.kind).toBe('expressar_emocao');
  });

  it('rejeita intensidade fora de [0,1] em ficar_tonto', () => {
    expect(() =>
      PlanoDeAcoes.parse({
        ...plano,
        acoes: [{ kind: 'ficar_tonto', intensidade: 1.5 }],
      }),
    ).toThrow();
  });
});
