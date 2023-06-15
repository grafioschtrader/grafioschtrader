import {TenantBaseId} from '../../entities/tenant.base.id';
import {AlgoStrategy} from './algo.strategy';
import {Exclude, Type} from 'class-transformer';


export abstract class AlgoTopAssetSecurity extends TenantBaseId {

  public idAlgoAssetclassSecurity: number = null;
  public percentage: number = null;

  @Type(() => AlgoStrategy)
  public algoStrategyList: AlgoStrategy[];

  @Exclude()
  get idTree(): string {
    return 'l' + this.idAlgoAssetclassSecurity;
  }

  @Exclude()
  public getId(): number {
    return this.idAlgoAssetclassSecurity;
  }

  public abstract getChildList(): AlgoTopAssetSecurity[];

}
