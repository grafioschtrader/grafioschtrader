import {Currencypair} from '../../entities/currencypair';
import {Security} from '../../entities/security';
import {SecurityDerivedLink} from '../../entities/security.derived.link';
import {CurrencypairWatchlist} from '../../entities/view/currencypair.watchlist';

export class SecurityCurrencypairDerivedLinks {
  public static readonly ADDITIONAL_INSTRUMENT_NAME = 'additionalInstrumentName';
  public static readonly ALLOWED_VAR_NAMES = 'opqrs';
  public static readonly VAR_NAME_REGEX = new RegExp('(?:^|[^A-Za-z0-9])([' + SecurityCurrencypairDerivedLinks.ALLOWED_VAR_NAMES[0] + '-'
    + SecurityCurrencypairDerivedLinks.ALLOWED_VAR_NAMES[SecurityCurrencypairDerivedLinks.ALLOWED_VAR_NAMES.length - 1]
    + '])(?![A-Za-z0-9])', 'g');

  securityDerivedLinks: SecurityDerivedLink[];
  securities: Security[];
  currencypairs: Currencypair[];

  public static getBaseInstrument(scdl: SecurityCurrencypairDerivedLinks, idLinkSecuritycurrency: number):
    Security | CurrencypairWatchlist {
    let baseInstrument: Security | CurrencypairWatchlist = scdl.securities.find(s => s.idSecuritycurrency === idLinkSecuritycurrency);
    if (!baseInstrument) {
      const currencypair = scdl.currencypairs.find(c => c.idSecuritycurrency === idLinkSecuritycurrency);
      baseInstrument = SecurityCurrencypairDerivedLinks.createCurrencypairWatchlist(currencypair);
    }
    return baseInstrument;
  }

  public static createCurrencypairWatchlist(currencypair: Currencypair): CurrencypairWatchlist {
    const currencypairWatchlist = new CurrencypairWatchlist(currencypair.fromCurrency, currencypair.toCurrency);
    Object.assign(currencypairWatchlist, currencypair);
    return currencypairWatchlist;
  }

  public static getAdditionalInstrumentsForExistingSecurity(scdl: SecurityCurrencypairDerivedLinks):
    { [name: string]: Security | CurrencypairWatchlist } {
    const additionalInstruments: { [fieldName: string]: Security | CurrencypairWatchlist } = {};
    scdl.securityDerivedLinks.forEach(securityDerivedLink => {
      const fieldName = SecurityCurrencypairDerivedLinks.ADDITIONAL_INSTRUMENT_NAME + '_' + securityDerivedLink.varName;
      const security = scdl.securities.find(s => s.idSecuritycurrency === securityDerivedLink.idLinkSecuritycurrency);
      if (security) {
        additionalInstruments[fieldName] = security;
      } else {
        const currencypair = scdl.currencypairs.find(c => c.idSecuritycurrency === securityDerivedLink.idLinkSecuritycurrency);
        additionalInstruments[fieldName] = SecurityCurrencypairDerivedLinks.createCurrencypairWatchlist(currencypair);
      }
    });
    return additionalInstruments;
  }

}
