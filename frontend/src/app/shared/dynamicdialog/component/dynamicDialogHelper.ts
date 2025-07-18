import {TranslateService} from '@ngx-translate/core';
import {Type} from '@angular/core';
import {DialogService, DynamicDialogRef} from 'primeng/dynamicdialog';

export class DynamicDialogHelper {

  constructor(private translateService: TranslateService,
    private dialogService: DialogService,
    private componentType: Type<any>,
    private titleKey: string) {
  }

  public openDynamicDialog(widthPx: number, data?: any, contentStyle?: any): DynamicDialogRef {
    let dynamicDialogRef: DynamicDialogRef;
    this.translateService.get(this.titleKey).subscribe(msg => {
      dynamicDialogRef = this.dialogService.open(this.componentType, {
        header: msg, width: widthPx + 'px',
        closable: true,
        contentStyle,
        data
      });
    });
    return dynamicDialogRef;
  }

}
