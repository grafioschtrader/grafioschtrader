import { BaseID } from '../lib/entities/base.id';
import { Auditable } from '../lib/entities/auditable';
import { Exclude } from 'class-transformer';

export class Stockexchange extends Auditable implements BaseID {
  idStockexchange?: number | null = null;
  mic: string | null = null;
  name: string | null = null;
  countryCode: string | null = null;
  timeOpen: string | null = null;
  timeClose: string | null = null;
  timeZone: string | null = null;
  secondaryMarket: boolean | null = null;
  noMarketValue: boolean | null = null;
  idIndexUpdCalendar: number | null = null;
  nameIndexUpdCalendar: string | null = null;
  /** Rule set producing the trading calendar. Mutually exclusive with idIndexUpdCalendar. */
  idTradingCalendarRuleSet: number | null = null;
  nameTradingCalendarRuleSet: string | null = null;
  lastDirectPriceUpdate?: number | null;
  website: string | null = null;
  @Exclude()
  public override getId(): number | null | undefined {
    return this.idStockexchange;
  }
}
