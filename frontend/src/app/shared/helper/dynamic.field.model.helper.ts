import {
  ClassDescriptorInputAndShow,
  ConstraintValidatorType,
  DynamicFormPropertyHelps,
  FieldDescriptorInputAndShow,
  ReplaceFieldWithGroup
} from '../dynamicfield/field.descriptor.input.and.show';
import {FieldConfig} from '../../dynamic-form/models/field.config';
import {AppHelper} from './app.helper';
import {DataType} from '../../dynamic-form/models/data.type';
import {BaseParam} from '../../entities/view/base.param';
import {DynamicFieldHelper, FieldOptions, FieldOptionsCc, VALIDATION_SPECIAL} from './dynamic.field.helper';
import {dateRange, gteDate} from '../validator/validator';
import {ErrorMessageRules, RuleEvent} from '../../dynamic-form/error/error.message.rules';
import {FieldFormGroup} from '../../dynamic-form/models/form.group.definition';

/**
 * With this, the input or output forms can be generated automatically from the definition of a class or variables.
 * The field definitions usually come directly from the server, thus avoiding the possible duplication of programming out models.
 */
export class DynamicFieldModelHelper {

  public static createFieldsFromClassDescriptorInputAndShow(cdias: ClassDescriptorInputAndShow, labelPreffix: string,
                                                            addSubmitButton = false, submitText?: string): FieldFormGroup[] {
    let config: FieldFormGroup[];
    if (cdias?.constraintValidatorMap) {
      let counter = 0;
      for (const [key, value] of Object.entries(cdias.constraintValidatorMap)) {
        counter++;
        switch (ConstraintValidatorType[key]) {
          case ConstraintValidatorType.DateRange:
            const fdDate1 = cdias.fieldDescriptorInputAndShows.find(f => f.fieldName === value.startField);
            const fdDate2 = cdias.fieldDescriptorInputAndShows.find(f => f.fieldName === value.endField);
            const fieldConfigs = this.createConfigFieldsFromDescriptor([fdDate1, fdDate2], labelPreffix, addSubmitButton, submitText);
            const fieldFormGroup: FieldFormGroup = {formGroupName: 'dateRange' + counter, fieldConfig: fieldConfigs };
            fieldFormGroup.validation = [dateRange(fdDate1.fieldName, fdDate2.fieldName, fdDate2.fieldName)];

            fieldFormGroup.errors=[{
              name: 'dateRange',
              keyi18n: 'dateRange',
              rules: ['dirty']
            }];

            const rfwg = new ReplaceFieldWithGroup(fdDate1.fieldName, fieldFormGroup, fdDate2.fieldName);
            config = this.ccFieldsFromDescriptorWithGroup(cdias.fieldDescriptorInputAndShows, labelPreffix, addSubmitButton,
              rfwg, submitText);
            break;
        }
      }
      return config;
    } else {
      return cdias? this.ccFieldsFromDescriptorWithGroup(cdias.fieldDescriptorInputAndShows, labelPreffix, addSubmitButton, null, submitText): [];
    }
  }

  public static ccWithFieldsFromDescriptorHeqF(fieldName: string, fieldDescriptorInputAndShows:
    FieldDescriptorInputAndShow[], fieldOptionsCc?: FieldOptionsCc): FieldConfig {
    return this.ccWithFieldsFromDescriptor(fieldName, AppHelper.convertPropertyForLabelOrHeaderKey(fieldName),
      fieldDescriptorInputAndShows, fieldOptionsCc);
  }

  public static ccWithFieldsFromDescriptor(fieldName: string, labelKey: string, fieldDescriptorInputAndShows:
    FieldDescriptorInputAndShow[], fieldOptionsCc?: FieldOptionsCc): FieldConfig {
    const fd = fieldDescriptorInputAndShows.filter(fdias => fdias.fieldName === fieldName)[0];
    return this.createConfigFieldFromDescriptor(fd, null, labelKey, fieldOptionsCc);
  }

  public static createConfigFieldsFromDescriptor(fieldDescriptorInputAndShows: FieldDescriptorInputAndShow[],
                                                 labelPreffix: string, addSubmitButton = false, submitText?: string): FieldConfig[] {
    return <FieldConfig[]>this.ccFieldsFromDescriptorWithGroup(fieldDescriptorInputAndShows, labelPreffix, addSubmitButton, null, submitText);
  }

  public static ccFieldsFromDescriptorWithGroup(fieldDescriptorInputAndShows: FieldDescriptorInputAndShow[],
                                                labelPreffix: string, addSubmitButton = false,
                                                rpg: ReplaceFieldWithGroup, submitText?: string): FieldFormGroup[] {
    const fieldConfigs: FieldFormGroup[] = [];

    fieldDescriptorInputAndShows.forEach(fd => {
      if (rpg && (fd.fieldName === rpg.replaceFieldName || fd.fieldName === rpg.removeFieldName)) {
        if (fd.fieldName === rpg.replaceFieldName) {
          fieldConfigs.push(rpg.fieldFormGroup);
        }
      } else {
        const fieldConfig: FieldConfig = this.createConfigFieldFromDescriptor(fd, labelPreffix, null);
        if (fieldConfig) {
          fieldConfigs.push(fieldConfig);
        }
      }
    });

    if (addSubmitButton) {
      fieldConfigs.push(DynamicFieldHelper.createSubmitButton(submitText ? submitText : undefined));
    }
    return fieldConfigs;
  }

