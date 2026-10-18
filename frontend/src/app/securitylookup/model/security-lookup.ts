import { AssetclassType } from '../../shared/types/assetclass.type';
import { SpecialInvestmentInstruments } from '../../shared/types/special.investment.instruments';

/**
 * One connector proposed for one feed of a looked-up instrument, with the URL extension the connector derived itself
 * and the outcome of a trial request. Corresponds to the backend record ConnectorAlternativeDto.
 */
export interface ConnectorAlternative {
  /** Connector id as stored in the security, e.g. 'gt.datafeed.six' */
  idConnector: string;
  readableName: string;
  /** URL extension derived by the connector; null when it needs none */
  urlExtension: string;
  /** Outcome of the trial request: OK, NO_DATA, REGEX_FAILED, CHECK_FAILED, UNKNOWN_SYMBOL, REQUEST_FAILED or TIMEOUT */
  probeStatus: string;
  /** Date of the sample value as 'yyyy-MM-dd' */
  sampleDate: string;
  sampleValue: number;
  sampleCount: number;
  /** Distribution frequency derived from the delivered dividends; only set for a dividend connector */
  distributionFrequency: string;
  probeDetail: string;
}

/**
 * Something the security lookup could not resolve, why, and what follows for the form. Corresponds to the backend
 * record SecurityLookupHint.
 */
export interface SecurityLookupHint {
  /** ERROR, WARNING or INFO */
  severity: string;
  /** Field of the candidate the hint is about, e.g. 'activeFromDate'; its label is derived from it */
  field: string;
  /** Translation key of the text */
  messageKey: string;
  /** Placeholder values; a name ending in 'Key' is translated, one ending in 'Date' formatted */
  args: { [name: string]: string };
}

/**
 * One listing proposed by the security lookup. Corresponds to the backend record SecurityLookupCandidateDto.
 */
export interface SecurityLookupCandidate {
  providerId: string;
  providerInstrumentId: string;
  providerInstrumentType: string;
  isin: string;
  currency: string;
  mic: string;
  name: string;
  tickerSymbol: string;
  issuerCountry: string;
  activeFromDate: string;
  activeToDate: string;
  distributionFrequency: string;
  denomination: number;
  /** Product page of the issuer, only derived for funds from an issuer template */
  productLink: string;
  /** Page of the listing at the stock exchange, e.g. the SIX detail page */
  stockexchangeLink: string;
  /** Asset class type the provider reports for the listing */
  categoryType: AssetclassType | string;
  /** Financial instrument the provider reports for the listing */
  specialInvestmentInstrument: SpecialInvestmentInstruments | string;
  /** Asset class the user chose before the lookup */
  matchedAssetClassId: number;
  /** True when the type the provider reports contradicts the chosen asset class; the listing is kept */
  instrumentTypeMismatch: boolean;
  /** Outcome of the master data request; the search values remain when it failed */
  baseDataStatus: string;
  baseDataDetail: string;
  /** Further raw values of the provider, shown when the row is expanded */
  providerAttributes: { [key: string]: string };
  historyConnectors: ConnectorAlternative[];
  intraConnectors: ConnectorAlternative[];
  dividendConnectors: ConnectorAlternative[];
  splitConnectors: ConnectorAlternative[];
  /** True when the instrument distributes nothing, so a dividend connector is pointless and none is taken over */
  distributesNothing: boolean;
  /** Every provider that found this listing, the exchange provider first */
  providerIds: string[];
  /** Source of each set field by field name: a provider id or 'derived' for a value a rule produced */
  fieldSources: { [field: string]: string };
  /** Missing fields, a failed master data request, a contradicting type and disagreeing providers of this listing */
  hints: SecurityLookupHint[];
}

/** Outcome of the search at one provider. */
export interface LookupProviderStatus {
  providerId: string;
  status: string;
  detail: string;
}

/** Result of the security lookup by ISIN, currency and exchange. */
export interface SecurityLookupResponse {
  candidates: SecurityLookupCandidate[];
  providerStatus: LookupProviderStatus[];
  /** False when an instrument of the chosen asset class receives no dividends; the dividend table is not shown */
  dividendConnectorApplicable: boolean;
  /** False when an instrument of the chosen asset class has no splits, e.g. a directly held bond */
  splitConnectorApplicable: boolean;
  /** Failed providers and exchanges on which the instrument was found instead; also given without any candidate */
  hints: SecurityLookupHint[];
}

/** The candidate and the connectors the user chose in the lookup dialog; a missing connector leaves the form as is. */
export interface SecurityLookupSelection {
  candidate: SecurityLookupCandidate;
  /** The listing whose name is taken over; another listing than the chosen one may spell the name better */
  nameCandidate: SecurityLookupCandidate;
  history: ConnectorAlternative;
  intra: ConnectorAlternative;
  dividend: ConnectorAlternative;
  split: ConnectorAlternative;
}
