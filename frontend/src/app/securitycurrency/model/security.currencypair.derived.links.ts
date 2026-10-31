import type { Currencypair } from '../../entities/currencypair';
import type { Security } from '../../entities/security';
import type { SecurityDerivedLink } from '../../entities/security.derived.link';
import { CurrencypairWatchlist } from '../../entities/view/currencypair.watchlist';

/** Instruments and links returned for a derived security. */
export interface SecurityCurrencypairDerivedLinks {
  securityDerivedLinks: SecurityDerivedLink[];
  securities: Security[];
  currencypairs: Currencypair[];
}

export class SecurityDerivedLinksHelper {
  public static readonly ADDITIONAL_INSTRUMENT_NAME = 'additionalInstrumentName';
  public static readonly ALLOWED_VAR_NAMES = 'opqrs';
  public static readonly VAR_NAME_REGEX = new RegExp(
    '(?:^|[^A-Za-z0-9])([' +
      SecurityDerivedLinksHelper.ALLOWED_VAR_NAMES[0] +
      '-' +
      SecurityDerivedLinksHelper.ALLOWED_VAR_NAMES[SecurityDerivedLinksHelper.ALLOWED_VAR_NAMES.length - 1] +
      '])(?![A-Za-z0-9])',
    'g'
  );

  public static getBaseInstrument(
    scdl: SecurityCurrencypairDerivedLinks,
    idLinkSecuritycurrency: number
  ): Security | CurrencypairWatchlist | undefined {
    let baseInstrument: Security | CurrencypairWatchlist | undefined = scdl.securities.find(
      (s) => s.idSecuritycurrency === idLinkSecuritycurrency
    );
    if (!baseInstrument) {
      const currencypair = scdl.currencypairs.find((c) => c.idSecuritycurrency === idLinkSecuritycurrency);
      baseInstrument = currencypair ? SecurityDerivedLinksHelper.createCurrencypairWatchlist(currencypair) : undefined;
    }
    return baseInstrument;
  }

  public static createCurrencypairWatchlist(currencypair: Currencypair): CurrencypairWatchlist {
    const currencypairWatchlist = new CurrencypairWatchlist(currencypair.fromCurrency, currencypair.toCurrency);
    Object.assign(currencypairWatchlist, currencypair);
    return currencypairWatchlist;
  }

  public static getAdditionalInstrumentsForExistingSecurity(scdl: SecurityCurrencypairDerivedLinks): {
    [name: string]: Security | CurrencypairWatchlist;
  } {
    const additionalInstruments: { [fieldName: string]: Security | CurrencypairWatchlist } = {};
    scdl.securityDerivedLinks.forEach((securityDerivedLink) => {
      const fieldName = SecurityDerivedLinksHelper.ADDITIONAL_INSTRUMENT_NAME + '_' + securityDerivedLink.varName;
      const security = scdl.securities.find((s) => s.idSecuritycurrency === securityDerivedLink.idLinkSecuritycurrency);
      if (security) {
        additionalInstruments[fieldName] = security;
      } else {
        const currencypair = scdl.currencypairs.find(
          (c) => c.idSecuritycurrency === securityDerivedLink.idLinkSecuritycurrency
        );
        if (currencypair)
          additionalInstruments[fieldName] = SecurityDerivedLinksHelper.createCurrencypairWatchlist(currencypair);
      }
    });
    return additionalInstruments;
  }
}
