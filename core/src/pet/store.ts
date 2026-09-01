// Persistência do estado canônico do pet (cloud-primary, ADR-023).
// SQLite via better-sqlite3 — arquivo local no Core (MVP PC→VPS). O arquivo
// .db é gitignored; nunca commitar o banco. Sem fallback NVS no MVP.

import type { Database as DB } from 'better-sqlite3';
import Database from 'better-sqlite3';
import { estagioOf } from './stages.js';
import {
  applyDeltas,
  applyStat,
  decay,
  initialStats,
  type PetState,
  STAGE_DECAY_MULTIPLIER,
  type StatName,
  type Stats,
} from './stats.js';

interface PetRow {
  stats_json: string;
  last_updated_ms: number;
  created_at: number;
}

export class PetStore {
  private readonly db: DB;

  constructor(dbPath: string) {
    this.db = new Database(dbPath);
    this.db.pragma('journal_mode = WAL');
    this.db.exec(`
      CREATE TABLE IF NOT EXISTS pet_state (
        pet_id     TEXT PRIMARY KEY,
        stats_json TEXT NOT NULL,
        last_updated_ms INTEGER NOT NULL,
        created_at INTEGER NOT NULL
      )
    `);
    // migrar tabela pré-created_at: adiciona coluna se faltar
    try {
      this.db.exec('ALTER TABLE pet_state ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0');
    } catch {
      // coluna já existe — ignora
    }
  }

  // Carrega (ou cria) o estado do pet. Aplica decay até o instante `nowMs`.
  load(petId: string, nowMs: number): PetState {
    const row = this.db
      .prepare<[string], PetRow>(
        'SELECT stats_json, last_updated_ms, created_at FROM pet_state WHERE pet_id = ?',
      )
      .get(petId);

    if (!row) {
      const stats = initialStats();
      const state: PetState = {
        petId,
        stats,
        lastUpdatedMs: nowMs,
        estagio: estagioOf(stats.maturity),
        createdAt: nowMs,
      };
      this.persist(state);
      return state;
    }

    const stats = JSON.parse(row.stats_json) as Stats;
    const stage = estagioOf(stats.maturity);
    const decayedStats = decay(stats, row.last_updated_ms, nowMs, STAGE_DECAY_MULTIPLIER[stage]);
    return {
      petId,
      stats: decayedStats,
      lastUpdatedMs: nowMs,
      estagio: estagioOf(decayedStats.maturity),
      createdAt: row.created_at,
    };
  }

  // Incrementa uma stat e persiste. Devolve o novo estado (com decay aplicado).
  adjust(petId: string, stat: StatName, delta: number, nowMs: number): PetState {
    const current = this.load(petId, nowMs);
    const nextStats = applyStat(current.stats, stat, delta);
    const state: PetState = {
      petId,
      stats: nextStats,
      lastUpdatedMs: nowMs,
      estagio: estagioOf(nextStats.maturity),
      createdAt: current.createdAt,
    };
    this.persist(state);
    return state;
  }

  // Aplica múltiplos deltas de uma vez (tools que mutam várias stats) e persiste.
  mutate(petId: string, deltas: Partial<Record<StatName, number>>, nowMs: number): PetState {
    const current = this.load(petId, nowMs);
    const nextStats = applyDeltas(current.stats, deltas);
    const state: PetState = {
      petId,
      stats: nextStats,
      lastUpdatedMs: nowMs,
      estagio: estagioOf(nextStats.maturity),
      createdAt: current.createdAt,
    };
    this.persist(state);
    return state;
  }

  private persist(state: PetState): void {
    this.db
      .prepare<[string, string, number, number], unknown>(
        'INSERT INTO pet_state (pet_id, stats_json, last_updated_ms, created_at) VALUES (?, ?, ?, ?) ON CONFLICT(pet_id) DO UPDATE SET stats_json = excluded.stats_json, last_updated_ms = excluded.last_updated_ms',
      )
      .run(state.petId, JSON.stringify(state.stats), state.lastUpdatedMs, state.createdAt);
  }

  close(): void {
    this.db.close();
  }
}
