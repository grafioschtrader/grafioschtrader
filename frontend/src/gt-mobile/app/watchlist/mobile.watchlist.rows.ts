/**
 * The fields of the watchlist report (backend SecuritycurrencyGroup / SecuritycurrencyPosition) the mobile application
 * reads. They are declared here rather than taken from the desktop entity classes, which are not yet written for
 * strict null checks.
 */
export interface MobileWatchlistGroup {
  securityPositionList: MobileSecurityPosition[] | null;
  currencypairPositionList: MobileCurrencypairPosition[] | null;
}

interface MobilePriceFields {
  idSecuritycurrency: number;
  sLast?: number | null;
  sChangePercentage?: number | null;
  priceFractionDigits?: number | null;
}

export interface MobileSecurityPosition {
  securitycurrency: MobilePriceFields & {
    name: string | null;
    currency: string | null;
    assetClass?: { categoryType?: string | null; specialInvestmentInstrument?: string | null } | null;
  };
  units?: number | null;
  positionGainLossPercentage?: number | null;
  valueSecurity?: number | null;
}

export interface MobileCurrencypairPosition {
  securitycurrency: MobilePriceFields & { fromCurrency: string; toCurrency: string };
}

/** A watchlist as the tenant list returns it. */
export interface MobileWatchlist {
  idWatchlist: number;
  name: string;
}

/** The figures of a held position, in the currency of the instrument. */
export interface MobileWatchlistHolding {
  units: number;
  gainLossPercentage: number | null;
  value: number | null;
}

/** One line of the mobile watchlist. */
export interface MobileWatchlistRow {
  idSecuritycurrency: number;
  name: string;
  /** Translation keys of the instrument kind, shown joined, e.g. asset class and financial instrument. */
  kindKeys: string[];
  currency: string;
  /** Last price with its own number of fraction digits. */
  price: { value: number | null; priceFractionDigits: number | null };
  dailyChangePercentage: number | null;
  /** Present only when the tenant holds the instrument. */
  holding: MobileWatchlistHolding | null;
}

/**
 * Turns the content of a watchlist into the lines of the mobile list: the securities in the order of the server, then
 * the currency pairs. A security is held when it has units other than zero.
 *
 * @param group Content of the watchlist
 * @returns The lines in display order
 */
export function mobileWatchlistRows(group: MobileWatchlistGroup): MobileWatchlistRow[] {
  return [
    ...(group.securityPositionList ?? []).map(securityRow),
    ...(group.currencypairPositionList ?? []).map(currencypairRow)
  ];
}

function securityRow(position: MobileSecurityPosition): MobileWatchlistRow {
  const security = position.securitycurrency;
  const assetClass = security.assetClass;
  return {
    idSecuritycurrency: security.idSecuritycurrency,
    name: security.name ?? '',
    kindKeys: [assetClass?.categoryType, assetClass?.specialInvestmentInstrument].filter(
      (key): key is string => typeof key === 'string' && key.length > 0
    ),
    currency: security.currency ?? '',
    price: { value: security.sLast ?? null, priceFractionDigits: security.priceFractionDigits ?? null },
    dailyChangePercentage: security.sChangePercentage ?? null,
    holding: position.units
      ? {
          units: position.units,
          gainLossPercentage: position.positionGainLossPercentage ?? null,
          value: position.valueSecurity ?? null
        }
      : null
  };
}

function currencypairRow(position: MobileCurrencypairPosition): MobileWatchlistRow {
  const pair = position.securitycurrency;
  return {
    idSecuritycurrency: pair.idSecuritycurrency,
    name: `${pair.fromCurrency}/${pair.toCurrency}`,
    kindKeys: ['CURRENCY_PAIR'],
    currency: pair.toCurrency,
    price: { value: pair.sLast ?? null, priceFractionDigits: pair.priceFractionDigits ?? null },
    dailyChangePercentage: pair.sChangePercentage ?? null,
    holding: null
  };
}
