import {Component, Input, OnInit} from '@angular/core';
import {SimpleEntityEditBase} from '../../edit/simple.entity.edit.base';
import {UDFDataType, UDFMetadataGeneral, UDFMetadataGeneralParam} from '../model/udf.metadata';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../service/globalparameter.service';
import {MessageToastService} from '../../message/message.toast.service';
import {HelpIds} from '../../help/help.ids';
import {AppSettings} from '../../app.settings';
import {UDFMetadataGeneralService} from '../service/udf.metadata.general.service';
import {GlobalSessionNames} from '../../global.session.names';
import {AppHelper} from '../../../lib/helper/app.helper';
import {UDFMetadataHelper} from './udf.metadata.helper';
import {DynamicFieldHelper} from '../../../lib/helper/dynamic.field.helper';
import {TranslateHelper} from '../../../helper/translate.helper';
import {FieldConfig} from '../../../dynamic-form/models/field.config';
import {Subscription} from 'rxjs';
import {SelectOptionsHelper} from '../../../lib/helper/select.options.helper';
import {UDFConfig} from '../../login/component/login.component';

/**
 * This can be used to edit the metadata of an information class that has no specific extensions.
 */
@Component({
    selector: 'udf-metadata-general-edit',
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
export class UDFMetadataGeneralEditComponent extends SimpleEntityEditBase<UDFMetadataGeneral> implements OnInit {
  @Input() callParam: UDFMetadataGeneralParam;
  private configDataTypeFields: FieldConfig[];
  private dataTypeSubscribe: Subscription;
  private udfConfig: UDFConfig;

  constructor(translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    uDFMetadataGeneralService: UDFMetadataGeneralService) {
    super(HelpIds.HELP_BASEDATA_UDF_METADATA_GENERAL, AppHelper.toUpperCaseWithUnderscore(AppSettings.UDF_METADATA_GENERAL),
      translateService, gps, messageToastService, uDFMetadataGeneralService);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      4, this.helpLink.bind(this));
    this.configDataTypeFields = UDFMetadataHelper.createDataTypeFields();
    this.udfConfig = JSON.parse(sessionStorage.getItem(GlobalSessionNames.UDF_CONFIG));

    this.config = [DynamicFieldHelper.createFieldSelectStringHeqF('entity', true),
      ...UDFMetadataHelper.createMetadataBaseFields(this.configDataTypeFields, this.callParam.excludeFieldNames),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    this.configObject.entity.valueKeyHtmlOptions = SelectOptionsHelper.translateExistingValueKeyHtmlSelectOptions(this.translateService,
      SelectOptionsHelper.createHtmlOptionsFromStringArray(this.udfConfig.udfGeneralSupportedEntities, true), false);
    this.dataTypeSubscribe = UDFMetadataHelper.prepareDataTypeFields(this.translateService, this.form, this.configObject,
      this.callParam.excludeUiOrders, this.callParam.uDFMetadataGeneral, this.configDataTypeFields);
    this.configObject.udfDataType.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService,
      UDFDataType);
    this.initializeInformationEntity();
  }

  private initializeInformationEntity(): void {
    if (this.configObject.entity.valueKeyHtmlOptions.length == 1 || this.callParam.uDFMetadataGeneral) {
      this.configObject.entity.valueKeyHtmlOptions.length == 1 && this.configObject.entity.formControl
        .setValue(this.configObject.entity.valueKeyHtmlOptions[0].key)
      this.configObject.entity.formControl.disable();
    }
  }

  protected override getNewOrExistingInstanceBeforeSave(value: {
    [name: string]: any
  }): UDFMetadataGeneral {
    const uDFMetadataGeneral: UDFMetadataGeneral = this.copyFormToPrivateBusinessObject(new UDFMetadataGeneral(),
      this.callParam.uDFMetadataGeneral);
    uDFMetadataGeneral.idUser = this.gps.getIdUser();
    return uDFMetadataGeneral;
  }

  override onHide(event): void {
    this.dataTypeSubscribe && this.dataTypeSubscribe.unsubscribe();
    super.onHide(event);
  }

}
