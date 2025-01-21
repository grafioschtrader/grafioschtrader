import {UDFDataType, UDFMetadataSecurity, UDFMetadataSecurityParam} from '../model/udf.metadata';
import {HelpIds} from '../../help/help.ids';
import {AppSettings} from '../../app.settings';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../service/globalparameter.service';
import {MessageToastService} from '../../message/message.toast.service';
import {UDFMetadataSecurityService} from '../service/udf.metadata.security.service';
import {Component, Input, OnInit} from '@angular/core';
import {AppHelper} from '../../helper/app.helper';
import {DynamicFieldHelper} from '../../helper/dynamic.field.helper';
import {TranslateHelper} from '../../helper/translate.helper';
import {AssetClassTypeSpecInstrument} from './asset.class.type.spec.instrument';
import {SelectOptionsHelper} from '../../helper/select.options.helper';
import {FieldConfig} from '../../../dynamic-form/models/field.config';
import {Subscription} from 'rxjs';
import {UDFMetadataHelper} from './udf.metadata.helper';

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
  `,
    standalone: false
})
export class UDFMetadataSecurityEditComponent extends AssetClassTypeSpecInstrument<UDFMetadataSecurity> implements OnInit {
  @Input() callParam: UDFMetadataSecurityParam;

  private configDataTypeFields: FieldConfig[];
  private dataTypeSubscribe: Subscription;

  constructor(translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    uDFMetadataSecurityService: UDFMetadataSecurityService) {
    super('categoryTypeEnums', 'specialInvestmentInstrumentEnums',
      HelpIds.HELP_BASEDATA_UDF_METADATA_SECURITY, AppHelper.toUpperCaseWithUnderscore(AppSettings.UDF_METADATA_SECURITY),
      translateService, gps, messageToastService, uDFMetadataSecurityService);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      4, this.helpLink.bind(this));
    this.configDataTypeFields = UDFMetadataHelper.createDataTypeFields();
    this.config = [...UDFMetadataHelper.createMetadataBaseFields(this.configDataTypeFields, this.callParam.excludeFieldNames),
      DynamicFieldHelper.createFieldMultiSelectString('categoryTypeEnums', AppSettings.ASSETCLASS.toUpperCase(), true),
      DynamicFieldHelper.createFieldMultiSelectString('specialInvestmentInstrumentEnums', 'FINANCIAL_INSTRUMENT', true),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initializeOthers(): void {
    this.dataTypeSubscribe = UDFMetadataHelper.prepareDataTypeFields(this.translateService, this.form, this.configObject,
      this.callParam.excludeUiOrders, this.callParam.uDFMetadataSecurity, this.configDataTypeFields);
    this.configObject.udfDataType.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService,
      UDFDataType);
  }

  protected override getNewOrExistingInstanceBeforeSave(value: {
    [name: string]: any
  }): UDFMetadataSecurity {
    const uDFMetadataSecurity: UDFMetadataSecurity = this.copyFormToPrivateBusinessObject(new UDFMetadataSecurity(),
      this.callParam.uDFMetadataSecurity);
    uDFMetadataSecurity.idUser = this.gps.getIdUser();
    return uDFMetadataSecurity;
  }

  override onHide(event): void {
    this.dataTypeSubscribe && this.dataTypeSubscribe.unsubscribe();
    super.onHide(event);
  }
}
