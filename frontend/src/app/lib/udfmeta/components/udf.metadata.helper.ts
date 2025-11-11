import {FieldConfig} from '../../dynamic-form/models/field.config';
import {DynamicFieldHelper, VALIDATION_SPECIAL} from '../../helper/dynamic.field.helper';
import {UDFDataType, UDFMetadata} from '../model/udf.metadata';
import {Subscription} from 'rxjs';
import {SelectOptionsHelper} from '../../helper/select.options.helper';
import {FormHelper} from '../../dynamic-form/components/FormHelper';
import {DynamicFormComponent} from '../../dynamic-form/containers/dynamic-form/dynamic-form.component';
import {dataTypeFieldSizeGroup} from '../../validator/validator';
import {TranslateService} from '@ngx-translate/core';
import {FieldFormGroup} from '../../dynamic-form/models/form.group.definition';
import {GlobalSessionNames} from '../../global.session.names';
import {FieldDescriptorInputAndShowExtended} from '../../dynamicfield/field.descriptor.input.and.show';

/**
 * Utility class providing static helper methods for UDF metadata management and dynamic form generation.
 * Contains methods for creating form field configurations, validating UDF metadata, and managing UDF field descriptors.
 * This class centralizes common UDF-related operations used across metadata editing components.
 */
export class UDFMetadataHelper {
  /** Form group name for data type and field size validation group */
  public static readonly DATA_TYPE_FIELD_GROUP = 'dataTypeFieldSizeGroup';

  /** Field name for the UDF description/label */
  public static readonly DESCRIPTION = 'description';

  /** Field name for the UDF data type selector */
  public static readonly UDF_DATA_TYPE = 'udfDataType';

  /** Field name for the field size specification */
  public static readonly FIELD_SIZE = 'fieldSize';

  /** Prefix used for UDF field names in dynamic forms (e.g., 'f1', 'f2') */
  public static readonly UDF_FIELD_PREFIX = 'f';

  /**
   * Creates the base field configurations for UDF metadata editing forms.
   * Generates form fields for UI order, description, help text, and data type/size grouped validation.
   *
   * @param configDataTypeFields - Pre-configured data type and field size field configurations
   * @param excludeFileNames - List of existing field names to exclude from validation (prevents duplicates)
   * @returns Array of field configurations for metadata editing
   * @static
   */
  public static createMetadataBaseFields(configDataTypeFields: FieldConfig[], excludeFileNames: string[]): FieldConfig[] {
    const fdTypeSizeGroup: FieldFormGroup = {
      formGroupName: UDFMetadataHelper.DATA_TYPE_FIELD_GROUP,
      fieldConfig: configDataTypeFields
    };
    fdTypeSizeGroup.validation = [dataTypeFieldSizeGroup(configDataTypeFields[0].field, configDataTypeFields[1].field, configDataTypeFields[1].field)];
    fdTypeSizeGroup.errors = [{name: 'dataTypeFieldSizeGroup', keyi18n: 'dataTypeFieldSizeGroup', rules: ['dirty']}];
    return <FieldConfig[]>[DynamicFieldHelper.createFieldSelectNumberHeqF('uiOrder', true),
      DynamicFieldHelper.createFieldInputStringVSParam(this.DESCRIPTION, 'FIELD_DESCRIPTION', 24, true,
        VALIDATION_SPECIAL.NOT_CONTAIN_STRING_IN_LIST, excludeFileNames),
      DynamicFieldHelper.createFieldTextareaInputString('descriptionHelp', 'FIELD_DESCRIPTION_HELP', 80, false),
      fdTypeSizeGroup];
  }

  /**
   * Creates field configurations for data type and field size selection.
   * These fields are validated together as a group since field size requirements depend on data type.
   *
   * @returns Array containing data type select field and field size input field configurations
   * @static
   */
  public static createDataTypeFields(): FieldConfig[] {
    return [DynamicFieldHelper.createFieldSelectStringHeqF(this.UDF_DATA_TYPE, true),
      DynamicFieldHelper.createFieldInputStringHeqF(this.FIELD_SIZE, 20, true)];
  }

