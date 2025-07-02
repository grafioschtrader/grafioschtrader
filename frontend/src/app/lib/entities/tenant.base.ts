import {TenantBaseId} from './tenant.base.id';

export abstract class TenantBase extends TenantBaseId {
  tenantName: string = null;
  createIdUser: number;
}
