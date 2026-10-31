import type { Stockexchange } from '../../entities/stockexchange';
import type { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import type { StockexchangeMic } from '../model/stockexchange.base.data';

export class StockexchangeCallParam {
  constructor(
    public stockexchange: Stockexchange | null,
    public hasSecurity: boolean,
    public countriesAsHtmlOptions: ValueKeyHtmlSelectOptions[],
    public stockexchangeMics: StockexchangeMic[],
    public existingMic: Set<string>,
    public proposeChange = false
  ) {}
}
