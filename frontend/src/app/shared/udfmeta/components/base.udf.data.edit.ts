import {SimpleEditBase} from '../../edit/simple.edit.base';
import {MessageToastService} from '../../message/message.toast.service';
import {UDFDataService} from '../service/udf.data.service';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../service/globalparameter.service';
import {HelpIds} from '../../help/help.ids';
import {AppHelper} from '../../helper/app.helper';
import {UDFData, UDFDataKey, UDFGeneralCallParam} from '../model/udf.metadata';
import {InfoLevelType} from '../../message/info.leve.type';
import {ProcessedActionData} from '../../types/processed.action.data';
import {ProcessedAction} from '../../types/processed.action';
import {DynamicFieldModelHelper} from '../../helper/dynamic.field.model.helper';
import {TranslateHelper} from '../../helper/translate.helper';
import {FieldDescriptorInputAndShowExtended} from '../../dynamicfield/field.descriptor.input.and.show';
import {Directive, Input} from '@angular/core';
import {DataType} from '../../../dynamic-form/models/data.type';
import {Helper} from '../../../helper/helper';
import {AppSettings} from '../../app.settings';

/**
 * This is the base class for editing the content of user-defined fields.
 */
@Directive()
export abstract class BaseUDFDataEdit extends SimpleEditBase {
  @Input() uDFGeneralCallParam: UDFGeneralCallParam;
  entityKeyName: string;

  protected constructor(private messageToastService: MessageToastService,
    private uDFDataService: UDFDataService,
    public translateService: TranslateService,
    helpId: HelpIds,
    gps: GlobalparameterService) {
    super(helpId, gps);
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      4, this.helpLink.bind(this));
  }

  protected baseInit(fdList: FieldDescriptorInputAndShowExtended[]): void {
    this.config = DynamicFieldModelHelper.createConfigFieldsFromExtendedDescriptor(fdList, '', true);
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    this.entityKeyName = this.gps.getKeyNameByEntityName(this.uDFGeneralCallParam.entityName);
  }

  protected override initialize(): void {
    if (!this.uDFGeneralCallParam.udfData) {
      this.uDFDataService.getUDFDataByEntityAndIdEntity(this.uDFGeneralCallParam.entityName,
        <number>this.uDFGeneralCallParam.selectedEntity[this.entityKeyName]).subscribe(udfData => {
        this.prepareData(udfData?.jsonValues)
      });
    }
    this.prepareData(this.uDFGeneralCallParam.udfData);
  }

  private prepareData(jsonValues: any): void {
    jsonValues && this.form.transferBusinessObjectToForm(jsonValues);
  }

  submit(value: { [name: string]: any }): void {
    const udfData = new UDFData(new UDFDataKey(null, this.uDFGeneralCallParam.entityName,
      <number>this.uDFGeneralCallParam.selectedEntity[this.entityKeyName]), value);
    this.formatDate(value);
    this.uDFDataService.update(udfData, this.uDFGeneralCallParam.udfData ? 1 : null).subscribe({
      next: (uDFData: UDFData) => {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED',
          {i18nRecord: 'UDF'});
        // Read only the watchlist, the watchlist was not changed
        this.closeDialog.emit(new ProcessedActionData(ProcessedAction.UPDATED));
      }, error: () => this.configObject.submit.disabled = false
    });
  }

  private formatDate(value: { [name: string]: any }): void {
    this.config.filter(c => c.dataType === DataType.DateString).forEach(c => value[c.field] =
      Helper.formatDateStringAsString(c, AppSettings.FORMAT_DATE_SHORT_NATIVE));
  }
}
