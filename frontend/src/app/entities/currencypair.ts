import {Securitycurrency} from './securitycurrency';
import {BaseID} from './base.id';


export class Currencypair extends Securitycurrency implements BaseID {

  public fromCurrency: string;
  public toCurrency: string;
  public isCryptocurrency?: boolean;

  // public toStringFN = (): string => this.fromCurrency + '/' + this.toCurrency;

  public constructor(fromCurrency?: string, toCurrency?: string) {
    super();
    this.fromCurrency = fromCurrency;
    this.toCurrency = toCurrency;
  }

  public getNewInstance(): Currencypair {
    return new Currencypair();
  }

}
