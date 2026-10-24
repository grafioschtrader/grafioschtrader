import { TranslateService } from '@ngx-translate/core';
import { DialogService, DynamicDialogRef } from '@openng/optimus-ui/dynamicdialog';
import { DynamicDialogHelper } from '../../lib/dynamicdialog/component/dynamicDialogHelper';
import { Tenant } from '../../entities/tenant';
import { Type } from '@angular/core';
import { CallParam } from '../../shared/maintree/types/dialog.visible';
import { DialogComponentLoader } from '../../lib/maintree/handler/lazy-dialog';

export class MainTreeDynamicDialogs {
  /** Loads the tenant dialog on first use, so it stays out of the bundle of the application shell. */
  public static readonly TENANT_EDIT_LOADER: DialogComponentLoader = () =>
    import('../../tenant/component/tenant.edit.dynamic.component').then((m) => m.TenantEditDynamicComponent);

  public static getTenantEditDialogComponent(
    tenantEditComponent: Type<unknown>,
    translateService: TranslateService,
    dialogService: DialogService,
    tenant: Tenant,
    onlyCurrency: boolean
  ): DynamicDialogRef {
    const dynamicDialogHelper = new DynamicDialogHelper(
      translateService,
      dialogService,
      tenantEditComponent,
      onlyCurrency ? 'TENANT_CHANGE_CURRENCY' : 'TENANT'
    );
    return dynamicDialogHelper.openDynamicDialog(400, { tenant: tenant, onlyCurrency: onlyCurrency });
  }

  public static getEditDialogComponent(
    componentType: Type<any>,
    translateService: TranslateService,
    dialogService: DialogService,
    callParam: CallParam,
    titleKey: string
  ): DynamicDialogRef {
    const width = (componentType as any).DIALOG_WIDTH || 400;
    const dynamicDialogHelper = new DynamicDialogHelper(translateService, dialogService, componentType, titleKey);
    return dynamicDialogHelper.openDynamicDialog(width, { callParam: callParam });
  }
}
