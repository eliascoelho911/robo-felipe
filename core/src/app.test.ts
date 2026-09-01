import { describe, expect, it } from 'vitest';
import { type AppDeps, createApp } from './app.js';
import { estagioOf } from './pet/stages.js';
import {
  applyDeltas,
  applyStat,
  decay,
  initialStats,
  type PetState,
  STAGE_DECAY_MULTIPLIER,
  type StatName,
} from './pet/stats.js';
import type { PetStore } from './pet/store.js';

type StateBody = {
  stage: string;
  mood: string;
  health: number;
  sickness: number;
  ageDays: number;
  stats: Record<string, number>;
  lastInteraction: number;
};

type PlanoBody = {
  version: number;
  batchId: string;
  actions: {
    kind: string;
    text?: string;
    emotion?: string;
    intensity?: number;
    durationMs?: number;
  }[];
  state?: StateBody;
};

// Mock store em memória — better-sqlite3 segfaulta neste ambiente.
// Imita a interface pública de PetStore: load (aplica decay, não persiste),
// adjust, mutate (persiste), close.
class MockPetStore {
  private states = new Map<string, PetState>();

  load(petId: string, nowMs: number): PetState {
    const existing = this.states.get(petId);
    if (!existing) {
      const stats = initialStats();
      const state: PetState = {
        petId,
        stats,
        lastUpdatedMs: nowMs,
        estagio: estagioOf(stats.maturity),
        createdAt: nowMs,
      };
      this.states.set(petId, state);
      return state;
    }
    const stage = estagioOf(existing.stats.maturity);
    const decayedStats = decay(
      existing.stats,
      existing.lastUpdatedMs,
      nowMs,
      STAGE_DECAY_MULTIPLIER[stage],
    );
    return {
      petId,
      stats: decayedStats,
      lastUpdatedMs: nowMs,
      estagio: estagioOf(decayedStats.maturity),
      createdAt: existing.createdAt,
    };
  }

  adjust(petId: string, stat: StatName, delta: number, nowMs: number): PetState {
    const current = this.load(petId, nowMs);
    const state: PetState = {
      ...current,
      stats: applyStat(current.stats, stat, delta),
      lastUpdatedMs: nowMs,
    };
    this.states.set(petId, state);
    return state;
  }

  advance(petId: string, nowMs: number): PetState {
    const state = this.load(petId, nowMs);
    this.states.set(petId, state);
    return state;
  }

  mutate(petId: string, deltas: Partial<Record<StatName, number>>, nowMs: number): PetState {
    const current = this.load(petId, nowMs);
    const state: PetState = {
      ...current,
      stats: applyDeltas(current.stats, deltas),
      lastUpdatedMs: nowMs,
    };
    this.states.set(petId, state);
    return state;
  }

  close(): void {}
}

function makeDeps(overrides: Partial<AppDeps> = {}): AppDeps {
  return {
    store: new MockPetStore() as unknown as PetStore,
    petId: 'felipe',
    corsOrigin: '*',
    now: () => 1_000_000,
    ...overrides,
  };
}

