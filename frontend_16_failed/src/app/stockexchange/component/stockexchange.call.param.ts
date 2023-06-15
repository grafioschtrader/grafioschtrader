import {Stockexchange} from '../../entities/stockexchange';
import {ValueKeyHtmlSelectOptions} from '../../dynamic-form/models/value.key.html.select.options';
import {StockexchangeMic} from '../model/stockexchange.base.data';

export class StockexchangeCallParam {
  public stockexchange: Stockexchange;
  public hasSecurity: boolean;
  public countriesAsHtmlOptions: ValueKeyHtmlSelectOptions[];
  public stockexchangeMics: StockexchangeMic[];
  public existingMic: Set<string>;
  public proposeChange = false;
}
