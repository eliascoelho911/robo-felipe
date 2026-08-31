// Bootstrap do servidor do Core. Lê config do ambiente (ver .env.example)
// e serve o app Hono via @hono/node-server.

import { serve } from '@hono/node-server';
import { createApp } from './app.js';
import { PetStore } from './pet/store.js';

const port = Number(process.env.CORE_PORT ?? 3000);
const dbPath = process.env.CORE_DB_PATH ?? './felipe.db';
const petId = process.env.CORE_PET_ID ?? 'felipe-tamagotchi';
const corsOrigin = process.env.CORE_CORS_ORIGIN ?? '*';

const store = new PetStore(dbPath);
const app = createApp({ store, petId, corsOrigin, now: () => Date.now() });

serve({ fetch: app.fetch, port }, (info) => {
  console.log(`Core do Robô Felipe ouvindo em http://localhost:${info.port}/mcp`);
});
