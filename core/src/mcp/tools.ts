// Catálogo de MCP tools exposto ao LLM (ADR-023). As tools manipulam o
// estado do pet via PetStore; o LLM as chama para interagir com o Tamagotchi.

import type { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { z } from 'zod';
import { healthOf, STAT_NAMES, type StatName } from '../pet/stats.js';
import type { PetStore } from '../pet/store.js';

export interface ToolDeps {
  store: PetStore;
  petId: string;
  // relógio injetável para testes determinísticos
  now: () => number;
}

function snapshotText(deps: ToolDeps): string {
  const state = deps.store.load(deps.petId, deps.now());
  return JSON.stringify({ ...state, health: healthOf(state.stats) });
}

export function registerPetTools(server: McpServer, deps: ToolDeps): void {
  server.tool(
    'pet_status',
    'Devolve as stats atuais do pet, o health derivado e o estágio.',
    {},
    async () => ({
      content: [{ type: 'text' as const, text: snapshotText(deps) }],
    }),
  );

  server.tool(
    'pet_alimentar',
    'Aumenta a saciedade (satiety) do pet em até 50 pontos.',
    { quantidade: z.number().min(1).max(50) },
    async ({ quantidade }) => {
      const state = deps.store.adjust(deps.petId, 'satiety', quantidade, deps.now());
      return {
        content: [
          {
            type: 'text' as const,
            text: JSON.stringify({ ...state, health: healthOf(state.stats) }),
          },
        ],
      };
    },
  );

  server.tool(
    'pet_brincar',
    'Aumenta fun e happiness do pet.',
    { quantidade: z.number().min(1).max(50) },
    async ({ quantidade }) => {
      const t1 = deps.store.adjust(deps.petId, 'fun', quantidade, deps.now());
      const t2 = deps.store.adjust(deps.petId, 'happiness', quantidade, deps.now());
      return {
        content: [
          { type: 'text' as const, text: JSON.stringify({ ...t2, health: healthOf(t1.stats) }) },
        ],
      };
    },
  );

  server.tool(
    'pet_dar_afeto',
    'Aumenta a affection e a socialization do pet.',
    { quantidade: z.number().min(1).max(50) },
    async ({ quantidade }) => {
      deps.store.adjust(deps.petId, 'affection', quantidade, deps.now());
      const state = deps.store.adjust(deps.petId, 'socialization', quantidade, deps.now());
      return {
        content: [
          {
            type: 'text' as const,
            text: JSON.stringify({ ...state, health: healthOf(state.stats) }),
          },
        ],
      };
    },
  );

  server.tool(
    'pet_descansar',
    'Aumenta a energy do pet.',
    { quantidade: z.number().min(1).max(50) },
    async ({ quantidade }) => {
      const state = deps.store.adjust(deps.petId, 'energy', quantidade, deps.now());
      return {
        content: [
          {
            type: 'text' as const,
            text: JSON.stringify({ ...state, health: healthOf(state.stats) }),
          },
        ],
      };
    },
  );

  server.tool(
    'pet_ajustar_stat',
    'Ajusta diretamente uma das 17 stats editáveis (health não é editável).',
    {
      stat: z.enum(STAT_NAMES as unknown as [StatName, ...StatName[]]),
      delta: z.number().min(-100).max(100),
    },
    async ({ stat, delta }) => {
      const state = deps.store.adjust(deps.petId, stat, delta, deps.now());
      return {
        content: [
          {
            type: 'text' as const,
            text: JSON.stringify({ ...state, health: healthOf(state.stats) }),
          },
        ],
      };
    },
  );
}
