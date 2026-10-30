import { TableConfigBase } from './table.config.base';
import { EditableColumnConfig, isEditableColumn, OptionalParams } from './column.config';
import { DataType } from '../dynamic-form/models/data.type';
import { AppHelper } from '../helper/app.helper';
import { Validators } from '@angular/forms';
import { DynamicFieldHelper } from '../helper/dynamic.field.helper';

export abstract class TableEditConfigBase extends TableConfigBase {
  addEditColumnFeqH(
    dataType: DataType,
    field: string,
    required: boolean,
    optionalParams?: OptionalParams
  ): EditableColumnConfig {
    return this.addEditColumn(
      dataType,
      field,
      AppHelper.removeSomeStringAndToUpperCaseWithUnderscore(field),
      required,
      optionalParams
    );
  }

  addEditColumn(
    dataType: DataType,
    field: string,
    headerKey: string,
    required: boolean,
    optionalParams?: OptionalParams
  ): EditableColumnConfig {
    const cc = this.addColumnToFields(this.fields, dataType, field, headerKey, true, true, optionalParams);
    const cec = {
      validation: required ? [Validators.required] : null,
      errors: required ? [DynamicFieldHelper.RULE_REQUIRED_TOUCHED] : null
    };
    return Object.assign(cc, { cec });
  }

  protected requireEditColumnByField(field: string): EditableColumnConfig {
    const column = this.getColumnConfigByField(field);
    if (!isEditableColumn(column)) throw new Error(`Column "${field}" has not been configured for inline editing.`);
    return column;
  }
}
