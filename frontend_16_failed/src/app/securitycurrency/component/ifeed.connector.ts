export interface IFeedConnector {
  id: string;
  domain: string;
  readableName: string;
  // securitycurrencyFeedSupport: Map<FeedSupport, FeedIdentifier[]>;
  securitycurrencyFeedSupport: SecuritycurrencyFeedSupport[];
  intraDescription: string;
  description: Description;
}

export class Description {
  historicalDescription: string;
  intraDescription: string;
}

export enum FeedSupport {
  HISTORY,
  INTRA,
  DIVIDEND,
  SPLIT
}

export interface SecuritycurrencyFeedSupport {
  [key: string]: FeedIdentifier[];
}


export enum FeedIdentifier {
  CURRENCY,
  CURRENCY_URL,
  SECURITY,
  SECURITY_URL,
  DIVIDEND,
  DIVIDEND_URL,
  SPLIT,
  SPLIT_URL
}

