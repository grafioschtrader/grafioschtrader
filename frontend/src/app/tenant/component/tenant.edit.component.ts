import {Directive, EventEmitter, Input, Output, ViewChild} from '@angular/core';
import {ProcessedActionData} from '../../shared/types/processed.action.data';
import {ProcessedAction} from '../../shared/types/processed.action';
import {FieldConfig} from '../../dynamic-form/models/field.config';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';

import {TranslateService} from '@ngx-translate/core';
import {AppHelper} from '../../shared/helper/app.helper';
import {DynamicFormComponent} from '../../dynamic-form/containers/dynamic-form/dynamic-form.component';
import {CallParam} from '../../shared/maintree/types/dialog.visible';
import {InfoLevelType} from '../../shared/message/info.leve.type';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {TenantService} from '../service/tenant.service';
import {Tenant} from '../../entities/tenant';
import {FormConfig} from '../../dynamic-form/models/form.config';
import {HelpIds} from '../../shared/help/help.ids';
import {DynamicFieldHelper} from '../../shared/helper/dynamic.field.helper';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {BusinessHelper} from '../../shared/helper/business.helper';

/**
 * Form for editing the tenant. It also supports changing the currency of the tenant and its portfolios.
 */
@Directive()
export abstract class TenantEditComponent {

  // InputMask from parent view
  @Input() visibleTenantDialog: boolean;
  @ViewChild(DynamicFormComponent) form: DynamicFormComponent;
  @Input() callParam: CallParam;

  // Output for parent view
  @Output() closeDialog = new EventEmitter<ProcessedActionData>();

  configObject: { [name: string]: FieldConfig };
  config: FieldConfig[] = [];
  formConfig: FormConfig;

  constructor(protected gps: GlobalparameterService,
              protected messageToastService: MessageToastService,
              protected tenantService: TenantService,
              public translateService: TranslateService,
              private nonModal: boolean, private labelColumns: number) {
  }

  init(onlyCurrency: boolean): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      this.labelColumns, this.helpLink.bind(this), this.nonModal);

    this.config = this.getFields(onlyCurrency);
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  onHide(event) {
    this.closeDialog.emit(new ProcessedActionData(ProcessedAction.NO_CHANGE));
  }

  submit(value: { [name: string]: any }) {
    const tenant: Tenant = new Tenant();
    if (this.callParam && this.callParam.thisObject) {
      Object.assign(tenant, this.callParam.thisObject);
    }
    this.form.cleanMaskAndTransferValuesToBusinessObject(tenant);
    this.tenantService.update(tenant).subscribe(newTenant => {
      this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED', {i18nRecord: 'CLIENT'});

      const tenantNew: Tenant = Object.assign(new Tenant(), newTenant);
      this.afterSaved(tenantNew);
    }, () => this.configObject.submit.disabled = false);
  }

  helpLink(): void {
    BusinessHelper.toExternalHelpWebpage(this.gps.getUserLang(), HelpIds.HELP_CLIENT);
  }

  protected abstract afterSaved(tenant: Tenant): void;

  protected loadData() {
    this.gps.getCurrencies().subscribe(data => {
        this.form.setDefaultValuesAndEnableSubmit();
        this.configObject.currency.valueKeyHtmlOptions = data;
        if (this.callParam && this.callParam.thisObject != null) {
          this.form.transferBusinessObjectToForm(this.callParam.thisObject);
        }
        if (this.configObject.tenantName) {
          this.configObject.tenantName.elementRef.nativeElement.focus();
        } else {
          this.configObject.currency.elementRef.nativeElement.focus();
        }
      }
    );
  }

  private getFields(onlyCurrency: boolean): FieldConfig[] {
    const fieldConfig = [DynamicFieldHelper.createFieldInputStringHeqF('tenantName', 25, true),
      DynamicFieldHelper.createFieldSelectStringHeqF('currency', true),
      DynamicFieldHelper.createFieldCheckboxHeqF('excludeDivTax'),
      DynamicFieldHelper.createSubmitButton()];
    return (onlyCurrency) ? [fieldConfig[1], fieldConfig[3]] : fieldConfig;
  }
}

