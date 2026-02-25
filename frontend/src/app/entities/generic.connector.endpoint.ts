import {GenericConnectorFieldMapping} from './generic.connector.field.mapping';

export class GenericConnectorEndpoint {
  idEndpoint: number;
  feedSupport: string;
  instrumentType: string;
  urlTemplate: string;
  httpMethod: string;
  responseFormat: string;
  numberFormat: string;
  dateFormatType: string;
  dateFormatPattern: string;
  jsonDataStructure: string;
  jsonDataPath: string;
  jsonStatusPath: string;
  jsonStatusOkValue: string;
  csvDelimiter: string;
  csvSkipHeaderLines: number;
  htmlCssSelector: string;
  htmlExtractMode: string;
  htmlTextCleanup: string;
  htmlExtractRegex: string;
  htmlSplitDelimiter: string;
  tickerBuildStrategy: string;
  currencyPairSeparator: string;
  currencyPairSuffix: string;
  tickerUppercase: boolean;
  maxDataPoints: number;
  paginationEnabled: boolean;
  endpointOptions: string[] = [];
  fieldMappings: GenericConnectorFieldMapping[] = [];
}
