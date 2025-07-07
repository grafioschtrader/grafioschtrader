import {FieldConfig} from '../../../dynamic-form/models/field.config';
import {DynamicFieldHelper, VALIDATION_SPECIAL} from '../../../lib/helper/dynamic.field.helper';
import {UDFDataType, UDFMetadata} from '../model/udf.metadata';
import {Subscription} from 'rxjs';
import {SelectOptionsHelper} from '../../../lib/helper/select.options.helper';
import {FormHelper} from '../../../dynamic-form/components/FormHelper';
import {DynamicFormComponent} from '../../../dynamic-form/containers/dynamic-form/dynamic-form.component';
import {dataTypeFieldSizeGroup} from '../../../lib/validator/validator';
import {TranslateService} from '@ngx-translate/core';
import {FieldFormGroup} from '../../../dynamic-form/models/form.group.definition';
import {GlobalSessionNames} from '../../global.session.names';
import {FieldDescriptorInputAndShowExtended} from '../../dynamicfield/field.descriptor.input.and.show';

/**
 * Contains static methods of the definitions for entering the metadata of the user-defined input fields.
 */
export class UDFMetadataHelper {
  public static readonly DATA_TYPE_FIELD_GROUP = 'dataTypeFieldSizeGroup';
  public static readonly DESCRIPTION = 'description';
  public static readonly UDF_DATA_TYPE = 'udfDataType';
  public static readonly FIELD_SIZE = 'fieldSize';
  public static readonly UDF_FIELD_PREFIX = 'f';

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
   * The data type and the field dimensions are validated together and therefore form a group.
   */
  public static createDataTypeFields(): FieldConfig[] {
    return [DynamicFieldHelper.createFieldSelectStringHeqF(this.UDF_DATA_TYPE, true),
      DynamicFieldHelper.createFieldInputStringHeqF(this.FIELD_SIZE, 20, true)];
  }

  public static getFieldDescriptorByEntity(entityName: string): FieldDescriptorInputAndShowExtended[] {
    return JSON.parse(sessionStorage.getItem(GlobalSessionNames.UDF_FORM_DESCRIPTOR_GENERAL)).filter(fd =>
      fd.entity === entityName)
  }

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

  public static removeUDFPropertiesFromObject(objectUDF: any): void {
    const regex = new RegExp(`^${UDFMetadataHelper.UDF_FIELD_PREFIX}\d+$`);
    for (const key in objectUDF) {
      if (regex.test(key)) {
        delete objectUDF[key];
      }
    }
  }

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
