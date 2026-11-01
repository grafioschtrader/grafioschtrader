import { Helper } from '../../helper/helper';
import { DataType } from './data.type';
import { FieldConfig } from './field.config';
import { requireFieldName } from './form-control-access';
import { InputType } from './input.type';
import { ValueKeyHtmlSelectOptions } from './value.key.html.select.options';

/**
 * Determines the value a field shows for a business object. Shared by the reactive and the signal dynamic form, so
 * both read business objects identically.
 *
 * A `dataproperty` path is read instead of the field name. Calendar days are parsed as local dates, a zero of an
 * input-number with `treatZeroAsNull` becomes empty, and an input with `valueKeyHtmlOptions` shows the mapped value of
 * the key. A field the object does not have keeps its default value.
 *
 * @param config - Configuration of a data field
 * @param sourceObject - Business object to read from
 * @returns The value to show in the field
 */
export function businessObjectFieldValue(config: FieldConfig, sourceObject: any): any {
  const field = requireFieldName(config);
  let value = config.defaultValue;

  if (config.dataproperty) {
    // field can not be used to access the input value
    value = Helper.getValueByPath(sourceObject, config.dataproperty);
  } else if (field in sourceObject) {
    if (
      config.dataType === DataType.DateNumeric ||
      config.dataType === DataType.DateTimeNumeric ||
      config.dataType === DataType.DateString
    ) {
      if (sourceObject[field]) {
        value = Helper.parseDateOnlyAsLocal(sourceObject[field]);
      }
    } else if (
      config.inputType === InputType.InputNumber &&
      sourceObject[field] === 0 &&
      config.inputNumberSettings?.treatZeroAsNull
    ) {
      value = null;
    } else {
      value = sourceObject[field];
    }
  }
  if (config.inputType === InputType.Input && config.valueKeyHtmlOptions) {
    // Mapping value to a shown value, this mapping is happened with valueKeyHtmlOptions
    let valueKeyHtmlOption: ValueKeyHtmlSelectOptions | undefined;
    if (!value) {
      valueKeyHtmlOption = config.valueKeyHtmlOptions.find((valueKeyHtmlOptions) => '' === valueKeyHtmlOptions.key);
    } else {
      valueKeyHtmlOption = config.valueKeyHtmlOptions.find((valueKeyHtmlOptions) => value === valueKeyHtmlOptions.key);
    }
    value = valueKeyHtmlOption?.value ?? value;
  }
  return value;
}
