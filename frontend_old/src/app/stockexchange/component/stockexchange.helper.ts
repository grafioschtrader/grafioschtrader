import {ValueKeyHtmlSelectOptions} from '../../dynamic-form/models/value.key.html.select.options';

export class StockexchangeHelper {

  public static transform(vkhso: ValueKeyHtmlSelectOptions[]): { [cc: string]: string } {
    const countriesAsKeyValue: { [cc: string]: string }  = {};
    vkhso.forEach(o => {
      countriesAsKeyValue[o.key] = o.value;
    });
    return countriesAsKeyValue;
  }
}
