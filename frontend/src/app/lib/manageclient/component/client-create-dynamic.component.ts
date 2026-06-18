import {Component, OnInit} from '@angular/core';
import {TranslateService, TranslateModule} from '@ngx-translate/core';
import {combineLatest} from 'rxjs';
import {DynamicDialogRef} from 'primeng/dynamicdialog';

import {PasswordBaseComponent} from '../../login/component/password.base.component';
import {GlobalparameterService, PasswordRegexProperties} from '../../services/globalparameter.service';
import {MessageToastService} from '../../message/message.toast.service';
import {InfoLevelType} from '../../message/info.leve.type';
import {DynamicFieldHelper} from '../../helper/dynamic.field.helper';
import {DynamicFieldModelHelper} from '../../helper/dynamic.field.model.helper';
import {TranslateHelper} from '../../helper/translate.helper';
import {FieldDescriptorInputAndShow} from '../../dynamicfield/field.descriptor.input.and.show';
import {ProcessedActionData} from '../../types/processed.action.data';
import {ProcessedAction} from '../../types/processed.action';
import {DynamicFormModule} from '../../dynamic-form/dynamic-form.module';
import {HelpIds} from '../../help/help.ids';
import {ManageClientService} from '../service/manage-client.service';

/**
 * Dialog where an advisor creates a managed client by entering the client's e-mail address and a password. On submit a
 * new tenant with a read-only client login is created and the client is e-mailed their credentials. Part of the
 * manage-client library feature (g.use.manageclient).
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
export class ClientCreateDynamicComponent extends PasswordBaseComponent implements OnInit {

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
        setTimeout(() => this.preparePasswordFields());
      });
  }

  submit(value: { [name: string]: any }): void {
    const businessObject: any = {};
    this.form.cleanMaskAndTransferValuesToBusinessObject(businessObject, true);
    this.form.setDisableAll(true);
    this.manageClientService.createClient({email: businessObject.email, password: businessObject.password}).subscribe({
      next: () => {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'CLIENT_CREATED');
        this.dynamicDialogRef.close(new ProcessedActionData(ProcessedAction.CREATED, businessObject));
      },
      error: () => {
        this.form.setDisableAll(false);
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
    this.formConfig = {labelColumns: 3, nonModal: true, language: this.translateService.currentLang,
      helpLinkFN: this.helpLink.bind(this)};
    this.config = [
      DynamicFieldModelHelper.ccWithFieldsFromDescriptorHeqF(this.translateService, 'email', fdias),
      {formGroupName: 'passwordGroup', fieldConfig: this.configPassword},
      DynamicFieldHelper.createSubmitButton('CREATE_CLIENT')
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    setTimeout(() => this.configObject.email.elementRef.nativeElement.focus());
  }
}
