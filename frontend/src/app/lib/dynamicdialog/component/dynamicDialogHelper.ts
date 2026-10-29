import { TranslateService } from '@ngx-translate/core';
import { Type } from '@angular/core';
import { DialogService, DynamicDialogRef } from '@openng/optimus-ui/dynamicdialog';
import { filter, map, Observable, take } from 'rxjs';

export class DynamicDialogHelper {
  constructor(
    private translateService: TranslateService,
    private dialogService: DialogService,
    private componentType: Type<any>,
    private titleKey: string
  ) {}

  /** Opens once on subscription, after the title is translated; a refused opening emits no reference. */
  public openDynamicDialog(widthPx: number, data?: any, contentStyle?: any): Observable<DynamicDialogRef> {
    return this.translateService.get(this.titleKey).pipe(
      take(1),
      map((msg) =>
        this.dialogService.open(this.componentType, {
          header: msg,
          width: widthPx + 'px',
          modal: true,
          closable: true,
          closeOnEscape: true,
          contentStyle,
          data
        })
      ),
      filter((ref) => ref !== null)
    );
  }
}
