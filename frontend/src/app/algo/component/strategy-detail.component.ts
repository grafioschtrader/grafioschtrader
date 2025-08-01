import {Component, Input, OnChanges} from '@angular/core';
import {AlgoStrategyParamCall} from '../model/algo.dialog.visible';
import {SingleRecordConfigBase} from '../../lib/datashowbase/single.record.config.base';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {
  DynamicFormPropertyHelps,
  FieldDescriptorInputAndShow
} from '../../shared/dynamicfield/field.descriptor.input.and.show';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {AppHelper} from '../../lib/helper/app.helper';
import {AppSettings} from '../../shared/app.settings';
import {OptionalParams, TranslateValue} from '../../lib/datashowbase/column.config';
import {AlgoStrategyHelper} from './algo.strategy.helper';
import {DynamicFieldModelHelper} from '../../lib/helper/dynamic.field.model.helper';

/**
 * Displays the parameters of a selected strategy.
 * A strategy can consist of different parameters, therefore the output is created dynamically from a map.
 * Project: Grafioschtrader
 */
@Component({
    selector: 'strategy-detail',
  template: `
    @for (field of fields; track field) {
      <div class="row">
        <div class="col-lg-6 col-md-6 col-sm-6 col-xs-6 showlabel text-end">
          {{field.headerTranslated}}:
        </div>
        <div class="col-lg-6 col-md-6 col-sm-6 col-xs-6 nopadding wrap">
          {{getValueByPath(dynamicModel, field)}}{{field.headerSuffix}}
        </div>
      </div>
    }
  `,
    standalone: false
})
export class StrategyDetailComponent extends SingleRecordConfigBase implements OnChanges {
  @Input() algoStrategyParamCall: AlgoStrategyParamCall;

  dynamicModel: any = {};

  constructor(translateService: TranslateService, gps: GlobalparameterService) {
    super(translateService, gps);
  }

  ngOnChanges() {
    console.log('ngOnInit-AlgoStrategy:', this.algoStrategyParamCall.algoStrategy);
    console.log('ngOnInit-description:', this.algoStrategyParamCall.fieldDescriptorShow);
    this.dynamicModel = DynamicFieldModelHelper.createAndSetValuesInDynamicModel(this.algoStrategyParamCall.algoStrategy.algoStrategyImplementations,
      AlgoStrategyHelper.FIELD_STRATEGY_IMPL,
      this.algoStrategyParamCall.algoStrategy.algoRuleStrategyParamMap,
      this.algoStrategyParamCall.fieldDescriptorShow);
    this.createDynamicOutputFields(this.algoStrategyParamCall.fieldDescriptorShow);
    this.translateHeadersAndColumns();
    this.createTranslatedValueStore([this.dynamicModel]);
  }


  private createDynamicOutputFields(fieldDescriptorInputAndShows: FieldDescriptorInputAndShow[]): void {
    this.fields = [];
    fieldDescriptorInputAndShows.forEach(fDIAS => {
      const optinalParams: OptionalParams = {};
      if (DataType[fDIAS.dataType] === DataType.None) {
        optinalParams.translateValues = TranslateValue.NORMAL;
      }
      if (fDIAS.dynamicFormPropertyHelps
        && (<string[]>fDIAS.dynamicFormPropertyHelps)
          .indexOf(DynamicFormPropertyHelps[DynamicFormPropertyHelps.PERCENTAGE]) >= 0) {
        optinalParams.headerSuffix = '%';
      }
      this.addFieldProperty(DataType[fDIAS.dataType], fDIAS.fieldName,
        AppSettings.PREFIX_ALGO_FIELD + AppHelper.toUpperCaseWithUnderscore(fDIAS.fieldName),
        optinalParams);

    });
  }

}
