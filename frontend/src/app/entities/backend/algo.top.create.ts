import {AlgoTop} from '../../algo/model/algo.top';
import {RuleStrategy} from '../../shared/types/rule.strategy';

export class AlgoTopCreate extends AlgoTop {

  assetclassPercentageList: AssetclassPercentage[] = [];

  constructor(ruleStrategy: RuleStrategy) {
    super();
    this.ruleStrategy = RuleStrategy[ruleStrategy];
  }
}

export class AssetclassPercentage {
  constructor(public idAssetclass: number, public percentage: number) {
  }
}
