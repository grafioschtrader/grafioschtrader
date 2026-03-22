import {Auditable} from '../lib/entities/auditable';
import {BaseID} from '../lib/entities/base.id';
import {MultilanguageString} from '../lib/entities/multilanguage.string';
import {GenericConnectorEndpoint} from './generic.connector.endpoint';
import {GenericConnectorHttpHeader} from './generic.connector.http.header';

export class GenericConnectorDef extends Auditable implements BaseID {
  idGenericConnector?: number = null;
  shortId: string = null;
  readableName: string = null;
  domainUrl: string = null;
  needsApiKey = false;
  rateLimitType: string = null;
  rateLimitRequests: number = null;
  rateLimitPeriodSec: number = null;
  rateLimitConcurrent: number = null;
  intradayDelaySeconds: number = null;
  regexUrlPattern: string = null;
  supportsSecurity = true;
  supportsCurrency = false;
  needHistoryGapFiller = false;
  gbxDividerEnabled = false;
  supportedCategories: string = null;
  geoRestrictions: string = null;
  tokenConfigYaml: string = null;
  descriptionNLS: MultilanguageString = new MultilanguageString();
  activated = false;
  instrumentCount = 0;
  endpoints: GenericConnectorEndpoint[] = [];
  httpHeaders: GenericConnectorHttpHeader[] = [];

  public override getId(): number {
    return this.idGenericConnector;
  }
}
