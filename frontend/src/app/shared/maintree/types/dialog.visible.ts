import {Portfolio} from '../../../entities/portfolio';
import {Tenant} from '../../../entities/tenant';
import {Cashaccount} from '../../../entities/cashaccount';
import {Securityaccount} from '../../../entities/securityaccount';
import {ImportTransactionHead} from '../../../entities/import.transaction.head';
import {ImportTransactionPlatform} from '../../../entities/import.transaction.platform';
import {ImportTransactionTemplate} from '../../../entities/import.transaction.template';
import {AlgoTop} from '../../../algo/model/algo.top';
import {AlgoAssetclass} from '../../../algo/model/algo.assetclass';
import {AlgoSecurity} from '../../../algo/model/algo.security';
import {AlgoTopCreate} from '../../../entities/backend/algo.top.create';
import {User} from '../../../entities/user';
import {Globalparameters} from '../../../entities/globalparameters';
import {CorrelationSet} from '../../../entities/correlation.set';
import {ConnectorApiKey} from '../../../entities/connector.api.key';

export enum DialogVisible {
  DvTenant,
  DvPortfolio,
  DvSecurityaccount,
  DvWatchlist,
  DvAlgoRuleStrategy
}

export class CallParam {
  constructor(public parentObject: Tenant | Portfolio | Securityaccount | ImportTransactionPlatform | AlgoTop | AlgoAssetclass | User,
              public thisObject: Tenant | Portfolio | Cashaccount | Securityaccount | ImportTransactionHead | Globalparameters
                | CorrelationSet | ImportTransactionPlatform | ImportTransactionTemplate | AlgoAssetclass | AlgoSecurity
                | AlgoTopCreate | User | ConnectorApiKey,
              public optParam?: { [key: string]: any }) {
  }
}

