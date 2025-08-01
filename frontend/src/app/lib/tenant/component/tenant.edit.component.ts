import {Directive, EventEmitter, Output, ViewChild} from '@angular/core';
import {ProcessedActionData} from '../../types/processed.action.data';
import {ProcessedAction} from '../../types/processed.action';
import {FieldConfig} from '../../dynamic-form/models/field.config';
import {GlobalparameterService} from '../../../shared/service/globalparameter.service';
import {TranslateService} from '@ngx-translate/core';
import {AppHelper} from '../../helper/app.helper';
import {DynamicFormComponent} from '../../dynamic-form/containers/dynamic-form/dynamic-form.component';
import {InfoLevelType} from '../../message/info.leve.type';
import {MessageToastService} from '../../message/message.toast.service';
import {TenantService} from '../service/tenant.service';
import {Tenant} from '../../../entities/tenant';
import {FormConfig} from '../../dynamic-form/models/form.config';
import {HelpIds} from '../../../shared/help/help.ids';
import {DynamicFieldHelper} from '../../helper/dynamic.field.helper';
import {TranslateHelper} from '../../helper/translate.helper';
import {BusinessHelper} from '../../../shared/helper/business.helper';
import {GlobalparameterGTService} from '../../../gtservice/globalparameter.gt.service';

/**
 * Form for editing the tenant. It also supports changing the currency of the tenant and its portfolios.
 */
@Directive()
export abstract class TenantEditComponent {
  existingTenant: Tenant;

  @ViewChild(DynamicFormComponent) form: DynamicFormComponent;

  // Output for parent view
  @Output() closeDialog = new EventEmitter<ProcessedActionData>();

  configObject: { [name: string]: FieldConfig };
  config: FieldConfig[] = [];
  formConfig: FormConfig;

  protected constructor(protected gpsGT: GlobalparameterGTService,
    protected gps: GlobalparameterService,
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
    if (this.existingTenant) {
      Object.assign(tenant, this.existingTenant);
    }
    this.form.cleanMaskAndTransferValuesToBusinessObject(tenant);
    this.tenantService.update(tenant).subscribe({
      next: newTenant => {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED', {i18nRecord: 'CLIENT'});
        const tenantNew: Tenant = Object.assign(new Tenant(), newTenant);
        this.closeInputDialog(tenantNew);
      }, error: () => this.configObject.submit.disabled = false
    });
  }

  protected closeInputDialog(tenant: Tenant): void {
  }

  helpLink(): void {
    BusinessHelper.toExternalHelpWebpage(this.gps.getUserLang(), HelpIds.HELP_CLIENT);
  }


  protected loadData() {
    this.gpsGT.getCurrencies().subscribe(data => {
        this.form.setDefaultValuesAndEnableSubmit();
        this.configObject.currency.valueKeyHtmlOptions = data;
        if (this.existingTenant) {
          this.form.transferBusinessObjectToForm(this.existingTenant);
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

