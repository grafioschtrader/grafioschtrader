import { requireInitialized } from '../../helper/required-state';
import { Component, OnInit, input, inject, computed } from '@angular/core';
import { SimpleEntityEditBase } from '../../edit/simple.entity.edit.base';
import { UDFDataType, UDFMetadataGeneral, UDFMetadataGeneralParam } from '../model/udf.metadata';
import { TranslateModule } from '@ngx-translate/core';
import { HelpIds } from '../../help/help.ids';
import { UDFMetadataGeneralService } from '../service/udf.metadata.general.service';
import { GlobalSessionNames } from '../../global.session.names';
import { AppHelper } from '../../helper/app.helper';
import { UDFMetadataHelper, UDFDataTypeModel } from './udf.metadata.helper';
import { DynamicFieldHelper } from '../../helper/dynamic.field.helper';
import { TranslateHelper } from '../../helper/translate.helper';
import { FieldConfig } from '../../dynamic-form/models/field.config';
import { SelectOptionsHelper } from '../../helper/select.options.helper';
import { UDFConfig } from '../../login/model/configuration-with-login';
import { BaseSettings } from '../../base.settings';

import { DialogModule } from '@openng/optimus-ui/dialog';
import { SignalDynamicFormComponent } from '../../dynamic-form/signal/signal-dynamic-form.component';
import { disabled, SchemaPathTree } from '@angular/forms/signals';

interface MetadataFormModel extends UDFDataTypeModel {
  entity: string | null;
}

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
      <signal-dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        [source]="callParam().uDFMetadataGeneral"
        [values]="startValues()"
        [rules]="rules"
        #form="signalDynamicForm"
        (submitBt)="submit($event)" />
    </p-dialog>
  `,
  imports: [DialogModule, SignalDynamicFormComponent, TranslateModule]
})
export class UDFMetadataGeneralEditComponent
  extends SimpleEntityEditBase<UDFMetadataGeneral, SignalDynamicFormComponent<MetadataFormModel>>
  implements OnInit
{
  /** Parameters containing UDF metadata to edit and validation exclusion lists */
  readonly callParam = input.required<UDFMetadataGeneralParam>();

  readonly startValues = computed(() => {
    const entities = this.udfConfig.udfGeneralSupportedEntities;
    return entities.length === 1 ? { entity: entities[0] } : {};
  });

  readonly rules = (p: SchemaPathTree<MetadataFormModel>): void => {
    UDFMetadataHelper.dataTypeRules(p, () => !!this.callParam().uDFMetadataGeneral);
    disabled(p.entity, {
      when: () => !!this.callParam().uDFMetadataGeneral || this.udfConfig.udfGeneralSupportedEntities.length === 1
    });
  };

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
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config, false);
    this.prepareOptions();
  }

  /**
   * Focuses the description after the dialog opens. Options and start values are ready before the form renders.
   *
   * @protected
   */
  protected override initialize(): void {
    this.configObject.description.baseInputComponent?.focus();
  }

  private prepareOptions(): void {
    this.configObject.entity.valueKeyHtmlOptions = SelectOptionsHelper.translateExistingValueKeyHtmlSelectOptions(
      this.translateService,
      SelectOptionsHelper.createHtmlOptionsFromStringArray(this.udfConfig.udfGeneralSupportedEntities, true),
      false
    );
    this.configObject.uiOrder.valueKeyHtmlOptions = SelectOptionsHelper.createValueKeyHtmlSelectOptionsForNumberRange(
      1,
      99,
      this.callParam().excludeUiOrders
    );
    this.configObject.udfDataType.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(
      this.translateService,
      UDFDataType
    );
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
    UDFMetadataHelper.clearFixedFieldSize(uDFMetadataGeneral);
    return uDFMetadataGeneral;
  }
}
