import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { DialogService, DynamicDialogRef } from '@openng/optimus-ui/dynamicdialog';
import { GlobalparameterService } from '../../services/globalparameter.service';
import { LoginService } from '../../login/service/log-in.service';
import { LimitTransactionRequestDynamicDialogComponent } from './limit-transaction-request-dynamic-dialog.component';
import { LogoutAdminSelfReleaseDynamicDialogComponent } from './logout-admin-self-release-dynamic-dialog.component';
import { LogoutReleaseRequestDynamicDialogComponent } from './logout-release-request-dynamic-dialog.component';
import { MailSendDynamicDialogComponent, MailSendParam } from './mail-send-dynamic-dialog.component';
import { DynamicDialogHelper } from './dynamicDialogHelper';

export class DynamicDialogs extends DynamicDialogHelper {
  public static getOpenedLimitTransactionRequestDynamicComponent(
    translateService: TranslateService,
    dialogService: DialogService,
    entityName: string
  ): DynamicDialogHelper {
    const dynamicDialogHelper = new DynamicDialogHelper(
      translateService,
      dialogService,
      LimitTransactionRequestDynamicDialogComponent,
      'APPLY_LIMIT_TITLE'
    );
    dynamicDialogHelper.openDynamicDialog(400, { entityName });
    return dynamicDialogHelper;
  }

  public static getOpenedAdminSelfReleaseDynamicComponent(
    translateService: TranslateService,
    dialogService: DialogService,
    loginService: LoginService,
    gps: GlobalparameterService,
    router: Router,
    email: string,
    password: string
  ): DynamicDialogHelper {
    const dynamicDialogHelper = new DynamicDialogHelper(
      translateService,
      dialogService,
      LogoutAdminSelfReleaseDynamicDialogComponent,
      'ADMIN_SELF_RELEASE_TITLE'
    );
    dynamicDialogHelper.openDynamicDialog(400, { email, password });
    return dynamicDialogHelper;
  }

  public static getOpenedLogoutReleaseRequestDynamicComponent(
    translateService: TranslateService,
    dialogService: DialogService,
    email: string,
    password: string
  ): DynamicDialogHelper {
    const dynamicDialogHelper = new DynamicDialogHelper(
      translateService,
      dialogService,
      LogoutReleaseRequestDynamicDialogComponent,
      'RESET_USER_MISUSED'
    );
    dynamicDialogHelper.openDynamicDialog(400, { email, password });
    return dynamicDialogHelper;
  }

  public static getOpenedMailSendComponent(
    translateService: TranslateService,
    dialogService: DialogService,
    mailSendParam: MailSendParam
  ): DynamicDialogRef {
    const dynamicDialogHelper = new DynamicDialogHelper(
      translateService,
      dialogService,
      MailSendDynamicDialogComponent,
      'MAIL_SEND_DIALOG'
    );
    return dynamicDialogHelper.openDynamicDialog(800, { mailSendParam });
  }
}
