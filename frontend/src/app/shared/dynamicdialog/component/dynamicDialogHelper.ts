import {TranslateService} from '@ngx-translate/core';
import {Type} from '@angular/core';
import {LimitTransactionRequestDynamicComponent} from './limit.transaction.request.dynamic.component';
import {LogoutReleaseRequestDynamicComponent} from './logout.release.request.dynamic.component';
import {MailSendDynamicComponent, MailSendParam} from './mail.send.dynamic.component';
import {DialogService, DynamicDialogRef} from 'primeng/dynamicdialog';
import {MailSendRecv} from '../../../lib/mail/model/mail.send.recv';

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
