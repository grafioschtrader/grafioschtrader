import type { IHistoryquoteQuality } from '../../entities/view/ihistoryquote.quality';

export interface HistoryquoteQualityIds {
  idConnectorHistory: string | null;
  idStockexchange: number | null;
  categoryType: number | null;
  specialInvestmentInstrument: number | null;
  uniqueKey?: number;
}

/** Selects exactly the query fields from a displayed group, retaining valid zero-valued dimensions. */
export function pickHistoryquoteQualityIds(source: HistoryquoteQualityIds): HistoryquoteQualityIds {
  const { idConnectorHistory, idStockexchange, categoryType, specialInvestmentInstrument, uniqueKey } = source;
  return { idConnectorHistory, idStockexchange, categoryType, specialInvestmentInstrument, uniqueKey };
}

export interface HistoryquoteQualityGroup extends HistoryquoteQualityIds {
  name: string;
  numberOfSecurities: number;
  activeNowSecurities: number;
  connectorCreated: number;
  manualImported: number;
  filledLinear: number;
  qualityPercentage: number;
  ohlPercentage: number;
  childrendHqg: HistoryquoteQualityGroup[];
}

export interface HistoryquoteQualityHead extends HistoryquoteQualityGroup {
  lastUpdate: string | null;
}

export enum HistoryquoteQualityGrouped {
  STOCKEXCHANGE_GROUPED,
  CONNECTOR_GROUPED
}

export interface IHistoryquoteQualityWithSecurityProp extends IHistoryquoteQuality {
  name: string;
  currency: string;
  activeFromDate: string;
  activeToDate: string;
  idSecurity: number;
  connectorCreated: number;
  filledNoTradeDay: number;
  manualImported: number;
  filledLinear: number;
}

/**
 * Date boundaries the linear gap filling offers for a single instrument. Delivered before the dialog opens, because the
 * date picker must know its range and default value while the form configuration is built.
 */
export interface HistoryquoteFillGapsBounds {
  /** Earliest selectable date, the active from date of the instrument. */
  minFillUpTo: string;
  /** Latest selectable date, the current day. */
  maxFillUpTo: string;
  /** Proposed date, reachable without relying on unknown exchange holidays. */
  defaultFillUpTo: string;
  /** Date up to which the trading calendar of the exchange is derived. */
  calendarHorizon: string;
  /** True when the trading calendar and not the lifetime of the instrument limits the proposed date. */
  calendarOutdated: boolean;
  /** Trading days between the calendar horizon and the last completed trading day. */
  missingTradingDays: number;
}

/**
 * Date boundaries the deletion of linear filled and manually imported prices offers for a single instrument. They are
 * the oldest and the most recent stored price. Deliberately not taken from the data quality figures, whose minDate and
 * maxDate stop at the last completed trading day while prices may be stored beyond it.
 */
export interface HistoryquoteDeleteBounds {
  /** Oldest stored price, the earliest selectable date. */
  minDate: string;
  /** Most recent stored price, the latest selectable date. It may lie in the future. */
  maxDate: string;
}

/** Parameters of the linear gap filling, chosen by the user in the dialog. */
export interface HistoryquoteFillGapsParam {
  moveWeekendToFriday: boolean;
  fillUpToDate: string;
}

export interface HisotryqouteLinearFilledSummary {
  message: string;
  requiredClosing: number;
  gapsTotalFilled: number;
  createdHistoryquotesStart: number;
  createdHistoryquotesEnd: number;
  createdHistoryquotes: number;
  movedWeekendDays: number;
  removedWeekendDays: number;
  warning: boolean;
}

export interface DeleteHistoryquotesSuccess {
  manualImported: number;
  filledLinear: number;
}
