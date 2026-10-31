import {
  MobileCurrencypairPosition,
  MobileSecurityPosition,
  MobileWatchlistGroup
} from '../watchlist/mobile.watchlist.rows';
import { detailEntry, detailSection, MobileDetailSection } from '../shared/mobile.detail';

/** An instrument of a watchlist, told apart by its kind. */
export type MobileWatchlistInstrument =
  | { kind: 'security'; position: MobileSecurityPosition }
  | { kind: 'currencypair'; position: MobileCurrencypairPosition };

/**
 * Finds an instrument in the content of a watchlist.
 *
 * @param group Content of the watchlist
 * @param idSecuritycurrency Id of the security or currency pair
 * @returns The instrument, or null when the watchlist no longer contains it
 */
export function findMobileWatchlistInstrument(
  group: MobileWatchlistGroup,
  idSecuritycurrency: number
): MobileWatchlistInstrument | null {
  const security = (group.securityPositionList ?? []).find(
    (p) => p.securitycurrency.idSecuritycurrency === idSecuritycurrency
  );
  if (security) {
    return { kind: 'security', position: security };
  }
  const currencypair = (group.currencypairPositionList ?? []).find(
    (p) => p.securitycurrency.idSecuritycurrency === idSecuritycurrency
  );
  return currencypair ? { kind: 'currencypair', position: currencypair } : null;
}

/** Name shown in the title of the instrument page. */
export function mobileInstrumentName(instrument: MobileWatchlistInstrument): string {
  return instrument.kind === 'security'
    ? (instrument.position.securitycurrency.name ?? '')
    : `${instrument.position.securitycurrency.fromCurrency}/${instrument.position.securitycurrency.toCurrency}`;
}

/** Currency the prices and amounts of the instrument are in. */
export function mobileInstrumentCurrency(instrument: MobileWatchlistInstrument): string {
  return instrument.kind === 'security'
    ? (instrument.position.securitycurrency.currency ?? '')
    : instrument.position.securitycurrency.toCurrency;
}

/**
 * The details of an instrument as the phone shows them: the held position, the quotation data and the base data. It is
 * the reader's part of the extended row of the desktop watchlist; the settings of the price data connectors are left
 * to the desktop. Lines without a value are omitted, and so is a section without lines.
 *
 * @param instrument Instrument of the watchlist
 * @param language Language of the user, for the sub asset class
 * @returns The sections in display order
 */
export function mobileInstrumentSections(
  instrument: MobileWatchlistInstrument,
  language: string
): MobileDetailSection[] {
  const sections =
    instrument.kind === 'security'
      ? [
          holdingSection(instrument.position),
          quotationSection(instrument),
          securityBaseSection(instrument.position, language)
        ]
      : [quotationSection(instrument), currencypairBaseSection(instrument.position)];
  return sections.filter((section) => section.entries.length > 0);
}

/** Only an instrument held with units other than zero has a position, as in the watchlist. */
function holdingSection(position: MobileSecurityPosition): MobileDetailSection {
  if (!position.units) {
    return detailSection('HOLDINGS', []);
  }
  const currency = position.securitycurrency.currency ?? '';
  return detailSection('HOLDINGS', [
    detailEntry('QUANTITY', position.units, 'units'),
    detailEntry('VALUE_SECURITY', position.valueSecurity, 'amount', { currency }),
    detailEntry('GAIN', position.positionGainLoss, 'amount', { currency, signed: true }),
    detailEntry('GAIN_PERCENTAGE', position.positionGainLossPercentage, 'percentage', { signed: true })
  ]);
}

function quotationSection(instrument: MobileWatchlistInstrument): MobileDetailSection {
  const position = instrument.position;
  const sc = position.securitycurrency;
  const fractionDigits = sc.priceFractionDigits ?? null;
  return detailSection('QUOTATION_DATA', [
    detailEntry('TIMEDATE', sc.sTimestamp, 'dateTime'),
    detailEntry('LAST', sc.sLast, 'price', { fractionDigits }),
    detailEntry('DAILY_CHANGE', sc.sChangePercentage, 'percentage', { signed: true }),
    detailEntry('DAY_BEFORE_CLOSE', sc.sPrevClose, 'price', { fractionDigits }),
    detailEntry('HIGH', sc.sHigh, 'price', { fractionDigits }),
    detailEntry('LOW', sc.sLow, 'price', { fractionDigits }),
    detailEntry('VOLUME', sc.sVolume, 'integer'),
    detailEntry('MOBILE_CHANGE_YTD', position.ytdChangePercentage, 'percentage', { signed: true }),
    detailEntry('MOBILE_CHANGE_ONE_YEAR', position.timeFrameChangePercentage, 'percentage', { signed: true })
  ]);
}

function securityBaseSection(position: MobileSecurityPosition, language: string): MobileDetailSection {
  const security = position.securitycurrency;
  const assetClass = security.assetClass;
  // A derived instrument has no ISIN or ticker of its own; the desktop omits both as well.
  const own = !security.idLinkSecuritycurrency;
  return detailSection('BASE_DATA', [
    detailEntry('ASSETCLASS', assetClass?.categoryType, 'translate'),
    detailEntry('FINANCIAL_INSTRUMENT', assetClass?.specialInvestmentInstrument, 'translate'),
    detailEntry('SUB_ASSETCLASS', assetClass?.subCategoryNLS?.map?.[language], 'text'),
    detailEntry('STOCKEXCHANGE', security.stockexchange?.name, 'text'),
    detailEntry('CURRENCY', security.currency, 'text'),
    own ? detailEntry('ISIN', security.isin, 'text') : null,
    own ? detailEntry('TICKER_SYMBOL', security.tickerSymbol, 'text') : null,
    detailEntry('ACTIVE_FROM_DATE', security.activeFromDate, 'date'),
    detailEntry('ACTIVE_TO_DATE', security.activeToDate, 'date'),
    detailEntry('DISTRIBUTION_FREQUENCY', security.distributionFrequency, 'translate'),
    // A factor of 1 is the plain instrument; the desktop leaves it blank as well.
    security.leverageFactor === 1 ? null : detailEntry('LEVERAGE_FACTOR', security.leverageFactor, 'units'),
    detailEntry('NOTE', security.note, 'text')
  ]);
}

function currencypairBaseSection(position: MobileCurrencypairPosition): MobileDetailSection {
  const pair = position.securitycurrency;
  return detailSection('BASE_DATA', [
    detailEntry('CURRENCY_FROM', pair.fromCurrency, 'text'),
    detailEntry('CURRENCY_TO', pair.toCurrency, 'text'),
    detailEntry('NOTE', pair.note, 'text')
  ]);
}
