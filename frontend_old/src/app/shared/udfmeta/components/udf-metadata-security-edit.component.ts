import {UDFDataType, UDFMetadataSecurity, UDFMetadataSecurityParam} from '../model/udf.metadata';
import {HelpIds} from '../../help/help.ids';
import {AppSettings} from '../../app.settings';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../service/globalparameter.service';
import {MessageToastService} from '../../message/message.toast.service';
import {UDFMetadataSecurityService} from '../service/udf.metadata.security.service';
import {Component, Input, OnInit} from '@angular/core';
import {AppHelper} from '../../helper/app.helper';
import {DynamicFieldHelper, VALIDATION_SPECIAL} from '../../helper/dynamic.field.helper';
import {TranslateHelper} from '../../helper/translate.helper';
import {AssetClassTypeSpecInstrument} from './asset.class.type.spec.instrument';
import {SelectOptionsHelper} from '../../helper/select.options.helper';
import {FormHelper} from '../../../dynamic-form/components/FormHelper';

/**
 * Edit user defined fields metadata of security in a dialog
 */
@Component({
  selector: 'udf-metadata-security-edit',
  template: `
      <p-dialog header="{{i18nRecord | translate}}" [(visible)]="visibleDialog"
                [style]="{width: '500px'}" (onShow)="onShow($event)" (onHide)="onHide($event)"
                [modal]="true">
          <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                        #form="dynamicForm"
                        (submitBt)="submit($event)">
          </dynamic-form>
      </p-dialog>
  `
})
export class UDFMetadataSecurityEditComponent extends AssetClassTypeSpecInstrument<UDFMetadataSecurity> implements OnInit {
  @Input() callParam: UDFMetadataSecurityParam;
  private readonly DESCRIPTION = 'description';
  private readonly UDF_DATA_TYPE = 'udfDataType';
  private readonly FIELD_SIZE = 'fieldSize';

  constructor(translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    uDFMetadataSecurityService: UDFMetadataSecurityService) {
    super(true, HelpIds.HELP_BASEDATA_UDF_METADATA_SECURITY, AppHelper.toUpperCaseWithUnderscore(AppSettings.UDF_METADATA_SECURITY), translateService, gps,
      messageToastService, uDFMetadataSecurityService);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      4, this.helpLink.bind(this));
    this.config = [
      DynamicFieldHelper.createFieldSelectNumberHeqF('uiOrder', true),
      DynamicFieldHelper.createFieldInputStringVSParam(this.DESCRIPTION, 'FIELD_DESCRIPTION', 24, true,
        VALIDATION_SPECIAL.NOT_CONTAIN_STRING_IN_LIST, this.callParam.excludeFieldNames),
      DynamicFieldHelper.createFieldTextareaInputString('descriptionHelp', 'FIELD_DESCRIPTION_HELP', 80, false),
      DynamicFieldHelper.createFieldSelectStringHeqF(this.UDF_DATA_TYPE, true),
      DynamicFieldHelper.createFieldInputStringHeqF(this.FIELD_SIZE, 5, true),
      DynamicFieldHelper.createFieldSelectString(AppSettings.CATEGORY_TYPE, AppSettings.ASSETCLASS.toUpperCase(), true),
      DynamicFieldHelper.createFieldSelectString('specialInvestmentInstrument', 'FINANCIAL_INSTRUMENT', true),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initializeOthers(): void {
    this.configObject.uiOrder.valueKeyHtmlOptions = SelectOptionsHelper.createValueKeyHtmlSelectOptionsForNumberRange(1, 99, this.callParam.excludeUiOrders)
    this.callParam.uDFMetadataSecurity && this.form.transferBusinessObjectToForm(this.callParam.uDFMetadataSecurity);
    this.configObject.udfDataType.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService,
      UDFDataType);
    FormHelper.disableEnableFieldConfigs(!!this.callParam.uDFMetadataSecurity, [this.configObject[this.DESCRIPTION],
      this.configObject[this.UDF_DATA_TYPE], this.configObject[this.FIELD_SIZE]]);
  }


  protected override getNewOrExistingInstanceBeforeSave(value: {
    [name: string]: any
  }): UDFMetadataSecurity {
    const uDFMetadataSecurity: UDFMetadataSecurity = this.copyFormToPrivateBusinessObject(new UDFMetadataSecurity(),
      this.callParam.uDFMetadataSecurity);
    uDFMetadataSecurity.idUser = this.gps.getIdUser();
    return uDFMetadataSecurity;
  }

}
