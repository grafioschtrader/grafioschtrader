import { TenantBaseId } from '../../lib/entities/tenant.base.id';
import { AlgoStrategy } from './algo.strategy';
import { Exclude, Type } from 'class-transformer';

export abstract class AlgoTopAssetSecurity extends TenantBaseId {
  public idAlgoAssetclassSecurity: number | null = null;
  public percentage: number | null = null;

  @Type(() => AlgoStrategy)
  public algoStrategyList: AlgoStrategy[];

  @Exclude()
  get idTree(): string {
    return 'l' + this.idAlgoAssetclassSecurity;
  }

  @Exclude()
  public getId(): number | null {
    return this.idAlgoAssetclassSecurity;
  }

  public abstract getChildList(): AlgoTopAssetSecurity[] | null;
}

/** Requires the persisted parent of an editor or simulation dialog. */
export function requireAlgoNodeId(node: AlgoTopAssetSecurity): number {
  const id = node.idAlgoAssetclassSecurity;
  if (id == null) throw new Error('This Algo operation requires a persisted hierarchy node.');
  return id;
}
