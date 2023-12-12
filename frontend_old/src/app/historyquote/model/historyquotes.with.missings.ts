import {Securitycurrency} from '../../entities/securitycurrency';
import {Historyquote} from '../../entities/historyquote';
import {Currencypair} from '../../entities/currencypair';

import {IHistoryquoteQuality} from '../../entities/view/ihistoryquote.quality';
import {SupportedCSVFormats} from '../../shared/generaldialog/model/file.upload.param';

export interface HistoryquotesWithMissings {
  historyquoteList: Historyquote[];
  securitycurrency: Securitycurrency | Currencypair;
  historyquoteQuality: IHistoryquoteQuality;
  supportedCSVFormats: SupportedCSVFormats;
}


