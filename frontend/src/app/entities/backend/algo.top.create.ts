import {AlgoTop} from '../../algo/model/algo.top';
import {RuleStrategyType} from '../../shared/types/rule.strategy.type';

export class AlgoTopCreate extends AlgoTop {

  assetclassPercentageList: AssetclassPercentage[] = [];

  constructor(ruleStrategy: RuleStrategyType) {
    super();
    this.ruleStrategy = RuleStrategyType[ruleStrategy];
  }
}

export class AssetclassPercentage {
  constructor(public idAssetclass: number, public percentage: number) {
  }
}
