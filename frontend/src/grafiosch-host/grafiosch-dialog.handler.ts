import {Injectable, Type} from '@angular/core';
import {DialogService} from 'primeng/dynamicdialog';
import {Observable, of} from 'rxjs';

import {DialogHandler} from '../app/lib/maintree/handler/dialog-handler.interface';

/**
 * The {@code DIALOG_HANDLER} implementation of this host. The main tree calls it whenever a node wants to open an edit
 * dialog; the library cannot provide it itself because the component types are application specific.
 *
 * <p>This host has no editable tree nodes — its tree is a pure navigation tree — so {@link #openEditDialog} is only
 * reached if a contributor is added later, and the tenant dialog is deliberately not offered: the tenant is created
 * once on the full page {@code GrafioschTenantEditComponent} and has a single field.</p>
 */
@Injectable()
export class GrafioschDialogHandler implements DialogHandler {

  constructor(private dialogService: DialogService) {
  }

  openEditDialog(componentType: Type<any>, parentObject: any, data: any, titleKey: string): Observable<any> {
    return this.dialogService.open(componentType, {
      data: {callParam: {parentObject, thisObject: data}},
      header: titleKey,
      modal: true,
      closable: true,
      closeOnEscape: true,
      width: '50vw'
    }).onClose;
  }

  openTenantDialog(data: any, onlyCurrency: boolean): Observable<any> {
    return of(null);
  }
}
