// Persistência do estado canônico do pet (cloud-primary, ADR-023).
// SQLite via better-sqlite3 — arquivo local no Core (MVP PC→VPS). O arquivo
// .db é gitignored; nunca commitar o banco. Sem fallback NVS no MVP.

import type { Database as DB } from 'better-sqlite3';
import Database from 'better-sqlite3';
import { createActor } from 'xstate';
import { estagioOf, type PetActor, petMachine } from './stages.js';
import {
  applyDeltas,
  applyStat,
  decay,
  initialStats,
  type PetState,
  type StatName,
  type Stats,
} from './stats.js';

export class PetStore {
  private readonly db: DB;
  // um actor por petId, em memória; o estado canônico é persistido no SQLite
  private readonly actors = new Map<string, PetActor>();

  constructor(dbPath: string) {
    this.db = new Database(dbPath);
    this.db.pragma('journal_mode = WAL');
    this.db.exec(`
      CREATE TABLE IF NOT EXISTS pet_state (
        pet_id     TEXT PRIMARY KEY,
        stats_json TEXT NOT NULL,
        last_updated_ms INTEGER NOT NULL,
        experiencia REAL NOT NULL DEFAULT 0
      )
    `);
  }

  // Carrega (ou cria) o estado do pet. Aplica decay até o instante `nowMs`.
  load(petId: string, nowMs: number): PetState {
    const actor = this.getActor(petId);
    const row = this.db
      .prepare<[string], { stats_json: string; last_updated_ms: number; experiencia: number }>(
        'SELECT stats_json, last_updated_ms, experiencia FROM pet_state WHERE pet_id = ?',
      )
      .get(petId);

    if (!row) {
      const stats = initialStats();
      const state: PetState = {
        petId,
        stats,
        lastUpdatedMs: nowMs,
        estagio: estagioOf(actor),
      };
      this.persist(state, 0);
      return state;
    }

    let stats = JSON.parse(row.stats_json) as Stats;
    stats = decay(stats, row.last_updated_ms, nowMs);
    // sincroniza experiência do actor
    actor.send({
      type: 'GANHAR_EXP',
      quantidade: row.experiencia - actor.getSnapshot().context.experiencia,
    });
    return {
      petId,
      stats,
      lastUpdatedMs: nowMs,
      estagio: estagioOf(actor),
    };
  }

  // Incrementa uma stat e persiste. Devolve o novo estado (com decay aplicado).
  adjust(petId: string, stat: StatName, delta: number, nowMs: number): PetState {
    const current = this.load(petId, nowMs);
    const nextStats = applyStat(current.stats, stat, delta);
    const actor = this.getActor(petId);
    const state: PetState = {
      petId,
      stats: nextStats,
      lastUpdatedMs: nowMs,
      estagio: estagioOf(actor),
    };
    this.persist(state, actor.getSnapshot().context.experiencia);
    return state;
  }

  // Aplica múltiplos deltas de uma vez (tools que mutam várias stats) e persiste.
  mutate(petId: string, deltas: Partial<Record<StatName, number>>, nowMs: number): PetState {
    const current = this.load(petId, nowMs);
    const nextStats = applyDeltas(current.stats, deltas);
    const actor = this.getActor(petId);
    const state: PetState = {
      petId,
      stats: nextStats,
      lastUpdatedMs: nowMs,
      estagio: estagioOf(actor),
    };
    this.persist(state, actor.getSnapshot().context.experiencia);
    return state;
  }

  private getActor(petId: string): PetActor {
    let actor = this.actors.get(petId);
    if (!actor) {
      actor = createActor(petMachine).start();
      this.actors.set(petId, actor);
    }
    return actor;
  }

  private persist(state: PetState, experiencia: number): void {
    this.db
      .prepare<[string, string, number, number], unknown>(
        'INSERT INTO pet_state (pet_id, stats_json, last_updated_ms, experiencia) VALUES (?, ?, ?, ?) ON CONFLICT(pet_id) DO UPDATE SET stats_json = excluded.stats_json, last_updated_ms = excluded.last_updated_ms, experiencia = excluded.experiencia',
      )
      .run(state.petId, JSON.stringify(state.stats), state.lastUpdatedMs, experiencia);
  }

  close(): void {
    this.db.close();
  }
}
