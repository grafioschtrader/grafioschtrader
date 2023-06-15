import {TenantBaseId} from '../../entities/tenant.base.id';
import {BaseParam} from '../../entities/view/base.param';
import {Exclude} from 'class-transformer';

export abstract class AlgoRuleStrategy extends TenantBaseId {
  idAlgoRuleStrategy: number;
  idAlgoAssetclassSecurity: number;

  algoRuleStrategyParamMap: Map<string, BaseParam> | { [key: string]: BaseParam };

  @Exclude()
  get idTree(): string {
    return 'rs' + this.idAlgoRuleStrategy;
  }

  @Exclude()
  public getId(): number {
    return this.idAlgoRuleStrategy;
  }
}
