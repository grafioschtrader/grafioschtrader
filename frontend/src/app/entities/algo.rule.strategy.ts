import {BuySell} from '../shared/types/buy.sell';
import {TenantBaseId} from './tenant.base.id';
import {AlgoRuleStrategyParam} from './algo.rule.strategy.param';
import {Exclude} from 'class-transformer';

export abstract class AlgoRuleStrategy extends TenantBaseId {
  idAlgoRuleStrategy: number;
  idAlgoAssetclassSecurity: number;

  algoRuleStrategyParamMap: Map<string, AlgoRuleStrategyParam> | { [key: string]: AlgoRuleStrategyParam };

  @Exclude()
  get idTree(): string {
    return 'rs' + this.idAlgoRuleStrategy;
  }

  @Exclude()
  public getId(): number {
    return this.idAlgoRuleStrategy;
  }
}
