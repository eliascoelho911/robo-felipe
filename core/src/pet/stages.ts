// Máquina de estágios do pet (ADR-023): Filhote → Jovem → Adulto, sem morte.
// As transições são guardadas por experiência — o Core decide quando enviar
// CRESCER com base no limiar. O estado da máquina é o estágio; a experiência
// vive no contexto.

import { type Actor, assign, setup } from 'xstate';

export type Estagio = 'Filhote' | 'Jovem' | 'Adulto';

export interface PetContext {
  experiencia: number;
}

export type PetEvent = { type: 'GANHAR_EXP'; quantidade: number } | { type: 'CRESCER' };

const LIMIAR_JOVEM = 30;
const LIMIAR_ADULTO = 70;

export const petMachine = setup({
  types: {} as { context: PetContext; events: PetEvent },
  guards: {
    podeCrescerParaJovem: ({ context }) => context.experiencia >= LIMIAR_JOVEM,
    podeCrescerParaAdulto: ({ context }) => context.experiencia >= LIMIAR_ADULTO,
  },
  actions: {
    ganharExp: assign({
      experiencia: ({ context, event }) =>
        event.type === 'GANHAR_EXP' ? context.experiencia + event.quantidade : context.experiencia,
    }),
  },
}).createMachine({
  id: 'pet',
  initial: 'Filhote',
  context: { experiencia: 0 },
  on: {
    GANHAR_EXP: { actions: 'ganharExp' },
  },
  states: {
    Filhote: {
      on: {
        CRESCER: { target: 'Jovem', guard: 'podeCrescerParaJovem' },
      },
    },
    Jovem: {
      on: {
        CRESCER: { target: 'Adulto', guard: 'podeCrescerParaAdulto' },
      },
    },
    Adulto: { type: 'final' },
  },
});

export type PetActor = Actor<typeof petMachine>;

// Devolve o estágio atual a partir do snapshot do actor.
export function estagioOf(actor: PetActor): Estagio {
  return actor.getSnapshot().value as Estagio;
}
