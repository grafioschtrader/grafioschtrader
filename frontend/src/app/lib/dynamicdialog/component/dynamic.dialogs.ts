import {Router} from '@angular/router';
import {TranslateService} from '@ngx-translate/core';
import {DialogService, DynamicDialogRef} from 'primeng/dynamicdialog';
import {GlobalparameterService} from '../../services/globalparameter.service';
import {LoginService} from '../../login/service/log-in.service';
import {LimitTransactionRequestDynamicComponent} from './limit.transaction.request.dynamic.component';
import {LogoutAdminSelfReleaseDynamicComponent} from './logout.admin.self.release.dynamic.component';
import {LogoutReleaseRequestDynamicComponent} from './logout.release.request.dynamic.component';
import {MailSendDynamicComponent, MailSendParam} from './mail.send.dynamic.component';
import {DynamicDialogHelper} from './dynamicDialogHelper';

export class DynamicDialogs extends DynamicDialogHelper {

  public static getOpenedLimitTransactionRequestDynamicComponent(translateService: TranslateService,
    dialogService: DialogService,
    entityName: string): DynamicDialogHelper {
    const dynamicDialogHelper = new DynamicDialogHelper(translateService, dialogService,
      LimitTransactionRequestDynamicComponent, 'APPLY_LIMIT_TITLE');
    dynamicDialogHelper.openDynamicDialog(400, {entityName});
    return dynamicDialogHelper;
  }

  public static getOpenedAdminSelfReleaseDynamicComponent(translateService: TranslateService,
    dialogService: DialogService, loginService: LoginService, gps: GlobalparameterService,
    router: Router, email: string, password: string): DynamicDialogHelper {
    const dynamicDialogHelper = new DynamicDialogHelper(translateService, dialogService,
      LogoutAdminSelfReleaseDynamicComponent, 'ADMIN_SELF_RELEASE_TITLE');
    dynamicDialogHelper.openDynamicDialog(400, {email, password});
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
}
