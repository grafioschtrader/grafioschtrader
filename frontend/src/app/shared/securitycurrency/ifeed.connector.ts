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
  FS_HISTORY,
  FS_INTRA,
  FS_DIVIDEND,
  FS_SPLIT
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

