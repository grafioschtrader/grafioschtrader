import { requireInitialized } from '../../helper/required-state';
import { requireFormControl } from '../../dynamic-form/models/form-control-access';
import { Component, OnInit, input, inject } from '@angular/core';
import { SimpleEntityEditBase } from '../../edit/simple.entity.edit.base';
import { UDFDataType, UDFMetadataGeneral, UDFMetadataGeneralParam } from '../model/udf.metadata';
import { TranslateModule } from '@ngx-translate/core';
import { HelpIds } from '../../help/help.ids';
import { UDFMetadataGeneralService } from '../service/udf.metadata.general.service';
import { GlobalSessionNames } from '../../global.session.names';
import { AppHelper } from '../../helper/app.helper';
import { UDFMetadataHelper } from './udf.metadata.helper';
import { DynamicFieldHelper } from '../../helper/dynamic.field.helper';
import { TranslateHelper } from '../../helper/translate.helper';
import { FieldConfig } from '../../dynamic-form/models/field.config';
import { SelectOptionsHelper } from '../../helper/select.options.helper';
import { UDFConfig } from '../../login/model/configuration-with-login';
import { BaseSettings } from '../../base.settings';

import { DialogModule } from '@openng/optimus-ui/dialog';
import { DYNAMIC_FORM_IMPORTS } from '../../dynamic-form/dynamic-form.imports';

/**
 * Component for editing UDF metadata definitions for general entity types.
 * Allows users to create or modify custom field definitions that can be applied to various entities
 * like portfolios, watchlists, and transactions. Configures field properties including data type,
 * size, display order, and help text.
 */
@Component({
  selector: 'udf-metadata-general-edit',
  template: `
    <p-dialog
      header="{{ i18nRecord | translate }}"
      [visible]="visibleDialog()"
      [style]="{ width: '500px' }"
      (onShow)="onShow($event)"
      (onHide)="onHide($event)"
      [modal]="true">
      <dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        [translateService]="translateService"
        #form="dynamicForm"
        (submitBt)="submit($event)" />
    </p-dialog>
  `,
  imports: [DialogModule, ...DYNAMIC_FORM_IMPORTS, TranslateModule]
})
export class UDFMetadataGeneralEditComponent extends SimpleEntityEditBase<UDFMetadataGeneral> implements OnInit {
  /** Parameters containing UDF metadata to edit and validation exclusion lists */
  readonly callParam = input.required<UDFMetadataGeneralParam>();

  /** Field configurations for data type and field size inputs */
  private readonly configDataTypeFields: FieldConfig[] = UDFMetadataHelper.createDataTypeFields();

  /** UDF configuration settings loaded from session storage */
  private initializedUdfConfig?: UDFConfig;

  private get udfConfig(): UDFConfig {
    return requireInitialized(this.initializedUdfConfig, 'UDFMetadataGeneralEditComponent.udfConfig');
  }

  private set udfConfig(value: UDFConfig) {
    this.initializedUdfConfig = value;
  }

  /**
   * Creates the UDF metadata general edit component.
   *
   */
  constructor() {
    super(
      HelpIds.HELP_BASEDATA_UDF_METADATA_GENERAL,
      AppHelper.toUpperCaseWithUnderscore(BaseSettings.UDF_METADATA_GENERAL),
      inject(UDFMetadataGeneralService)
    );
  }

  /**
   * Initializes component configuration and form fields.
   * Sets up entity selector, metadata fields, and data type options.
   */
  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 4, this.helpLink.bind(this));
    const stored = sessionStorage.getItem(GlobalSessionNames.UDF_CONFIG);
    if (stored == null) throw new Error('UDF configuration has not been loaded for this session.');
    this.udfConfig = JSON.parse(stored);

    this.config = [
      DynamicFieldHelper.createFieldSelectStringHeqF('entity', true),
      ...UDFMetadataHelper.createMetadataBaseFields(this.configDataTypeFields, this.callParam().excludeFieldNames),
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
    this.configObject.entity.valueKeyHtmlOptions = SelectOptionsHelper.translateExistingValueKeyHtmlSelectOptions(
      this.translateService,
      SelectOptionsHelper.createHtmlOptionsFromStringArray(this.udfConfig.udfGeneralSupportedEntities, true),
      false
    );
    UDFMetadataHelper.prepareDataTypeFields(
      this.translateService,
      this.form(),
      this.configObject,
      this.callParam().excludeUiOrders,
      this.callParam().uDFMetadataGeneral,
      this.configDataTypeFields,
      this.untilDialogClosed()
    );
    this.configObject.udfDataType.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(
      this.translateService,
      UDFDataType
    );
    this.initializeInformationEntity();
  }

  /**
   * Initializes and optionally disables the entity selector field.
   * If only one entity type is available or editing existing metadata, the entity field is pre-selected and disabled.
   *
   * @private
   */
  private initializeInformationEntity(): void {
    const options = this.configObject.entity.valueKeyHtmlOptions;
    if (options?.length === 1 || this.callParam().uDFMetadataGeneral) {
      options?.length === 1 && requireFormControl(this.configObject.entity).setValue(options[0].key);
      requireFormControl(this.configObject.entity).disable();
    }
  }

  /**
   * Prepares UDF metadata entity for saving by copying form values and setting user ownership.
   *
   * @param value - Form values to save
   * @returns UDF metadata general entity ready for persistence
   * @protected
   */
  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): UDFMetadataGeneral {
    const uDFMetadataGeneral: UDFMetadataGeneral = this.copyFormToPrivateBusinessObject(
      new UDFMetadataGeneral(),
      this.callParam().uDFMetadataGeneral
    );
    uDFMetadataGeneral.idUser = this.gps.getIdUser();
    return uDFMetadataGeneral;
  }
}
