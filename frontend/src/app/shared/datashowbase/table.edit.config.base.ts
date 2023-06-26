import {TableConfigBase} from './table.config.base';
import {FilterService} from 'primeng/api';
import {UserSettingsService} from '../service/user.settings.service';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../service/globalparameter.service';
import {ColumnConfig, OptionalParams} from './column.config';
import {DataType} from '../../dynamic-form/models/data.type';
import {AppHelper} from '../helper/app.helper';
import {Validators} from '@angular/forms';
import {DynamicFieldHelper} from '../helper/dynamic.field.helper';

export abstract class TableEditConfigBase extends TableConfigBase {
  protected constructor(filterService: FilterService,
                        usersettingsService: UserSettingsService,
                        translateService: TranslateService,
                        gps: GlobalparameterService) {
    super(filterService, usersettingsService, translateService, gps);
  }

  addEditColumnFeqH(dataType: DataType, field: string, required: boolean,
                    optionalParams?: OptionalParams): ColumnConfig {
    return this.addEditColumn(dataType, field, AppHelper.convertPropertyForLabelOrHeaderKey(field), required, optionalParams);
  }

  addEditColumn(dataType: DataType, field: string, headerKey: string, required: boolean,
                optionalParams?: OptionalParams): ColumnConfig {
    const cc: ColumnConfig = this.addColumnToFields(this.fields, dataType, field, headerKey, true, true, optionalParams);
    cc.cec = {validation:  required ? [Validators.required] : null, errors:  required ? [DynamicFieldHelper.RULE_REQUIRED_TOUCHED] : null};

    return cc;
  }


}
