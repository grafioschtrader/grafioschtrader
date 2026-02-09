import {AssetclassType} from '../../shared/types/assetclass.type';
import {SpecialInvestmentInstruments} from '../../shared/types/special.investment.instruments';

/**
 * Request DTO for querying security metadata from GTNet peers.
 * At least one of isin or tickerSymbol must be provided along with currency.
 */
export interface SecurityGtnetLookupRequest {
  isin?: string;
  currency?: string;
  tickerSymbol?: string;
}

/**
 * Capabilities a connector can provide for a security.
 */
export enum ConnectorCapability {
  HISTORY = 'HISTORY',
  INTRADAY = 'INTRADAY',
  DIVIDEND = 'DIVIDEND',
  SPLIT = 'SPLIT'
}

/**
 * Connector hint describing which connector type works for a security.
 * Does not expose API keys or instance-specific configuration.
 */
export interface ConnectorHint {
  connectorFamily: string;
  capabilities: ConnectorCapability[];
  urlExtensionPattern?: string;
  requiresApiKey: boolean;
}

/**
 * DTO for security metadata received from GTNet peers.
 * Contains instance-agnostic data that can be used across different GT installations.
 */
export interface SecurityGtnetLookupDTO {
  // Identification
  isin: string;
  currency: string;
  name: string;
  tickerSymbol?: string;

  // Asset class (enum values, not local IDs)
  categoryType: AssetclassType | string;
  specialInvestmentInstrument: SpecialInvestmentInstruments | string;

  /** Sub-category of asset class in multiple languages (e.g., 'Emerging Markets', 'US Large Cap') */
  subCategoryNLS?: { [language: string]: string };
  /** Detected categorization scheme: REGIONAL (geographical) or SECTOR (industry) */
  subCategoryScheme?: 'REGIONAL' | 'SECTOR' | 'UNKNOWN';

  // Stock exchange (MIC code for cross-instance mapping)
  stockexchangeMic: string;
  stockexchangeName?: string;
  stockexchangeLink?: string;

  // Connector hints (no API keys exposed)
  connectorHints?: ConnectorHint[];

  // Security properties
  denomination?: number;
  distributionFrequency?: string;
  leverageFactor?: number;
  productLink?: string;
  activeFromDate?: Date;
  activeToDate?: Date;

  // Source tracking
  sourceDomain?: string;

  // Matched connector fields (populated by backend after matching against local connectors)
  /** Score indicating how well connectors match local configuration (higher is better) */
  connectorMatchScore?: number;
  /** Matched connector ID for history data */
  matchedHistoryConnector?: string;
  /** URL extension for history connector */
  matchedHistoryUrlExtension?: string;
  /** Matched connector ID for intraday data */
  matchedIntraConnector?: string;
  /** URL extension for intraday connector */
  matchedIntraUrlExtension?: string;
  /** Matched connector ID for dividend data */
  matchedDividendConnector?: string;
  /** URL extension for dividend connector */
  matchedDividendUrlExtension?: string;
  /** Matched connector ID for split data */
  matchedSplitConnector?: string;
  /** URL extension for split connector */
  matchedSplitUrlExtension?: string;

  // Connector retry counters
  retryHistoryLoad?: number;
  retryIntraLoad?: number;
  retryDividendLoad?: number;
  retrySplitLoad?: number;

  // Intraday timestamp
  sTimestamp?: number;

  // History quality data (from historyquote_quality table)
  historyMinDate?: string;
  historyMaxDate?: string;
  ohlPercentage?: number;

  // Dividend and split counts
  dividendCount?: number;
  splitCount?: number;

  /** Matched local asset class ID based on categoryType, specialInvestmentInstrument, and subCategoryNLS */
  matchedAssetClassId?: number;
  /** Asset class match type: EXACT (with subCategoryNLS), PARTIAL (categoryType + specialInvestmentInstrument only), SCHEME_MATCH */
  assetClassMatchType?: 'EXACT' | 'PARTIAL' | 'SCHEME_MATCH';
}

/**
 * Response wrapper for security lookup results from local database and GTNet peers.
 */
export interface SecurityGtnetLookupResponse {
  securities: SecurityGtnetLookupDTO[];
  peersQueried: number;
  peersResponded: number;
  errors?: string[];
}

/**
 * Extended DTO with connector match information for display and selection.
 * Used internally after processing the lookup response.
 */
export interface SecurityGtnetLookupWithMatch extends SecurityGtnetLookupDTO {
  /** Score indicating how well connectors match local configuration (higher is better) */
  connectorMatchScore: number;
  /** Matched connector ID for history data */
  matchedHistoryConnector?: string;
  /** URL extension for history connector */
  matchedHistoryUrlExtension?: string;
  /** Matched connector ID for intraday data */
  matchedIntraConnector?: string;
  /** URL extension for intraday connector */
  matchedIntraUrlExtension?: string;
  /** Matched connector ID for dividend data */
  matchedDividendConnector?: string;
  /** URL extension for dividend connector */
  matchedDividendUrlExtension?: string;
  /** Matched connector ID for split data */
  matchedSplitConnector?: string;
  /** URL extension for split connector */
  matchedSplitUrlExtension?: string;
}
