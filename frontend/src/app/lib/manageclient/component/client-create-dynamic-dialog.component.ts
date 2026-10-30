import { afterNextRender, Component, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateModule } from '@ngx-translate/core';
import { combineLatest } from 'rxjs';
import { DynamicDialogRef } from '@openng/optimus-ui/dynamicdialog';

import { PasswordBaseComponent } from '../../login/component/password.base.component';
import { PasswordRegexProperties } from '../../services/globalparameter.service';
import { MessageToastService } from '../../message/message.toast.service';
import { InfoLevelType } from '../../message/info.leve.type';
import { DynamicFieldHelper } from '../../helper/dynamic.field.helper';
import { DynamicFieldModelHelper } from '../../helper/dynamic.field.model.helper';
import { TranslateHelper } from '../../helper/translate.helper';
import { FieldDescriptorInputAndShow } from '../../dynamicfield/field.descriptor.input.and.show';
import { ProcessedActionData } from '../../types/processed.action.data';
import { ProcessedAction } from '../../types/processed.action';
import { DYNAMIC_FORM_IMPORTS } from '../../dynamic-form/dynamic-form.imports';
import { HelpIds } from '../../help/help.ids';
import { ManageClientService } from '../service/manage-client.service';

/**
 * Dialog where an advisor creates a managed client by entering the client's e-mail address and a password. On submit a
 * new tenant with a read-only client login is created and the client is e-mailed their credentials. Part of the
 * manage-client library feature (g.use.manageclient).
 */
@Component({
  template: `
    @if (formConfig) {
      <dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        [translateService]="translateService"
        #form="dynamicForm"
        (submitBt)="submit($event)" />
    }
  `,
  imports: [...DYNAMIC_FORM_IMPORTS, TranslateModule]
})
export class ClientCreateDynamicDialogComponent extends PasswordBaseComponent implements OnInit {
  private dynamicDialogRef = inject(DynamicDialogRef);
  private manageClientService = inject(ManageClientService);
  private messageToastService = inject(MessageToastService);

  ngOnInit(): void {
    combineLatest([this.gps.getUserFormDefinitions(), this.gps.getPasswordRegexProperties()])
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((data: [FieldDescriptorInputAndShow[], PasswordRegexProperties]) => {
        this.passwordRegexProperties = data[1];
        this.buildForm(data[0]);
        // The form appears with formConfig; its controls and inputs exist after that render
        this.viewChanged();
        afterNextRender(
          () => {
            this.preparePasswordFields();
            this.configObject.email.elementRef?.nativeElement.focus();
          },
          { injector: this.injector }
        );
      });
  }

  submit(value: { [name: string]: any }): void {
    const form = this.form();
    if (!form) {
      return;
    }
    const businessObject: any = {};
    form.cleanMaskAndTransferValuesToBusinessObject(businessObject, true);
    form.setDisableAll(true);
    this.manageClientService
      .createClient({
        email: businessObject.email,
        password: businessObject.password
      })
      .subscribe({
        next: () => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'CLIENT_CREATED');
          this.dynamicDialogRef.close(new ProcessedActionData(ProcessedAction.CREATED, businessObject));
        },
        error: () => {
          this.form()?.setDisableAll(false);
          this.configObject.submit.disabled = false;
        }
      });
  }

  /** Opens the manage-client help page in the gt-user-manual. */
  helpLink(): void {
    this.gps.toExternalHelpWebpage(this.gps.getUserLang(), HelpIds.HELP_MANAGE_CLIENT);
  }

  private buildForm(fdias: FieldDescriptorInputAndShow[]): void {
    super.init(fdias, false);
    this.formConfig = {
      labelColumns: 3,
      nonModal: true,
      language: this.translateService.currentLang,
      helpLinkFN: this.helpLink.bind(this)
    };
    this.config = [
      DynamicFieldModelHelper.ccWithFieldsFromDescriptorHeqF(this.translateService, 'email', fdias),
      { formGroupName: 'passwordGroup', fieldConfig: this.configPassword },
      DynamicFieldHelper.createSubmitButton('CREATE_CLIENT')
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }
}
