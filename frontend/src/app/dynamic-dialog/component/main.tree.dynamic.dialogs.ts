import {TranslateService} from '@ngx-translate/core';
import {DialogService, DynamicDialogRef} from 'primeng/dynamicdialog';
import {DynamicDialogHelper} from '../../lib/dynamicdialog/component/dynamicDialogHelper';
import {TenantEditDynamicComponent} from '../../tenant/component/tenant.edit.dynamic.component';
import {Tenant} from '../../entities/tenant';
import {Type} from '@angular/core';
import {CallParam} from '../../shared/maintree/types/dialog.visible';

export class MainTreeDynamicDialogs {

  public static getTenantEditDialogComponent(translateService: TranslateService,
    dialogService: DialogService,
    tenant: Tenant, onlyCurrency: boolean): DynamicDialogRef {
    const dynamicDialogHelper = new DynamicDialogHelper(translateService, dialogService,
      TenantEditDynamicComponent, onlyCurrency ? 'CLIENT_CHANGE_CURRENCY' : 'CLIENT');
    return dynamicDialogHelper.openDynamicDialog(400, {tenant: tenant, onlyCurrency: onlyCurrency});
  }

  public static getEditDialogComponent(componentType: Type<any>, translateService: TranslateService,
    dialogService: DialogService, callParam: CallParam, titleKey: string): DynamicDialogRef {
    const width = (componentType as any).DIALOG_WIDTH || 400;
    const dynamicDialogHelper = new DynamicDialogHelper(translateService, dialogService,
      componentType, titleKey);
    return dynamicDialogHelper.openDynamicDialog(width, {callParam: callParam});
  }
}
