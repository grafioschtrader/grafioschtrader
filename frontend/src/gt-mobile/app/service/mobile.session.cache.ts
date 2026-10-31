import { signal } from '@angular/core';
import { GlobalSessionNames } from '../../../app/lib/global.session.names';

/**
 * Results last read, kept so a detail page opens with the figures its list just showed and follows a refresh started
 * there. Reads are signals, so a page shows a newer result as soon as it is stored. A result read in an earlier session
 * is never handed out, because another user may have signed in since.
 */
export abstract class MobileSessionCache<K, V> {
  private readonly entriesState = signal<ReadonlyMap<K, V>>(new Map());
  private storedForToken: string | null = null;

  /**
   * The result last stored under a key in this session.
   *
   * @param key Key of the result
   * @returns The result, or undefined when it was not read yet
   */
  get(key: K): V | undefined {
    const entries = this.entriesState();
    return this.storedForToken === sessionStorage.getItem(GlobalSessionNames.JWT) ? entries.get(key) : undefined;
  }

  /** Keeps a result just read; results of an earlier session are dropped. */
  put(key: K, value: V): void {
    const token = sessionStorage.getItem(GlobalSessionNames.JWT);
    const entries = this.storedForToken === token ? this.entriesState() : new Map<K, V>();
    this.storedForToken = token;
    this.entriesState.set(new Map(entries).set(key, value));
  }
}
