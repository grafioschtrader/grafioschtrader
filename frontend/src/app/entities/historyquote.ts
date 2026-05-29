import {Exclude} from 'class-transformer';
import {HistoryquoteBase} from './historyquote.base';

export {HistoryquoteCreateType} from './historyquote.base';

export class Historyquote extends HistoryquoteBase {
  idHistoryQuote?: number;
  createModifyTime: string | Date;

  @Exclude()
  override getId(): number {
    return this.idHistoryQuote;
  }
}
