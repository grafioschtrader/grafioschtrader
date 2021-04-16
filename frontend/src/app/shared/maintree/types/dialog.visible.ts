import {Portfolio} from '../../../entities/portfolio';
import {Tenant} from '../../../entities/tenant';
import {Cashaccount} from '../../../entities/cashaccount';
import {Securityaccount} from '../../../entities/securityaccount';
import {ImportTransactionHead} from '../../../entities/import.transaction.head';
import {ImportTransactionPlatform} from '../../../entities/import.transaction.platform';
import {ImportTransactionTemplate} from '../../../entities/import.transaction.template';
import {AlgoTop} from '../../../entities/algo.top';
import {AlgoAssetclass} from '../../../entities/algo.assetclass';
import {AlgoSecurity} from '../../../entities/algo.security';
import {AlgoTopCreate} from '../../../entities/backend/algo.top.create';
import {User} from '../../../entities/user';
import {Globalparameters} from '../../../entities/globalparameters';

export enum DialogVisible {
  Tenant,
  Portfolio,
  Securityaccount,
  Watchlist,
  AlgoRuleStrategy
}


export class CallParam {
  constructor(public parentObject: Tenant | Portfolio | Securityaccount | ImportTransactionPlatform | AlgoTop | AlgoAssetclass | User,
              public thisObject: Tenant | Portfolio | Cashaccount | Securityaccount | ImportTransactionHead | Globalparameters
                | ImportTransactionPlatform | ImportTransactionTemplate | AlgoAssetclass | AlgoSecurity | AlgoTopCreate | User,
              public optParam?: { [key: string]: any }) {
  }
}

