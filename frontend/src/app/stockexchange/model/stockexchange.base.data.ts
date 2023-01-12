import {Stockexchange} from '../../entities/stockexchange';
import {StockexchangeHasSecurity} from './stockexchange.has.security';
import {ValueKeyHtmlSelectOptions} from '../../dynamic-form/models/value.key.html.select.options';

export interface StockexchangeBaseData {
  stockexchanges: Stockexchange[];
  hasSecurity: StockexchangeHasSecurity[];
  stockexchangeMics: StockexchangeMic[];
  countries: ValueKeyHtmlSelectOptions[];
}

export interface StockexchangeMic {
  mic: string;
  name: string;
  countryCode: string;
  city?: string;
  website?: string;
  timeZone?: string;
}

