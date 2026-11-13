import { Injector } from '@angular/core';
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
import { Observable } from 'rxjs';

export class DynamicDialogs extends DynamicDialogHelper {
  /**
   * Opens the dialog in which the user applies for a higher daily limit of changes on an entity. Opens once on
   * subscription.
   *
   * @param injector - Resolves the translation and dialog services, so a caller needs no import of either
   * @param entityName - Entity class name the limit belongs to, as the backend reports it
   * @returns The reference of the opened dialog; it emits nothing when opening is refused
   */
  public static openLimitTransactionRequest(injector: Injector, entityName: string): Observable<DynamicDialogRef> {
    return new DynamicDialogHelper(
      injector.get(TranslateService),
      injector.get(DialogService),
      LimitTransactionRequestDynamicDialogComponent,
      'APPLY_LIMIT_TITLE'
    ).openDynamicDialog(400, { entityName });
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
    dynamicDialogHelper.openDynamicDialog(400, { email, password }).subscribe();
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
    dynamicDialogHelper.openDynamicDialog(400, { email, password }).subscribe();
    return dynamicDialogHelper;
  }

  public static getOpenedMailSendComponent(
    translateService: TranslateService,
    dialogService: DialogService,
    mailSendParam: MailSendParam
  ): Observable<DynamicDialogRef> {
    const dynamicDialogHelper = new DynamicDialogHelper(
      translateService,
      dialogService,
      MailSendDynamicDialogComponent,
      'MAIL_SEND_DIALOG'
    );
    return dynamicDialogHelper.openDynamicDialog(800, { mailSendParam });
  }
}
