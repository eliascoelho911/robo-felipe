import { describe, expect, it } from 'vitest';
import { type AppDeps, createApp } from './app.js';
import { applyDeltas, applyStat, initialStats, type PetState, type StatName } from './pet/stats.js';
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

// Mock store em memória — better-sqlite3 segfaulta neste ambiente.
// Imita a interface pública de PetStore: load, adjust, mutate, close.
class MockPetStore {
  private states = new Map<string, PetState>();

  load(petId: string, nowMs: number): PetState {
    const existing = this.states.get(petId);
    if (!existing) {
      const state: PetState = {
        petId,
        stats: initialStats(),
        lastUpdatedMs: nowMs,
        estagio: 'Filhote',
        createdAt: nowMs,
      };
      this.states.set(petId, state);
      return state;
    }
    const updated = { ...existing, lastUpdatedMs: nowMs };
    this.states.set(petId, updated);
    return updated;
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
});