  private static createConfigFieldFromDescriptor(fd: FieldDescriptorInputAndShow, labelPreffix: string,
                                                 labelKey: string, fieldOptionsCc?: FieldOptionsCc): FieldConfig {
    let fieldConfig: FieldConfig;
    const targetField = fieldOptionsCc && fieldOptionsCc.targetField ? fieldOptionsCc.targetField : fd.fieldName;
    labelKey = labelKey ? labelKey : labelPreffix + AppHelper.convertPropertyNameToUppercase(fd.fieldName);

    switch (DataType[fd.dataType]) {
      case DataType.String:
        fieldConfig = this.createStringInputFromDescriptor(fd, labelKey, targetField, fieldOptionsCc);
        break;
      case DataType.Numeric:
      case DataType.NumericInteger:
        fieldConfig = DynamicFieldHelper.createFieldMinMaxNumber(DataType[fd.dataType], targetField,
          labelKey, fd.required, fd.min, fd.max,
          {fieldSuffix: DynamicFieldModelHelper.getFieldPercentageSuffix(fd)});
        break;
      case DataType.DateString:
      case DataType.DateNumeric:
      case DataType.DateTimeNumeric:
      case DataType.DateTimeString:
      case DataType.DateStringShortUS:
        fieldConfig = DynamicFieldHelper.createFieldPcalendar(DataType[fd.dataType], targetField, labelKey, fd.required);
        fieldConfig.defaultValue = new Date();
        if (fd.dynamicFormPropertyHelps
          && DynamicFormPropertyHelps[fd.dynamicFormPropertyHelps[0]] === DynamicFormPropertyHelps.DATE_FUTURE) {
          fieldConfig.calendarConfig = {minDate: fieldConfig.defaultValue};
          fieldConfig.validation = fd.required ? [...fieldConfig.validation, gteDate(fieldConfig.defaultValue)]
            : [gteDate(fieldConfig.defaultValue)];
          const emr: ErrorMessageRules = {
            name: 'gteDate',
            keyi18n: 'gteDate',
            param1: fieldConfig.defaultValue,
            rules: ['dirty']
          };
          fieldConfig.errors = fd.required ? [...fieldConfig.errors, emr] : [emr];
        }
        break;

    }
    return fieldConfig;
  }

  private static createStringInputFromDescriptor(fd: FieldDescriptorInputAndShow, labelKey: string, targetField: string,
                                                 fieldOptionsCc?: FieldOptionsCc): FieldConfig {
    let fieldConfig: FieldConfig;
    const fieldOptions: FieldOptions = Object.assign({}, fieldOptionsCc, {minLength: fd.min});
    if (fd.dynamicFormPropertyHelps) {
      switch (DynamicFormPropertyHelps[fd.dynamicFormPropertyHelps[0]]) {
        case DynamicFormPropertyHelps.EMAIL:
          fieldConfig = DynamicFieldHelper.createFieldDAInputStringVSHeqF(DataType.Email, targetField, fd.max, fd.required,
            [VALIDATION_SPECIAL.EMail], fieldOptions);
          break;
        case DynamicFormPropertyHelps.PASSWORD:
          fieldConfig = DynamicFieldHelper.createFieldDAInputStringHeqF(DataType.Password, targetField, fd.max, fd.required,
            fieldOptions);
          break;
        case DynamicFormPropertyHelps.SELECT_OPTIONS:
          fieldOptions.inputWidth = fd.max;
          fieldConfig = DynamicFieldHelper.createFieldSelectStringHeqF(targetField, fd.required,
            fieldOptions);
          break;

        default:

      }
    } else {
      fieldConfig = DynamicFieldHelper.createFieldInputString(targetField, labelKey, fd.max, fd.required,
        fieldOptions);
    }
    return fieldConfig;
  }


  public static createAndSetValuesInDynamicModel(e: any,
                                                 targetSelectionField: string,
                                                 paramMap: Map<string, BaseParam> | { [key: string]: BaseParam },
                                                 fieldDescriptorInputAndShows: FieldDescriptorInputAndShow[],
                                                 addStrategyImplField = false): any {
    const dynamicModel: any = {};
    if (addStrategyImplField) {
      dynamicModel[targetSelectionField] = e;
    }
    return DynamicFieldModelHelper.setValuesOfMapModelToDynamicModel(fieldDescriptorInputAndShows,
      paramMap, dynamicModel);

  }


  public static setValuesOfMapModelToDynamicModel(fieldDescriptorInputAndShows: FieldDescriptorInputAndShow[],
                                                  paramMap: Map<string, BaseParam> | { [key: string]: BaseParam },
                                                  dynamicModel: any = {}): any {
    fieldDescriptorInputAndShows.forEach(fieldDescriptorInputAndShow => {
      console.log('param', paramMap[fieldDescriptorInputAndShow.fieldName].paramValue);
      let value = paramMap[fieldDescriptorInputAndShow.fieldName].paramValue;
      switch (DataType[fieldDescriptorInputAndShow.dataType]) {
        case DataType.Numeric:
        case DataType.NumericInteger:
          value = Number(value);
          break;
        default:
        // Nothing
      }
      dynamicModel[fieldDescriptorInputAndShow.fieldName] = value;
    });
    return dynamicModel;
  }

  public static getFieldPercentageSuffix(fDIAS: FieldDescriptorInputAndShow): string {
    if (fDIAS.dynamicFormPropertyHelps
      && (<string[]>fDIAS.dynamicFormPropertyHelps)
        .indexOf(DynamicFormPropertyHelps[DynamicFormPropertyHelps.PERCENTAGE]) >= 0) {
      return '%';
    }
    return null;
  }

  public static isDataModelEqual(model1: any, model2: any, fDIASs: FieldDescriptorInputAndShow[]) {
    if (model1 && model2) {
      for (const fDIAS of fDIASs) {
        if (model1[fDIAS.fieldName] !== model2[fDIAS.fieldName]) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

}
