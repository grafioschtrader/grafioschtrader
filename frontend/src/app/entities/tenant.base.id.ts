import {BaseID} from './base.id';

export abstract class TenantBaseId implements BaseID {
  idTenant: number;

  abstract getId(): number;
}
