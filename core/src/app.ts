// App Hono do Core. Expõe rotas HTTP REST para o estado e tools do pet
// (ADR-023 emenda: HTTP + adapter Python, não MCP). O adapter Python no
// xiaozhi-server registra cada tool como ToolType.SYSTEM_CTL, chama o Core
// via HTTP, e usa conn para enviar ações ao device.

import { type Emotion, Emotion as EmotionSchema } from '@robo-felipe/contract';
import { Hono } from 'hono';
import { cors } from 'hono/cors';
import {
  healthOf,
  moodOf,
  type PetStage,
  type StatName,
  type Stats,
  sicknessOf,
} from './pet/stats.js';
import type { PetStore } from './pet/store.js';

export interface AppDeps {
  store: PetStore;
  petId: string;
  corsOrigin: string;
  // relógio injetável (testes determinísticos; produção usa Date.now)
  now: () => number;
}

// Efeitos de cada tool write nas stats (ADR-023 §7). `happiness` entra em
// feed/play/cuddle como contribuição emocional. Valores indicativos —
// tuning fino fica para iteração.
const TOOL_DELTAS: Record<string, Partial<Record<StatName, number>>> = {
  feed: { fullness: 25, affection: 5, happiness: 10 },
  play: { playfulness: 20, sociability: 10, happiness: 10, energy: -15 },
  rest: { energy: 30, serenity: 10 },
  clean: { cleanliness: 30, comfort: 10, happiness: 5 },
  cuddle: { affection: 15, comfort: 10, happiness: 10 },
  heal: { comfort: 10 },
  train: { intelligence: 15, focus: 10, maturity: 5, energy: -10 },
  dance: { playfulness: 15, happiness: 10, energy: -10 },
  express_emotion: {},
  get_dizzy: { focus: -20 },
};

const WRITE_TOOLS = Object.keys(TOOL_DELTAS);

// Snapshot de resposta — corresponde a PetStateSnapshot do contract:
// {stage, mood, health, sickness, ageDays, stats, lastInteraction}.
interface StateResponse {
  stage: PetStage;
  mood: Emotion;
  health: number;
  sickness: number;
  ageDays: number;
  stats: Stats;
  lastInteraction: number;
}

const MS_PER_DAY = 24 * 60 * 60 * 1000;

function toResponse(state: {
  stats: Stats;
  lastUpdatedMs: number;
  estagio: PetStage;
  createdAt: number;
}): StateResponse {
  const health = healthOf(state.stats);
  return {
    stage: state.estagio,
    mood: moodOf(state.stats),
    health,
    sickness: sicknessOf(health),
    ageDays: Math.floor((state.lastUpdatedMs - state.createdAt) / MS_PER_DAY),
    stats: state.stats,
    lastInteraction: state.lastUpdatedMs,
  };
}

export function createApp(deps: AppDeps): Hono {
  const app = new Hono();

  app.use('/pet', cors({ origin: deps.corsOrigin }));
  app.use('/pet/*', cors({ origin: deps.corsOrigin }));

  app.get('/health', (c) => c.json({ status: 'ok' }));

  app.get('/pet/:id/state', (c) => {
    const id = c.req.param('id');
    const state = deps.store.load(id, deps.now());
    return c.json(toResponse(state));
  });

  app.get('/pet/:id/mood', (c) => {
    const id = c.req.param('id');
    const state = deps.store.load(id, deps.now());
    return c.json({ mood: moodOf(state.stats) });
  });

  app.post('/pet/:id/:tool', async (c) => {
    const id = c.req.param('id');
    const tool = c.req.param('tool');

    if (!WRITE_TOOLS.includes(tool)) {
      return c.json({ error: `Tool desconhecida: ${tool}` }, 404);
    }

    // express_emotion aceita body opcional com emotion (validada pelo schema)
    if (tool === 'express_emotion') {
      const body = await c.req.json().catch(() => ({}));
      const emotionParse = EmotionSchema.safeParse(body?.emotion ?? 'happy');
      if (!emotionParse.success) {
        return c.json({ error: 'emotion inválida' }, 400);
      }
    }

    const deltas = TOOL_DELTAS[tool] ?? {};
    const state = deps.store.mutate(id, deltas, deps.now());
    return c.json(toResponse(state));
  });

  return app;
}

export default createApp;
