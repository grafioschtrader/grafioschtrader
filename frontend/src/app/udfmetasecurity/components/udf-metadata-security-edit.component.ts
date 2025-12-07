import {UDFDataType} from '../../lib/udfmeta/model/udf.metadata';
import {HelpIds} from '../../lib/help/help.ids';
import {AppSettings} from '../../shared/app.settings';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {UDFMetadataSecurityService} from '../service/udf.metadata.security.service';
import {Component, Input, OnInit} from '@angular/core';
import {AppHelper} from '../../lib/helper/app.helper';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {AssetClassTypeSpecInstrument} from './asset.class.type.spec.instrument';
import {SelectOptionsHelper} from '../../lib/helper/select.options.helper';
import {FieldConfig} from '../../lib/dynamic-form/models/field.config';
import {Subscription} from 'rxjs';
import {UDFMetadataHelper} from '../../lib/udfmeta/components/udf.metadata.helper';
import {GlobalparameterGTService} from '../../gtservice/globalparameter.gt.service';
import {UDFMetadataSecurity, UDFMetadataSecurityParam} from '../model/udf.metadata.security';

import {DialogModule} from 'primeng/dialog';
import {DynamicFormModule} from '../../lib/dynamic-form/dynamic-form.module';

/**
 * Component for editing UDF metadata definitions specific to securities.
 * Allows users to create or modify custom field definitions applicable to securities and financial instruments,
 * with filtering by asset class type and special investment instrument. Configures field properties including
 * data type, size, display order, help text, and applicability criteria.
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
    standalone: true,
    imports: [DialogModule, DynamicFormModule, TranslateModule]
})
export class UDFMetadataSecurityEditComponent extends AssetClassTypeSpecInstrument<UDFMetadataSecurity> implements OnInit {
  /** Parameters containing security UDF metadata to edit and validation exclusion lists */
  @Input() callParam: UDFMetadataSecurityParam;

  /** Field configurations for data type and field size inputs */
  private configDataTypeFields: FieldConfig[];

  /** Subscription to data type changes for conditional field size enabling/disabling */
  private dataTypeSubscribe: Subscription;

  /**
   * Creates the UDF metadata security edit component.
   *
   * @param translateService - Translation service for i18n support
   * @param gpsGT - GT-specific global parameter service for asset class mappings
   * @param gps - Global parameter service providing user settings and system configuration
   * @param messageToastService - Service for displaying user notifications
   * @param uDFMetadataSecurityService - Service for security UDF metadata CRUD operations
   */
  constructor(translateService: TranslateService,
    gpsGT: GlobalparameterGTService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    uDFMetadataSecurityService: UDFMetadataSecurityService) {
    super('categoryTypeEnums', gpsGT, 'specialInvestmentInstrumentEnums',
      HelpIds.HELP_BASEDATA_UDF_METADATA_SECURITY, AppHelper.toUpperCaseWithUnderscore(AppSettings.UDF_METADATA_SECURITY),
      translateService, gps, messageToastService, uDFMetadataSecurityService);
  }

  /**
   * Initializes component configuration and form fields.
   * Sets up metadata fields, asset class selector, and special instrument selector.
   */
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

  /**
   * Completes component initialization after asset class mappings are loaded.
   * Sets up data type fields and populates data type options.
   *
   * @protected
   */
  protected override initializeOthers(): void {
    this.dataTypeSubscribe = UDFMetadataHelper.prepareDataTypeFields(this.translateService, this.form, this.configObject,
      this.callParam.excludeUiOrders, this.callParam.uDFMetadataSecurity, this.configDataTypeFields);
    this.configObject.udfDataType.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService,
      UDFDataType);
  }

  /**
   * Prepares security UDF metadata entity for saving by copying form values and setting user ownership.
   *
   * @param value - Form values to save
   * @returns Security UDF metadata entity ready for persistence
   * @protected
   */
  protected override getNewOrExistingInstanceBeforeSave(value: {
    [name: string]: any
  }): UDFMetadataSecurity {
    const uDFMetadataSecurity: UDFMetadataSecurity = this.copyFormToPrivateBusinessObject(new UDFMetadataSecurity(),
      this.callParam.uDFMetadataSecurity);
    uDFMetadataSecurity.idUser = this.gps.getIdUser();
    return uDFMetadataSecurity;
  }

  /**
   * Cleanup handler when dialog is closed.
   * Unsubscribes from data type and category type change listeners to prevent memory leaks.
   *
   * @param event - Dialog hide event
   */
  override onHide(event): void {
    this.dataTypeSubscribe && this.dataTypeSubscribe.unsubscribe();
    super.onHide(event);
  }
}