describe('app (HTTP endpoints)', () => {
  it('GET /health retorna { status: "ok" }', async () => {
    const app = createApp(makeDeps());
    const res = await app.request('/health');
    expect(res.status).toBe(200);
    const body = (await res.json()) as { status: string };
    expect(body).toEqual({ status: 'ok' });
  });

  it('GET /pet/:id/state retorna snapshot com todos os campos', async () => {
    const app = createApp(makeDeps());
    const res = await app.request('/pet/felipe/state');
    expect(res.status).toBe(200);
    const body = (await res.json()) as StateBody;
    expect(body).toHaveProperty('stage');
    expect(body).toHaveProperty('mood');
    expect(body).toHaveProperty('health');
    expect(body).toHaveProperty('sickness');
    expect(body).toHaveProperty('ageDays');
    expect(body).toHaveProperty('stats');
    expect(body).toHaveProperty('lastInteraction');
    expect(body.stats).toHaveProperty('fullness');
    expect(body.stats).toHaveProperty('happiness');
    expect(body.stats).not.toHaveProperty('health');
  });

  it('GET /pet/:id/mood retorna { mood }', async () => {
    const app = createApp(makeDeps());
    const res = await app.request('/pet/felipe/mood');
    expect(res.status).toBe(200);
    const body = (await res.json()) as { mood: string };
    expect(body).toHaveProperty('mood');
    expect(typeof body.mood).toBe('string');
  });

  it('POST /pet/:id/feed muta stats e retorna estado atualizado', async () => {
    const app = createApp(makeDeps());
    const res = await app.request('/pet/felipe/feed', { method: 'POST' });
    expect(res.status).toBe(200);
    const body = (await res.json()) as StateBody;
    expect(body.stats.fullness).toBeGreaterThan(80);
  });

  it('POST /pet/:id/express_emotion com emotion válida retorna 200', async () => {
    const app = createApp(makeDeps());
    const res = await app.request('/pet/felipe/express_emotion', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ emotion: 'happy' }),
    });
    expect(res.status).toBe(200);
  });

  it('POST /pet/:id/express_emotion com emotion inválida retorna 400', async () => {
    const app = createApp(makeDeps());
    const res = await app.request('/pet/felipe/express_emotion', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ emotion: 'invalid_mood' }),
    });
    expect(res.status).toBe(400);
  });

  it('POST /pet/:id/express_emotion sem body usa default happy', async () => {
    const app = createApp(makeDeps());
    const res = await app.request('/pet/felipe/express_emotion', { method: 'POST' });
    expect(res.status).toBe(200);
  });

  it('POST /pet/:id/unknown retorna 404', async () => {
    const app = createApp(makeDeps());
    const res = await app.request('/pet/felipe/unknown', { method: 'POST' });
    expect(res.status).toBe(404);
  });

  it('health é derivado — não aparece nas stats mas aparece no snapshot', async () => {
    const app = createApp(makeDeps());
    const res = await app.request('/pet/felipe/state');
    const body = (await res.json()) as StateBody;
    expect(body.stats).not.toHaveProperty('health');
    expect(body).toHaveProperty('health');
    expect(typeof body.health).toBe('number');
  });

  it('sickness é 0 quando health >= 30 (stats em 80)', async () => {
    const app = createApp(makeDeps());
    const res = await app.request('/pet/felipe/state');
    const body = (await res.json()) as StateBody;
    expect(body.sickness).toBe(0);
  });

  it('ageDays é calculado a partir de createdAt', async () => {
    let now = 1_000_000;
    const app = createApp(makeDeps({ now: () => now }));
    await app.request('/pet/felipe/state');
    now += 3 * 24 * 60 * 60 * 1000;
    const res = await app.request('/pet/felipe/state');
    const body = (await res.json()) as StateBody;
    expect(body.ageDays).toBe(3);
  });

  it('todas as 10 tools write aceitam POST', async () => {
    const app = createApp(makeDeps());
    const tools = [
      'feed',
      'play',
      'rest',
      'clean',
      'cuddle',
      'heal',
      'train',
      'dance',
      'express_emotion',
      'get_dizzy',
    ];
    for (const tool of tools) {
      const res = await app.request(`/pet/felipe/${tool}`, { method: 'POST' });
      expect(res.status).toBe(200);
    }
  });

  describe('POST /batch', () => {
    const validBatch = {
      version: 1,
      batchId: 'f47ac10b-58cc-4372-a567-0e02b2c3d479',
      platformId: 'android-sobrinho',
      petId: 'felipe',
      triggers: [
        {
          id: '6ec0bd7f-11c0-43dc-a75b-2a90c20d8b1c',
          kind: 'button',
          timestamp: 1_000_000,
          payload: {},
        },
      ],
    };

    function postBatch(app: ReturnType<typeof createApp>, body: unknown) {
      return app.request('/batch', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
    }

    it('Batch com 1 Trigger button → Plano com [speak{oi!}] + state', async () => {
      const app = createApp(makeDeps());
      const res = await postBatch(app, validBatch);
      expect(res.status).toBe(200);
      const body = (await res.json()) as PlanoBody;
      expect(body.version).toBe(1);
      expect(body.batchId).toBe(validBatch.batchId);
      expect(body.actions).toHaveLength(1);
      expect(body.actions[0]?.kind).toBe('speak');
      expect(body.actions[0]?.text).toBe('Oi! Que bom te ver!');
      expect(body.state).toBeDefined();
      expect(body.state?.stats).toHaveProperty('fullness');
    });

    it('Batch com 1 Trigger shake (courage alto) → Plano com [get_dizzy]', async () => {
      const app = createApp(makeDeps());
      const res = await postBatch(app, {
        ...validBatch,
        triggers: [
          {
            id: '6ec0bd7f-11c0-43dc-a75b-2a90c20d8b1c',
            kind: 'shake',
            timestamp: 1_000_000,
            payload: {},
          },
        ],
      });
      expect(res.status).toBe(200);
      const body = (await res.json()) as PlanoBody;
      expect(body.actions).toHaveLength(1);
      expect(body.actions[0]?.kind).toBe('get_dizzy');
    });

    it('Batch com 1 Trigger shake (courage baixo) → Plano com [express_emotion{scared}]', async () => {
      const mockStore = new MockPetStore();
      const deps = makeDeps({ store: mockStore as unknown as PetStore });
      mockStore.adjust('felipe', 'courage', -80, 1_000_000);
      const app = createApp(deps);
      const res = await postBatch(app, {
        ...validBatch,
        triggers: [
          {
            id: '6ec0bd7f-11c0-43dc-a75b-2a90c20d8b1c',
            kind: 'shake',
            timestamp: 1_000_000,
            payload: {},
          },
        ],
      });
      expect(res.status).toBe(200);
      const body = (await res.json()) as PlanoBody;
      expect(body.actions).toHaveLength(1);
      expect(body.actions[0]?.kind).toBe('express_emotion');
      expect(body.actions[0]?.emotion).toBe('scared');
    });

    it('Batch com 1 Trigger manual → Plano com [] + state (snapshot only)', async () => {
      const app = createApp(makeDeps());
      const res = await postBatch(app, {
        ...validBatch,
        triggers: [
          {
            id: '6ec0bd7f-11c0-43dc-a75b-2a90c20d8b1c',
            kind: 'manual',
            timestamp: 1_000_000,
            payload: {},
          },
        ],
      });
      expect(res.status).toBe(200);
      const body = (await res.json()) as PlanoBody;
      expect(body.actions).toHaveLength(0);
      expect(body.state).toBeDefined();
    });

    it('Batch com 1 Trigger voice → Plano com [] (sem Ações no Plano)', async () => {
      const app = createApp(makeDeps());
      const res = await postBatch(app, {
        ...validBatch,
        triggers: [
          {
            id: '6ec0bd7f-11c0-43dc-a75b-2a90c20d8b1c',
            kind: 'voice',
            timestamp: 1_000_000,
            payload: {},
          },
        ],
      });
      expect(res.status).toBe(200);
      const body = (await res.json()) as PlanoBody;
      expect(body.actions).toHaveLength(0);
      expect(body.state).toBeDefined();
    });

    it('Batch com múltiplos Triggers → Plano com Ações concatenadas', async () => {
      const app = createApp(makeDeps());
      const res = await postBatch(app, {
        ...validBatch,
        triggers: [
          {
            id: '6ec0bd7f-11c0-43dc-a75b-2a90c20d8b1c',
            kind: 'button',
            timestamp: 1_000_000,
            payload: {},
          },
          {
            id: '7ec0bd7f-11c0-43dc-a75b-2a90c20d8b1c',
            kind: 'shake',
            timestamp: 1_000_000,
            payload: {},
          },
          {
            id: '8ec0bd7f-11c0-43dc-a75b-2a90c20d8b1c',
            kind: 'manual',
            timestamp: 1_000_000,
            payload: {},
          },
        ],
      });
      expect(res.status).toBe(200);
      const body = (await res.json()) as PlanoBody;
      expect(body.actions).toHaveLength(2);
      expect(body.actions[0]?.kind).toBe('speak');
      expect(body.actions[1]?.kind).toBe('get_dizzy');
    });

    it('Batch inválido (sem triggers) → 400', async () => {
      const app = createApp(makeDeps());
      const res = await postBatch(app, { ...validBatch, triggers: [] });
      expect(res.status).toBe(400);
    });

    it('Batch inválido (sem version) → 400', async () => {
      const app = createApp(makeDeps());
      const res = await postBatch(app, {
        batchId: validBatch.batchId,
        platformId: validBatch.platformId,
        petId: validBatch.petId,
        triggers: validBatch.triggers,
      });
      expect(res.status).toBe(400);
    });

    it('Batch com body não-JSON → 400', async () => {
      const app = createApp(makeDeps());
      const res = await app.request('/batch', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: 'not-json',
      });
      expect(res.status).toBe(400);
    });

    it('Batch com petId errado → 404', async () => {
      const app = createApp(makeDeps());
      const res = await postBatch(app, { ...validBatch, petId: 'outro-pet' });
      expect(res.status).toBe(404);
    });

    it('advanceStats: Batch após tempo decorrido → stats decaídas no state', async () => {
      let now = 1_000_000;
      const app = createApp(makeDeps({ now: () => now }));
      await app.request('/pet/felipe/state');
      now += 24 * 60 * 60 * 1000;
      const res = await postBatch(app, validBatch);
      expect(res.status).toBe(200);
      const body = (await res.json()) as PlanoBody;
      expect(body.state?.stats.fullness).toBeLessThan(80);
    });

    it('advanceStats: elapsed=0 (sem decay) → stats permanecem em 80', async () => {
      const app = createApp(makeDeps());
      const res = await postBatch(app, validBatch);
      expect(res.status).toBe(200);
      const body = (await res.json()) as PlanoBody;
      expect(body.state?.stats.fullness).toBe(80);
    });

    it('idempotência: mesmo batchId → mesmo Plano (sem duplo decay)', async () => {
      let now = 1_000_000;
      const app = createApp(makeDeps({ now: () => now }));
      const res1 = await postBatch(app, validBatch);
      const body1 = (await res1.json()) as PlanoBody;
      now += 24 * 60 * 60 * 1000;
      const res2 = await postBatch(app, validBatch);
      const body2 = (await res2.json()) as PlanoBody;
      expect(body2.state?.stats.fullness).toBe(body1.state?.stats.fullness);
    });
  });
});
