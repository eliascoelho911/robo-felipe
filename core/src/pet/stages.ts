// Estágios do pet (ADR-023): Filhote → Jovem → Adulto, sem morte.
// O estágio deriva da stat `maturity` — é função pura, não state machine.
// Limiares: Filhote (0-29), Jovem (30-69), Adulto (70-100).

import type { PetStage } from './stats.js';

export type Estagio = PetStage;

const LIMIAR_JOVEM = 30;
const LIMIAR_ADULTO = 70;

// Devolve o estágio a partir do valor de maturity.
export function estagioOf(maturity: number): Estagio {
  if (maturity >= LIMIAR_ADULTO) return 'Adulto';
  if (maturity >= LIMIAR_JOVEM) return 'Jovem';
  return 'Filhote';
}
