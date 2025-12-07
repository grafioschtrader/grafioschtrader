import {Component, Input, OnInit} from '@angular/core';
import {SimpleEntityEditBase} from '../../edit/simple.entity.edit.base';
import {UDFDataType, UDFMetadataGeneral, UDFMetadataGeneralParam} from '../model/udf.metadata';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../services/globalparameter.service';
import {MessageToastService} from '../../message/message.toast.service';
import {HelpIds} from '../../help/help.ids';
import {UDFMetadataGeneralService} from '../service/udf.metadata.general.service';
import {GlobalSessionNames} from '../../global.session.names';
import {AppHelper} from '../../helper/app.helper';
import {UDFMetadataHelper} from './udf.metadata.helper';
import {DynamicFieldHelper} from '../../helper/dynamic.field.helper';
import {TranslateHelper} from '../../helper/translate.helper';
import {FieldConfig} from '../../dynamic-form/models/field.config';
import {Subscription} from 'rxjs';
import {SelectOptionsHelper} from '../../helper/select.options.helper';
import {UDFConfig} from '../../login/model/configuration-with-login';
import {BaseSettings} from '../../base.settings';

import {DialogModule} from 'primeng/dialog';
import {DynamicFormModule} from '../../dynamic-form/dynamic-form.module';

/**
 * Component for editing UDF metadata definitions for general entity types.
 * Allows users to create or modify custom field definitions that can be applied to various entities
 * like portfolios, watchlists, and transactions. Configures field properties including data type,
 * size, display order, and help text.
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
    standalone: true,
    imports: [DialogModule, DynamicFormModule, TranslateModule]
})
export class UDFMetadataGeneralEditComponent extends SimpleEntityEditBase<UDFMetadataGeneral> implements OnInit {
  /** Parameters containing UDF metadata to edit and validation exclusion lists */
  @Input() callParam: UDFMetadataGeneralParam;

  /** Field configurations for data type and field size inputs */
  private configDataTypeFields: FieldConfig[];

  /** Subscription to data type changes for conditional field size enabling/disabling */
  private dataTypeSubscribe: Subscription;

  /** UDF configuration settings loaded from session storage */
  private udfConfig: UDFConfig;

  /**
   * Creates the UDF metadata general edit component.
   *
   * @param translateService - Translation service for i18n support
   * @param gps - Global parameter service providing user settings and system configuration
   * @param messageToastService - Service for displaying user notifications
   * @param uDFMetadataGeneralService - Service for UDF metadata CRUD operations
   */
  constructor(translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    uDFMetadataGeneralService: UDFMetadataGeneralService) {
    super(HelpIds.HELP_BASEDATA_UDF_METADATA_GENERAL, AppHelper.toUpperCaseWithUnderscore(BaseSettings.UDF_METADATA_GENERAL),
      translateService, gps, messageToastService, uDFMetadataGeneralService);
  }

  /**
   * Initializes component configuration and form fields.
   * Sets up entity selector, metadata fields, and data type options.
   */
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

  /**
   * Initializes form with entity options, data types, and existing metadata values.
   * Sets up dynamic field behavior and conditionally disables entity selector.
   *
   * @protected
   */
  protected override initialize(): void {
    this.configObject.entity.valueKeyHtmlOptions = SelectOptionsHelper.translateExistingValueKeyHtmlSelectOptions(this.translateService,
      SelectOptionsHelper.createHtmlOptionsFromStringArray(this.udfConfig.udfGeneralSupportedEntities, true), false);
    this.dataTypeSubscribe = UDFMetadataHelper.prepareDataTypeFields(this.translateService, this.form, this.configObject,
      this.callParam.excludeUiOrders, this.callParam.uDFMetadataGeneral, this.configDataTypeFields);
    this.configObject.udfDataType.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService,
      UDFDataType);
    this.initializeInformationEntity();
  }

  /**
   * Initializes and optionally disables the entity selector field.
   * If only one entity type is available or editing existing metadata, the entity field is pre-selected and disabled.
   *
   * @private
   */
  private initializeInformationEntity(): void {
    if (this.configObject.entity.valueKeyHtmlOptions.length == 1 || this.callParam.uDFMetadataGeneral) {
      this.configObject.entity.valueKeyHtmlOptions.length == 1 && this.configObject.entity.formControl
        .setValue(this.configObject.entity.valueKeyHtmlOptions[0].key)
      this.configObject.entity.formControl.disable();
    }
  }

  /**
   * Prepares UDF metadata entity for saving by copying form values and setting user ownership.
   *
   * @param value - Form values to save
   * @returns UDF metadata general entity ready for persistence
   * @protected
   */
  protected override getNewOrExistingInstanceBeforeSave(value: {
    [name: string]: any
  }): UDFMetadataGeneral {
    const uDFMetadataGeneral: UDFMetadataGeneral = this.copyFormToPrivateBusinessObject(new UDFMetadataGeneral(),
      this.callParam.uDFMetadataGeneral);
    uDFMetadataGeneral.idUser = this.gps.getIdUser();
    return uDFMetadataGeneral;
  }

  /**
   * Cleanup handler when dialog is closed.
   * Unsubscribes from data type change listener to prevent memory leaks.
   *
   * @param event - Dialog hide event
   */
  override onHide(event): void {
    this.dataTypeSubscribe && this.dataTypeSubscribe.unsubscribe();
    super.onHide(event);
  }

}
