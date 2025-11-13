import {Portfolio} from '../../../entities/portfolio';
import {Tenant} from '../../../entities/tenant';
import {Cashaccount} from '../../../entities/cashaccount';
import {Securityaccount} from '../../../entities/securityaccount';
import {Watchlist} from '../../../entities/watchlist';
import {ImportTransactionHead} from '../../../entities/import.transaction.head';
import {ImportTransactionPlatform} from '../../../entities/import.transaction.platform';
import {ImportTransactionTemplate} from '../../../entities/import.transaction.template';
import {AlgoTop} from '../../../algo/model/algo.top';
import {AlgoAssetclass} from '../../../algo/model/algo.assetclass';
import {AlgoSecurity} from '../../../algo/model/algo.security';
import {AlgoTopCreate} from '../../../entities/backend/algo.top.create';
import {CorrelationSet} from '../../../entities/correlation.set';


export class CallParam {
  constructor(public parentObject: Tenant | Portfolio | Securityaccount | ImportTransactionPlatform | AlgoTop | AlgoAssetclass,
              public thisObject: Tenant | Portfolio | Cashaccount | Securityaccount | Watchlist | ImportTransactionHead
                | CorrelationSet | ImportTransactionPlatform | ImportTransactionTemplate | AlgoAssetclass | AlgoSecurity
                | AlgoTopCreate,
              public optParam?: { [key: string]: any }) {
  }
}

