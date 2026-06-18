import {Component, OnInit} from '@angular/core';
import {TranslateService, TranslateModule} from '@ngx-translate/core';
import {combineLatest} from 'rxjs';
import {DynamicDialogRef} from 'primeng/dynamicdialog';

import {PasswordBaseComponent} from '../../login/component/password.base.component';
import {GlobalparameterService, PasswordRegexProperties} from '../../services/globalparameter.service';
import {MessageToastService} from '../../message/message.toast.service';
import {InfoLevelType} from '../../message/info.leve.type';
import {DynamicFieldModelHelper} from '../../helper/dynamic.field.model.helper';
import {DynamicFieldHelper} from '../../helper/dynamic.field.helper';
import {AppHelper} from '../../helper/app.helper';
import {TranslateHelper} from '../../helper/translate.helper';
import {FieldDescriptorInputAndShow} from '../../dynamicfield/field.descriptor.input.and.show';
import {ProcessedActionData} from '../../types/processed.action.data';
import {ProcessedAction} from '../../types/processed.action';
import {DynamicFormModule} from '../../dynamic-form/dynamic-form.module';
import {HelpIds} from '../../help/help.ids';
import {ManageClientService} from '../service/manage-client.service';

/**
 * Dialog where a tenant owner grants another person read access to their own portfolio. The owner first enters an
 * e-mail and clicks "Check e-mail": the backend resolves the recipient status and the e-mail is locked (read-only) and
 * the check button disabled. If the recipient is already registered, no password is needed and a read-only access grant
 * is created; if the recipient is not yet registered, the password fields appear (required) and a read-only viewer login
 * is created and e-mailed the credentials. The owner's own e-mail and someone who already has access are rejected,
 * restarting the workflow. Editing the e-mail again re-opens the check. Part of the manage-client library feature
 * (g.use.manageclient).
 */
@Component({
  template: `
    @if (formConfig) {
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm" (submitBt)="submit($event)">
      </dynamic-form>
    }
  `,
  standalone: true,
  imports: [DynamicFormModule, TranslateModule]
})
export class ShareReadAccessDynamicComponent extends PasswordBaseComponent implements OnInit {

  /** True once the entered e-mail has been checked and accepted, so the grant button may be enabled. */
  private checked = false;

  constructor(private dynamicDialogRef: DynamicDialogRef,
    private manageClientService: ManageClientService,
    private messageToastService: MessageToastService,
    gps: GlobalparameterService,
    translateService: TranslateService) {
    super(gps, translateService);
  }

  ngOnInit(): void {
    combineLatest([this.gps.getUserFormDefinitions(), this.gps.getPasswordRegexProperties()]).subscribe(
      (data: [FieldDescriptorInputAndShow[], PasswordRegexProperties]) => {
        this.passwordRegexProperties = data[1];
        this.buildForm(data[0]);
        setTimeout(() => {
          this.preparePasswordFields();
          this.resetToEmailEntry(true);
          // Editing the e-mail after a check re-opens the check so the grant always reflects a checked address.
          this.configObject.email.formControl.valueChanges.subscribe(() => {
            if (this.checked) {
              this.resetToEmailEntry(false);
            }
          });
        });
      });
  }

  submit(value: { [name: string]: any }): void {
    if (!this.checked) {
      return;
    }
    const businessObject: any = {};
    this.form.cleanMaskAndTransferValuesToBusinessObject(businessObject, true);
    this.form.setDisableAll(true);
    this.manageClientService.shareReadAccess({email: businessObject.email, password: businessObject.password}).subscribe({
      next: () => {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'SHARE_READ_ACCESS_DONE');
        this.dynamicDialogRef.close(new ProcessedActionData(ProcessedAction.CREATED, businessObject));
      },
      error: () => {
        this.form.setDisableAll(false);
        this.configObject.submit.disabled = false;
      }
    });
  }

  /** Resolves the recipient status, then locks the e-mail / check button and reveals the password only when needed. */
  private checkExistence(): void {
    const email = (this.configObject.email.formControl.value || '').trim();
    if (!email || this.configObject.email.formControl.invalid) {
      this.configObject.email.formControl.markAsTouched();
      return;
    }
    this.manageClientService.checkRecipientStatus(email).subscribe(response => {
      switch (response.status) {
        case 'SELF':
          this.messageToastService.showMessageI18n(InfoLevelType.WARNING, 'SHARE_SELF_REJECTED');
          this.resetToEmailEntry(true);
          break;
        case 'ALREADY_SHARED':
          this.messageToastService.showMessageI18n(InfoLevelType.WARNING, 'SHARE_ALREADY_REJECTED');
          this.resetToEmailEntry(true);
          break;
        case 'EXISTS':
          // Registered user: no password needed; sharing creates a read-only access grant.
          this.lockEmailEntry();
          this.checked = true;
          this.configObject.submit.disabled = false;
          break;
        case 'NEW':
          // Not registered: an initial password is required to create the read-only viewer login.
          this.lockEmailEntry();
          AppHelper.enableAndVisibleInput(this.configObject.password);
          AppHelper.enableAndVisibleInput(this.configObject.passwordConfirm);
          this.checked = true;
          this.configObject.submit.disabled = false;
          break;
      }
    });
  }

  /**
   * Locks the e-mail field (read-only, control stays enabled so the form remains valid) and disables the check button.
   */
  private lockEmailEntry(): void {
    this.configObject.email.readonly = true;
    this.configObject.checkExistence.disabled = true;
  }

  /**
   * Opens (or re-opens) the e-mail entry step: editable e-mail, enabled check button, password hidden and the grant
   * button disabled until the next successful check.
   *
   * @param clearEmail when true the e-mail value is cleared (initial open / rejection); when false the current value is
   *                   kept (the owner is editing it)
   */
  private resetToEmailEntry(clearEmail: boolean): void {
    this.checked = false;
    this.configObject.email.readonly = false;
    this.configObject.checkExistence.disabled = false;
    if (clearEmail) {
      this.configObject.email.formControl.setValue(null);
    }
    AppHelper.disableAndHideInput(this.configObject.password);
    AppHelper.disableAndHideInput(this.configObject.passwordConfirm);
    this.configObject.password.formControl.setValue(null);
    this.configObject.passwordConfirm.formControl.setValue(null);
    this.configObject.submit.disabled = true;
    if (clearEmail) {
      setTimeout(() => this.configObject.email.elementRef.nativeElement.focus());
    }
  }

  /** Opens the manage-client help page in the gt-user-manual. */
  helpLink(): void {
    this.gps.toExternalHelpWebpage(this.gps.getUserLang(), HelpIds.HELP_MANAGE_CLIENT);
  }

  private buildForm(fdias: FieldDescriptorInputAndShow[]): void {
    super.init(fdias, false);
    this.formConfig = {labelColumns: 3, nonModal: true, language: this.translateService.currentLang,
      helpLinkFN: this.helpLink.bind(this)};
    this.config = [
      DynamicFieldModelHelper.ccWithFieldsFromDescriptorHeqF(this.translateService, 'email', fdias),
      DynamicFieldHelper.createFunctionButtonFieldName('checkExistence', 'CHECK_EXISTENCE',
        () => this.checkExistence()),
      {formGroupName: 'passwordGroup', fieldConfig: this.configPassword},
      DynamicFieldHelper.createSubmitButton('GRANT_READ_ACCESS')
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }
}