  /**
   * Retrieves UDF field descriptors for a specific entity from session storage.
   * Filters cached descriptors to return only those applicable to the specified entity type.
   *
   * @param entityName - Entity type name to filter descriptors by
   * @returns Array of field descriptors for the specified entity
   * @static
   */
  public static getFieldDescriptorByEntity(entityName: string): FieldDescriptorInputAndShowExtended[] {
    return JSON.parse(sessionStorage.getItem(GlobalSessionNames.UDF_FORM_DESCRIPTOR_GENERAL)).filter(fd =>
      fd.entity === entityName)
  }

  /**
   * Prepares and configures data type fields with dynamic behavior and validation.
   * Sets up UI order options, data type change listeners, and conditional field size enabling/disabling.
   * Also transfers existing business object data to the form if provided.
   *
   * @param translateService - Angular translation service for i18n support
   * @param form - Dynamic form component to populate with data
   * @param configObject - Map of field configurations by field name
   * @param excludeUiOrders - List of UI orders to exclude from selection (prevents duplicates)
   * @param businessObject - Existing UDF metadata to edit, or null for new entry
   * @param configDataTypeFields - Data type and field size field configurations
   * @returns Subscription to data type value changes that must be unsubscribed on component destroy
   * @static
   */
  public static prepareDataTypeFields(translateService: TranslateService, form: DynamicFormComponent, configObject: {
      [name: string]: FieldConfig
    },
    excludeUiOrders: number[], businessObject: UDFMetadata, configDataTypeFields: FieldConfig[]): Subscription {
    configObject.uiOrder.valueKeyHtmlOptions = SelectOptionsHelper.createValueKeyHtmlSelectOptionsForNumberRange(1, 99, excludeUiOrders)
    const fieldGroupSub: Subscription = configObject[UDFMetadataHelper.DATA_TYPE_FIELD_GROUP].formControl
      .get([UDFMetadataHelper.UDF_DATA_TYPE]).valueChanges.subscribe(udt =>
        UDFMetadataHelper.enableDisableFileSize(translateService, configObject, configDataTypeFields, udt))
    businessObject && form.transferBusinessObjectToForm(businessObject);
    FormHelper.disableEnableFieldConfigs(!!businessObject, [configObject[UDFMetadataHelper.UDF_DATA_TYPE]]);
    return fieldGroupSub;
  }

  /**
   * Removes all UDF-specific properties from an object.
   * Scans the object for properties matching the UDF field pattern (prefix + number) and deletes them.
   * Used to clean objects before serialization or when UDF data should be excluded.
   *
   * @param objectUDF - Object to remove UDF properties from
   * @static
   */
  public static removeUDFPropertiesFromObject(objectUDF: any): void {
    const regex = new RegExp(`^${UDFMetadataHelper.UDF_FIELD_PREFIX}\d+$`);
    for (const key in objectUDF) {
      if (regex.test(key)) {
        delete objectUDF[key];
      }
    }
  }

  /**
   * Conditionally enables or disables the field size input based on selected data type.
   * Some data types (date, boolean, URL) have fixed sizes and don't require user-specified field size.
   * When such types are selected, the field size control is cleared and disabled.
   *
   * @param translateService - Translation service for i18n (currently unused but kept for consistency)
   * @param configObject - Map of field configurations by field name
   * @param configDataTypeFields - Data type and field size field configurations
   * @param udt - Selected UDF data type as string
   * @private
   * @static
   */
  private static enableDisableFileSize(translateService: TranslateService, configObject: {
      [name: string]: FieldConfig
    }, configDataTypeFields: FieldConfig[],
    udt: string): void {
    const controlFieldSize = configObject[UDFMetadataHelper.DATA_TYPE_FIELD_GROUP].formControl
      .get([UDFMetadataHelper.FIELD_SIZE]);
    if (udt === UDFDataType[UDFDataType.UDF_DateTimeNumeric] || udt === UDFDataType[UDFDataType.UDF_DateString]
      || udt === UDFDataType[UDFDataType.UDF_Boolean] || udt === UDFDataType[UDFDataType.UDF_URLString]) {
      controlFieldSize.setValue(null);
      controlFieldSize.disable();
    } else {
      controlFieldSize.enable();
    }
  }

}
