import { beforeEach, describe, expect, it } from 'vitest';
import { SimulationContextService } from './simulation.context.service';

/**
 * The Vitest setup of this project runs without a DOM, so the two session entries the service reads are backed by a
 * minimal in-memory store. Only get/set/remove/clear are used.
 */
const store = new Map<string, string>();
globalThis.sessionStorage = {
  getItem: (key: string) => (store.has(key) ? store.get(key)! : null),
  setItem: (key: string, value: string) => void store.set(key, value),
  removeItem: (key: string) => void store.delete(key),
  clear: () => store.clear(),
  key: (index: number) => [...store.keys()][index] ?? null,
  get length() {
    return store.size;
  }
} as Storage;

/**
 * The service answers a security-relevant question — may the strategy hierarchy be edited here — from two session
 * entries, so the cases that matter are the ones where those two disagree.
 */
describe('SimulationContextService', () => {
  let service: SimulationContextService;
  let switched: { idTargetTenant: number; backToHome: boolean } | null;

  beforeEach(() => {
    sessionStorage.clear();
    switched = null;
    // Only switchAndReload is reached, and it is the part that must not fire unless the session really is in an
    // environment: it reloads the whole application.
    const manageClient = {
      switchAndReload: (idTargetTenant: number, backToHome: boolean, onSwitched?: () => void) => {
        switched = { idTargetTenant, backToHome };
        onSwitched?.();
      }
    };
    service = new SimulationContextService(manageClient as never);
  });

  it('reports a simulation while the entered environment is the tenant of the session', () => {
    sessionStorage.setItem('idTenant', '42');
    service.enter(42);
    expect(service.isInSimulation()).toBe(true);
  });

  it('stops reporting a simulation once the session moved to another tenant', () => {
    sessionStorage.setItem('idTenant', '42');
    service.enter(42);
    sessionStorage.setItem('idTenant', '7');
    // A left-over entry must never keep a later context looking like a simulation.
    expect(service.isInSimulation()).toBe(false);
  });

  it('reports no simulation after leaving', () => {
    sessionStorage.setItem('idTenant', '42');
    service.enter(42);
    service.leave();
    expect(service.isInSimulation()).toBe(false);
  });

  it('reports no simulation for a managed client, which sets only the home tenant bookmark', () => {
    sessionStorage.setItem('idTenant', '7');
    sessionStorage.setItem('mainIdTenant', '42');
    expect(service.isInSimulation()).toBe(false);
  });

  it('reports no simulation for an untouched session', () => {
    expect(service.isInSimulation()).toBe(false);
  });

  it('returns home from an environment and forgets it', () => {
    sessionStorage.setItem('idTenant', '42');
    sessionStorage.setItem('mainIdTenant', '7');
    service.enter(42);

    expect(service.recoverToHome()).toBe(true);
    expect(switched).toEqual({ idTargetTenant: 7, backToHome: true });
    expect(service.isInSimulation()).toBe(false);
  });

  it('does not return home when the session is not in an environment', () => {
    sessionStorage.setItem('idTenant', '7');
    expect(service.recoverToHome()).toBe(false);
    expect(switched).toBeNull();
  });
});
