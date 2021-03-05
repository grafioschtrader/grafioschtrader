import {BaseID} from './base.id';
import {Exclude, Expose} from 'class-transformer';

export abstract class TenantBaseId implements BaseID {
  idTenant: number;
  abstract getId(): number;
}
