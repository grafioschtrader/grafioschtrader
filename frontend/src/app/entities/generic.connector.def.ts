import { Auditable } from '../lib/entities/auditable';
import { BaseID } from '../lib/entities/base.id';
import { MultilanguageString } from '../lib/entities/multilanguage.string';
import { GenericConnectorEndpoint } from './generic.connector.endpoint';
import { GenericConnectorHttpHeader } from './generic.connector.http.header';

export class GenericConnectorDef extends Auditable implements BaseID {
  idGenericConnector?: number | null = null;
  shortId: string | null = null;
  readableName: string | null = null;
  domainUrl: string | null = null;
  needsApiKey = false;
  rateLimitType: string | null = null;
  rateLimitRequests: number | null = null;
  rateLimitPeriodSec: number | null = null;
  rateLimitConcurrent: number | null = null;
  intradayDelaySeconds: number | null = null;
  regexUrlPattern: string | null = null;
  supportsSecurity = true;
  supportsCurrency = false;
  needHistoryGapFiller = false;
  gbxDividerEnabled = false;
  supportedCategories: string | null = null;
  geoRestrictions: string | null = null;
  tokenConfigYaml: string | null = null;
  descriptionNLS: MultilanguageString = new MultilanguageString();
  activated = false;
  instrumentCount = 0;
  endpoints: GenericConnectorEndpoint[] = [];
  httpHeaders: GenericConnectorHttpHeader[] = [];

  public override getId(): number | null | undefined {
    return this.idGenericConnector;
  }
}
