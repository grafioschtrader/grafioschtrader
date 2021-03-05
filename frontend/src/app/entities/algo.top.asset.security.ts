import {TenantBaseId} from './tenant.base.id';
import {AlgoStrategy} from './algo.strategy';
import {Exclude, Type} from 'class-transformer';
import {AlgoTreeName} from './view/algo.tree.name';


export abstract class AlgoTopAssetSecurity extends TenantBaseId  {

  public idAlgoAssetclassSecurity: number = null;
  public percentage: number = null;

  @Type(() => AlgoStrategy)
  public algoStrategyList: AlgoStrategy[];

  @Exclude()
  public getId(): number {
    return this.idAlgoAssetclassSecurity;
  }

  @Exclude()
  get idTree(): string {
    return 'l' + this.idAlgoAssetclassSecurity;
  }

  public abstract getChildList(): AlgoTopAssetSecurity[];

}
