import {TenantBaseId} from './tenant.base.id';
import {Exclude} from 'class-transformer';

export class Watchlist extends TenantBaseId {

  idWatchlist?: number;
  name?: string = null;

  @Exclude()
  public getId(): number {
    return this.idWatchlist;
  }
}
