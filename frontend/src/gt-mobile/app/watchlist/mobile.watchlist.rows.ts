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
  sTimestamp?: string | number | null;
  sLast?: number | null;
  sChangePercentage?: number | null;
  sPrevClose?: number | null;
  sHigh?: number | null;
  sLow?: number | null;
  sVolume?: number | null;
  priceFractionDigits?: number | null;
  note?: string | null;
}

/** Changes over longer periods; only the price update fills them, the stored content leaves them null. */
interface MobilePeriodChanges {
  ytdChangePercentage?: number | null;
  timeFrameChangePercentage?: number | null;
}

export interface MobileSecurity extends MobilePriceFields {
  name: string | null;
  currency: string | null;
  isin?: string | null;
  tickerSymbol?: string | null;
  /** Set for a derived instrument, whose ISIN and ticker belong to its base instrument. */
  idLinkSecuritycurrency?: number | null;
  activeFromDate?: string | null;
  activeToDate?: string | null;
  distributionFrequency?: string | null;
  leverageFactor?: number | null;
  stockexchange?: { name?: string | null } | null;
  assetClass?: {
    categoryType?: string | null;
    specialInvestmentInstrument?: string | null;
    subCategoryNLS?: { map?: Record<string, string> | null } | null;
  } | null;
}

export interface MobileSecurityPosition extends MobilePeriodChanges {
  securitycurrency: MobileSecurity;
  units?: number | null;
  positionGainLoss?: number | null;
  positionGainLossPercentage?: number | null;
  valueSecurity?: number | null;
  /** The tenant has open or closed transactions on this security. */
  watchlistSecurityHasEver?: boolean;
}

export interface MobileCurrencypairPosition extends MobilePeriodChanges {
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

/** Column the mobile watchlist can be ordered by. */
export type MobileWatchlistSortKey = 'name' | 'dailyChange';

/** Order chosen on the watchlist; null keeps the order of the server. */
export interface MobileWatchlistSort {
  key: MobileWatchlistSortKey;
  descending: boolean;
}

/** Names read alphabetically from A, changes from the largest gain, the direction a reader expects first. */
function startsDescending(key: MobileWatchlistSortKey): boolean {
  return key === 'dailyChange';
}

/**
 * The order after a tap on a sort button. The first tap orders by that column in its usual direction, the second
 * reverses it and the third returns to the order of the server.
 *
 * @param current Order shown now, null for the order of the server
 * @param key Column tapped
 * @returns The new order, null for the order of the server
 */
export function nextMobileWatchlistSort(
  current: MobileWatchlistSort | null,
  key: MobileWatchlistSortKey
): MobileWatchlistSort | null {
  if (current?.key !== key) {
    return { key, descending: startsDescending(key) };
  }
  return current.descending === startsDescending(key) ? { key, descending: !current.descending } : null;
}

/**
 * Orders the lines of a watchlist for display. Names compare by the rules of the user's language, so umlauts and
 * numbers sort as a reader expects. A line without a change against the previous day stays at the end in both
 * directions. Lines that compare equal keep the order of the server.
 *
 * @param rows Lines in the order of the server
 * @param sort Order chosen, null for the order of the server
 * @param locale Locale of the user, for comparing names
 * @returns A new array in display order
 */
export function sortMobileWatchlistRows(
  rows: MobileWatchlistRow[],
  sort: MobileWatchlistSort | null,
  locale: string
): MobileWatchlistRow[] {
  if (!sort) {
    return rows;
  }
  const direction = sort.descending ? -1 : 1;
  if (sort.key === 'name') {
    const collator = new Intl.Collator(locale, { sensitivity: 'base', numeric: true });
    return [...rows].sort((a, b) => direction * collator.compare(a.name, b.name));
  }
  return [...rows].sort((a, b) => {
    const changeA = a.dailyChangePercentage;
    const changeB = b.dailyChangePercentage;
    if (changeA == null || changeB == null) {
      return changeA == null ? (changeB == null ? 0 : 1) : -1;
    }
    return direction * (changeA - changeB);
  });
}
