// Gera JSON Schema (draft-2020-12) a partir dos schemas Zod canônicos,
// para a Plataforma Android consumir via kotlinx.serialization. Rodar com:
// `pnpm --filter contract gen` (ou `just contract-gen`).

import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { z } from 'zod';
import { Batch, PlanoDeAcoes } from './index.js';

const here = dirname(fileURLToPath(import.meta.url));
const outDir = join(here, '..', 'schemas');
mkdirSync(outDir, { recursive: true });

const schemas = {
  'batch.json': Batch,
  'plano-de-acoes.json': PlanoDeAcoes,
};

for (const [filename, schema] of Object.entries(schemas)) {
  const json = JSON.stringify(z.toJSONSchema(schema), null, 2) + '\n';
  writeFileSync(join(outDir, filename), json);
  console.log(`gerado: schemas/${filename}`);
}

console.log(`Schemas gerados em ${outDir}`);
