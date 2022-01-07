import {DataType} from '../../dynamic-form/models/data.type';
import {ColumnConfig, OptionalParams} from './column.config';
import {GlobalparameterService} from '../service/globalparameter.service';
import {TranslateService} from '@ngx-translate/core';
import {ShowRecordConfigBase} from './show.record.config.base';


/**
 * Show a singe record with the same definition as a table.
 */
export abstract class SingleRecordConfigBase extends ShowRecordConfigBase {

  // Otherwise Object can not be used in the template
  Object = Object;

  constructor(translateService: TranslateService, gps: GlobalparameterService) {
    super(translateService, gps);
  }

  _fieldSetGroups: { [fieldSetName: string]: ColumnConfig };

  get fieldSetGroups(): { [fieldSetName: string]: ColumnConfig } {
    this._fieldSetGroups = this._fieldSetGroups || this.group(this.fields, 'fieldsetName');
    return this._fieldSetGroups;
  }

  addFieldProperty(dataType: DataType, field: string, headerKey: string,
                   optionalParams?: OptionalParams): ColumnConfig {
    return this.addColumn(dataType, field, headerKey, true, true, optionalParams);
  }

  addFieldPropertyFeqH(dataType: DataType, field: string, optionalParams?: OptionalParams): ColumnConfig {
    return this.addColumnFeqH(dataType, field, true, true, optionalParams);
  }

  group(list, prop) {
    return list.reduce((grouped, item) => {
      const key = item[prop];
      grouped[key] = grouped[key] || [];
      grouped[key].push(item);
      return grouped;
    }, {});
  }

}

