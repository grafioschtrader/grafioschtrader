import {TranslateService} from '@ngx-translate/core';
import {Type} from '@angular/core';
import {LimitTransactionRequestDynamicComponent} from './limit.transaction.request.dynamic.component';
import {LogoutReleaseRequestDynamicComponent} from './logout.release.request.dynamic.component';
import {MailSendDynamicComponent, MailSendParam} from './mail.send.dynamic.component';
import {DialogService, DynamicDialogRef} from 'primeng/dynamicdialog';
import {MailSendRecv} from '../../../mail/model/mail.send.recv';

export class DynamicDialogHelper {

  constructor(private translateService: TranslateService,
              private dialogService: DialogService,
              private componentType: Type<any>,
              private titleKey: string) {
  }

  public static getOpenedLimitTransactionRequestDynamicComponent(translateService: TranslateService,
                                                                 dialogService: DialogService,
                                                                 entityName: string): DynamicDialogHelper {
    const dynamicDialogHelper = new DynamicDialogHelper(translateService, dialogService,
      LimitTransactionRequestDynamicComponent, 'APPLY_LIMIT_TITLE');
    dynamicDialogHelper.openDynamicDialog(400, {entityName});
    return dynamicDialogHelper;
  }

  public static getOpenedLogoutReleaseRequestDynamicComponent(translateService: TranslateService,
                                                              dialogService: DialogService, email: string,
                                                              password: string): DynamicDialogHelper {
    const dynamicDialogHelper = new DynamicDialogHelper(translateService, dialogService,
      LogoutReleaseRequestDynamicComponent, 'RESET_USER_MISUSED');
    dynamicDialogHelper.openDynamicDialog(400, {email, password});
    return dynamicDialogHelper;
  }


  public static getOpenedMailSendComponent(translateService: TranslateService,
                                           dialogService: DialogService,
                                           mailSendParam: MailSendParam): DynamicDialogRef {
    const dynamicDialogHelper = new DynamicDialogHelper(translateService, dialogService,
      MailSendDynamicComponent, 'MAIL_SEND_DIALOG');
    return dynamicDialogHelper.openDynamicDialog(800, {mailSendParam});
  }


  private openDynamicDialog(widthPx: number, data?: any, contentStyle?: any): DynamicDialogRef {
    let dynamicDialogRef: DynamicDialogRef;
    this.translateService.get(this.titleKey).subscribe(msg => {
      dynamicDialogRef = this.dialogService.open(this.componentType, {
        header: msg, width: widthPx + 'px',
        contentStyle,
        data
      });
    });
    return dynamicDialogRef;
  }


}
