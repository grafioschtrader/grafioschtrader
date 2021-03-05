import {AlgoTop} from '../algo.top';
import {RuleStrategy} from '../../shared/types/rule.strategy';

export class AlgoTopCreate extends AlgoTop {

  constructor(ruleStrategy: RuleStrategy) {
    super();
    this.ruleStrategy = RuleStrategy[ruleStrategy];
  }

  assetclassPercentageList: AssetclassPercentage[] = [];
}

export class AssetclassPercentage {
  constructor(public idAssetclass: number, public percentage: number) {
  }
}
