import { CrudMenuOptions, TableCrudSupportMenu } from './table.crud.support.menu';
import { Directive } from '@angular/core';
import { DeleteService } from './delete.service';
import { BaseID } from '../entities/base.id';

/**
 * For example, a security has an asset class, this can only be deleted if no security refers to it.
 * Therefore, tables that manage such dependent entities should be derived from this class.
 */
@Directive()
export abstract class TableCrudSupportMenuSecurity<T extends BaseID> extends TableCrudSupportMenu<T> {
  hasSecurityObject: { [key: number]: number } = {};

  protected constructor(
    entityName: string,
    deleteService: DeleteService,
    crudMenuOptions: CrudMenuOptions[] = TableCrudSupportMenu.ALLOW_ALL_CRUD_OPERATIONS
  ) {
    super(entityName, deleteService, crudMenuOptions);
  }

  protected override hasRightsForDeleteEntity(entity: T): boolean {
    return this.hasSecurityObject[this.getId(entity)] === 0 && super.hasRightsForDeleteEntity(entity);
  }
}
