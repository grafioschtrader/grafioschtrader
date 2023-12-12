import {Currencypair} from '../currencypair';
import {Assetclass} from '../assetclass';
import {AssetclassType} from '../../shared/types/assetclass.type';

export class CurrencypairWatchlist extends Currencypair {

  assetClass: Assetclass;

  public constructor(fromCurrency: string, toCurrency: string) {
    super(fromCurrency, toCurrency);
    this.assetClass = new Assetclass();
    this.assetClass.categoryType = AssetclassType[AssetclassType.CURRENCY_PAIR];
  }

  /*
    public get name(): string {
      return this.toStringFN();
    }
  */
}
