import { TenantBaseId } from '../lib/entities/tenant.base.id';
import { Exclude } from 'class-transformer';

export class Watchlist extends TenantBaseId {
  idWatchlist?: number;
  name?: string | null = null;

  @Exclude()
  public getId(): number | undefined {
    return this.idWatchlist;
  }
}
