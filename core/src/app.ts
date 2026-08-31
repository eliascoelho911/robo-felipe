// App Hono do Core. Expõe o endpoint /mcp (MCP sobre Streamable HTTP) e
// /health. O transporte web-standard é compatível com Hono (retorna Response).
// Padrão: um transporte por sessão, identificado pelo header mcp-session-id.
// GET/POST/DELETE em /mcp são delegados ao transporte, que trata cada verbo.

import { randomUUID } from 'node:crypto';
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { WebStandardStreamableHTTPServerTransport } from '@modelcontextprotocol/sdk/server/webStandardStreamableHttp.js';
import { Hono } from 'hono';
import { cors } from 'hono/cors';
import { registerPetTools } from './mcp/tools.js';
import type { PetStore } from './pet/store.js';

export interface AppDeps {
  store: PetStore;
  petId: string;
  corsOrigin: string;
  // relógio injetável (testes determinísticos; produção usa Date.now)
  now: () => number;
}

export function createApp(deps: AppDeps): Hono {
  const app = new Hono();
  const sessions = new Map<string, WebStandardStreamableHTTPServerTransport>();

  app.use('/mcp', cors({ origin: deps.corsOrigin, exposeHeaders: ['Mcp-Session-Id'] }));

  function createServer(): McpServer {
    const server = new McpServer({
      name: 'robo-felipe-core',
      version: '0.0.0',
    });
    registerPetTools(server, {
      store: deps.store,
      petId: deps.petId,
      now: deps.now,
    });
    return server;
  }

  // Todos os verbos em /mcp são tratados pelo transporte web-standard.
  // Sessões existentes delegam ao transporte armazenado; sessões novas são
  // inicializadas apenas em POST (sem header mcp-session-id).
  app.all('/mcp', async (c) => {
    const sid = c.req.header('mcp-session-id');
    if (sid && sessions.has(sid)) {
      return await sessions.get(sid)!.handleRequest(c.req.raw);
    }
    if (sid && !sessions.has(sid)) {
      return c.text('Session not found', 404);
    }
    // nova sessão (POST de inicialização, sem header de sessão)
    const transport = new WebStandardStreamableHTTPServerTransport({
      sessionIdGenerator: randomUUID,
      onsessioninitialized: (id) => {
        sessions.set(id, transport);
      },
      onsessionclosed: (id) => {
        sessions.delete(id);
      },
    });
    await createServer().connect(transport);
    return await transport.handleRequest(c.req.raw);
  });

  app.get('/health', (c) => c.json({ status: 'ok' }));

  return app;
}

export default createApp;
