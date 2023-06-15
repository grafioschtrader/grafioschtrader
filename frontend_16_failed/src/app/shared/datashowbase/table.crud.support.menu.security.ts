import {CrudMenuOptions, TableCrudSupportMenu} from './table.crud.support.menu';
import {Directive} from '@angular/core';
import {DeleteService} from './delete.service';
import {ConfirmationService, FilterService} from 'primeng/api';
import {MessageToastService} from '../message/message.toast.service';
import {ActivePanelService} from '../mainmenubar/service/active.panel.service';
import {DialogService} from 'primeng/dynamicdialog';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../service/globalparameter.service';
import {UserSettingsService} from '../service/user.settings.service';
import {BaseID} from '../../entities/base.id';

/**
 * For example, a security has an asset class, this can only be deleted if no security refers to it.
 * Therefore, tables that manage such dependent entities should be derived from this class.
 */
@Directive()
export abstract class TableCrudSupportMenuSecurity<T extends BaseID> extends TableCrudSupportMenu<T> {

  hasSecurityObject: { [key: number]: number } = {};

  constructor(entityName: string,
              deleteService: DeleteService,
              confirmationService: ConfirmationService,
              messageToastService: MessageToastService,
              activePanelService: ActivePanelService,
              dialogService: DialogService,
              filterService: FilterService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              usersettingsService: UserSettingsService,
              crudMenuOptions: CrudMenuOptions[] = TableCrudSupportMenu.ALLOW_ALL_CRUD_OPERATIONS) {
    super(entityName, deleteService, confirmationService, messageToastService,
      activePanelService, dialogService, filterService, translateService, gps, usersettingsService, crudMenuOptions);
  }

  protected override hasRightsForDeleteEntity(entity: T): boolean {
    return Object.keys(this.hasSecurityObject).length > 0 && this.hasSecurityObject[this.getId(entity)] === 0
      && super.hasRightsForDeleteEntity(entity);
  }
}
