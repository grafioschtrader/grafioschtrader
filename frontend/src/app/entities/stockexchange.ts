import {BaseID} from './base.id';
import {Auditable} from './auditable';
import {Exclude} from 'class-transformer';

export class Stockexchange extends Auditable implements BaseID {
  idStockexchange?: number = null;
  name: string = null;
  countryCode: string = null;
  symbol: string = null;
  timeClose: string = null;
  timeZone: string = null;
  secondaryMarket: boolean = null;
  noMarketValue: boolean = null;

  @Exclude()
  public getId(): number {
    return this.idStockexchange;
  }

}
