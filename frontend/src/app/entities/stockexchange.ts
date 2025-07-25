import {BaseID} from '../lib/entities/base.id';
import {Auditable} from '../lib/entities/auditable';
import {Exclude} from 'class-transformer';

export class Stockexchange extends Auditable implements BaseID {
  idStockexchange?: number = null;
  mic: string = null;
  name: string = null;
  countryCode: string = null;
  timeOpen: string = null;
  timeClose: string = null;
  timeZone: string = null;
  secondaryMarket: boolean = null;
  noMarketValue: boolean = null;
  idIndexUpdCalendar: number = null;
  nameIndexUpdCalendar: string = null;
  lastDirectPriceUpdate: number;
  website: string = null;
  @Exclude()
  public override getId(): number {
    return this.idStockexchange;
  }

}
