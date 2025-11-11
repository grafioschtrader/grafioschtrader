import {Injectable, Type} from '@angular/core';
import {Observable} from 'rxjs';
import {TranslateService} from '@ngx-translate/core';
import {DialogService} from 'primeng/dynamicdialog';
import {DialogHandler} from '../../../lib/maintree/handler/dialog-handler.interface';
import {MainTreeDynamicDialogs} from '../../../dynamic-dialog/component/main.tree.dynamic.dialogs';
import {CallParam} from '../types/dialog.visible';
import {Portfolio} from '../../../entities/portfolio';
import {Cashaccount} from '../../../entities/cashaccount';
import {Securityaccount} from '../../../entities/securityaccount';
import {Watchlist} from '../../../entities/watchlist';
import {Tenant} from '../../../entities/tenant';
import {AlgoTopCreate} from '../../../entities/backend/algo.top.create';

/**
 * Application-level implementation of DialogHandler.
 * This class knows about all the domain entities and how to open dialogs for them.
 */
@Injectable()
export class AppDialogHandler implements DialogHandler {

  constructor(
    private translateService: TranslateService,
    private dialogService: DialogService
  ) {}

  openEditDialog(
    componentType: Type<any>,
    parentObject: any,
    data: Tenant | Portfolio | Cashaccount | Securityaccount | Watchlist | AlgoTopCreate | null,
    titleKey: string
  ): Observable<any> {
    const callParam = new CallParam(parentObject, data);
    return MainTreeDynamicDialogs.getEditDialogComponent(
      componentType,
      this.translateService,
      this.dialogService,
      callParam,
      titleKey
    ).onClose;
  }

  openTenantDialog(data: Tenant, onlyCurrency: boolean): Observable<any> {
    return MainTreeDynamicDialogs.getTenantEditDialogComponent(
      this.translateService,
      this.dialogService,
      data,
      onlyCurrency
    ).onClose;
  }
}
