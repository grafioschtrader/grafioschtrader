import {Exclude} from 'class-transformer';
import {HistoryquoteBase} from './historyquote.base';

/**
 * One row of the {@code historyquote_legacy} shadow archive. Mirrors the backend HistoryquoteLegacy entity. Besides the
 * shared OHLCV/date/createType fields it carries the archival {@code transferDate} (managed by the system, read-only in
 * the UI) and its own primary key, so individual rows can be edited and deleted.
 */
export class HistoryquoteLegacy extends HistoryquoteBase {
  idHistoryquoteLegacy?: number;
  /** Day the row was archived (when copyLiveToLegacy ran). Read-only in the UI. */
  transferDate?: string;

  @Exclude()
  override getId(): number {
    return this.idHistoryquoteLegacy;
  }
}
