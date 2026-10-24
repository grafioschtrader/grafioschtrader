import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { DialogHandler } from '../../../lib/maintree/handler/dialog-handler.interface';
import { DialogComponentLoader, openLazyDialog } from '../../../lib/maintree/handler/lazy-dialog';
import { MessageToastService } from '../../../lib/message/message.toast.service';
import { InfoLevelType } from '../../../lib/message/info.leve.type';
import { MainTreeDynamicDialogs } from '../../../dynamic-dialog/component/main.tree.dynamic.dialogs';
import { CallParam } from '../types/dialog.visible';
import { Portfolio } from '../../../entities/portfolio';
import { Cashaccount } from '../../../entities/cashaccount';
import { Securityaccount } from '../../../entities/securityaccount';
import { Watchlist } from '../../../entities/watchlist';
import { Tenant } from '../../../entities/tenant';
import { AlgoTopCreate } from '../../../entities/backend/algo.top.create';

/**
 * Application-level implementation of DialogHandler.
 * This class knows about all the domain entities and how to open dialogs for them. The dialog classes are loaded on
 * first use, see {@link openLazyDialog}.
 */
@Injectable()
export class AppDialogHandler implements DialogHandler {
  constructor(
    private translateService: TranslateService,
    private dialogService: DialogService,
    private messageToastService: MessageToastService
  ) {}

  openEditDialog(
    loadComponent: DialogComponentLoader,
    parentObject: any,
    data: Tenant | Portfolio | Cashaccount | Securityaccount | Watchlist | AlgoTopCreate | null,
    titleKey: string
  ): Observable<any> {
    const callParam = new CallParam(parentObject, data);
    return openLazyDialog(
      loadComponent,
      (componentType) =>
        MainTreeDynamicDialogs.getEditDialogComponent(
          componentType,
          this.translateService,
          this.dialogService,
          callParam,
          titleKey
        ).onClose,
      () => this.reportLoadFailure()
    );
  }

  openTenantDialog(data: Tenant, onlyCurrency: boolean): Observable<any> {
    return openLazyDialog(
      MainTreeDynamicDialogs.TENANT_EDIT_LOADER,
      (componentType) =>
        MainTreeDynamicDialogs.getTenantEditDialogComponent(
          componentType,
          this.translateService,
          this.dialogService,
          data,
          onlyCurrency
        ).onClose,
      () => this.reportLoadFailure()
    );
  }

  private reportLoadFailure(): void {
    this.messageToastService.showMessageI18n(InfoLevelType.ERROR, 'MSG_LOAD_NEW_VERSION');
  }
}
