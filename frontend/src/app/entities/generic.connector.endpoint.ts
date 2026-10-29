import { GenericConnectorFieldMapping } from './generic.connector.field.mapping';

export class GenericConnectorEndpoint {
  idEndpoint: number | null = null;
  feedSupport: string | null = null;
  instrumentType: string | null = null;
  urlTemplate: string | null = null;
  httpMethod: string | null = null;
  responseFormat: string | null = null;
  numberFormat: string | null = null;
  dateFormatType: string | null = null;
  dateFormatPattern: string | null = null;
  jsonDataStructure: string | null = null;
  jsonDataPath: string | null = null;
  jsonColumnNamesPath: string | null = null;
  jsonStatusPath: string | null = null;
  jsonStatusOkValue: string | null = null;
  csvDelimiter: string | null = null;
  csvSkipHeaderLines: number | null = null;
  htmlCssSelector: string | null = null;
  htmlExtractMode: string | null = null;
  htmlTextCleanup: string | null = null;
  htmlExtractRegex: string | null = null;
  htmlSplitDelimiter: string | null = null;
  tickerBuildStrategy: string | null = null;
  currencyPairSeparator: string | null = null;
  currencyPairSuffix: string | null = null;
  tickerUppercase: boolean = false;
  maxDataPoints: number | null = null;
  paginationEnabled: boolean = false;
  everUsedSuccessfully: boolean = false;
  endpointOptions: string[] = [];
  fieldMappings: GenericConnectorFieldMapping[] = [];
}
