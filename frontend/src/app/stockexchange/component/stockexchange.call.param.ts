import {Stockexchange} from '../../entities/stockexchange';
import {ValueKeyHtmlSelectOptions} from '../../dynamic-form/models/value.key.html.select.options';

export class StockexchangeCallParam {
  stockexchange: Stockexchange;
  hasSecurity: boolean;
  countriesAsHtmlOptions: ValueKeyHtmlSelectOptions[];
}
