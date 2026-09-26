import { AlgoTopAssetSecurity } from './algo.top.asset.security';
import { AlgoAssetclass } from './algo.assetclass';
import { Exclude, Type } from 'class-transformer';
import { AlgoTreeName } from '../../entities/view/algo.tree.name';

export class AlgoTop extends AlgoTopAssetSecurity implements AlgoTreeName {
  name: string = null;
  @Type(() => AlgoAssetclass)
  algoAssetclassList: AlgoAssetclass[];
  idWatchlist: number = null;
  referenceDate: Date;
  addedPercentage: number;
  /** Derived by the backend when the strategy is read; absent where the endpoint does not evaluate it. */
  readiness?: AlgoTopReadiness;

  @Exclude()
  getNameByLanguage(language: string): string {
    return this.name;
  }

  @Exclude()
  getChildList(): AlgoTopAssetSecurity[] {
    return this.algoAssetclassList;
  }
}

/**
 * Whether a strategy can be used as it stands. Mirrors the backend record AlgoTopReadiness, which derives it from the
 * hierarchy on every read.
 */
export interface AlgoTopReadiness {
  /** A simulation environment can be created and replayed. */
  readyForReplay: boolean;
  /** The rebalancing comparison can be computed; requires readyForReplay and a portfolio rebalance. */
  readyForRebalancing: boolean;
  /** All findings, blocking ones first. */
  issues: AlgoTopReadinessIssue[];
}

/** One finding of the readiness check. */
export interface AlgoTopReadinessIssue {
  /** Message key of the finding. */
  code: string;
  /** Hierarchy node the finding belongs to. */
  idNode: number;
  /** Property path of the node that the tree highlights, if any. */
  field: string;
  /** The finding in the language of the user. */
  message: string;
  /** True when the finding makes a replay or the rebalancing fail. */
  blocking: boolean;
}

export enum AlgoLevelType {
  TOP_LEVEL = 'T',
  ASSET_CLASS_LEVEL = 'A',
  SECURITY_LEVEL = 'S'
}
