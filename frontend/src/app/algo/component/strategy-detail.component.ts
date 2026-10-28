import { Component, input, computed, effect, untracked } from '@angular/core';

import { AlgoStrategyParamCall } from '../model/algo.dialog.visible';
import { SingleRecordConfigBase } from '../../lib/datashowbase/single.record.config.base';
import { TranslateService } from '@ngx-translate/core';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import {
  DynamicFormPropertyHelps,
  FieldDescriptorInputAndShow
} from '../../lib/dynamicfield/field.descriptor.input.and.show';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { AppHelper } from '../../lib/helper/app.helper';
import { AppSettings } from '../../shared/app.settings';
import { OptionalParams, TranslateValue } from '../../lib/datashowbase/column.config';
import { AlgoStrategyHelper } from './algo.strategy.helper';
import { DynamicFieldModelHelper } from '../../lib/helper/dynamic.field.model.helper';
import * as yaml from 'js-yaml';

/**
 * Displays the parameters of a selected strategy.
 * For simple strategies, parameters are shown as key-value pairs from algoRuleStrategyParamMap.
 * For complex strategies, the strategyConfig JSON is converted to YAML for readable display.
 */
@Component({
  selector: 'strategy-detail',
  template: `
    @if (isComplexStrategy()) {
      <pre class="strategy-yaml-display">{{ yamlDisplay() }}</pre>
    } @else {
      @for (field of fields; track field) {
        <div class="row">
          <div class="col-md-6 showlabel text-end">{{ field.headerTranslated }}:</div>
          <div class="col-md-6 nopadding wrap">{{ getValueByPath(dynamicModel(), field) }}{{ field.headerSuffix }}</div>
        </div>
      }
    }
  `,
  styles: [
    `
      .strategy-yaml-display {
        white-space: pre-wrap;
        word-wrap: break-word;
        max-height: 400px;
        overflow-y: auto;
        padding: 8px;
        font-size: 0.85em;
      }
    `
  ],
  imports: []
})
export class StrategyDetailComponent extends SingleRecordConfigBase {
  readonly algoStrategyParamCall = input<AlgoStrategyParamCall>(undefined);

  readonly dynamicModel = computed(() => {
    const call = this.algoStrategyParamCall();
    return !call || call.isComplexStrategy
      ? {}
      : DynamicFieldModelHelper.createAndSetValuesInDynamicModel(
          call.algoStrategy.algoStrategyImplementations,
          AlgoStrategyHelper.FIELD_STRATEGY_IMPL,
          call.algoStrategy.algoRuleStrategyParamMap,
          call.fieldDescriptorShow
        );
  });
  readonly isComplexStrategy = computed(() => !!this.algoStrategyParamCall()?.isComplexStrategy);
  readonly yamlDisplay = computed(() => {
    const config = this.algoStrategyParamCall()?.algoStrategy.strategyConfig;
    if (!config) return '';
    try {
      return yaml.dump(JSON.parse(config), { lineWidth: 120, noRefs: true });
    } catch {
      return config;
    }
  });

  constructor(translateService: TranslateService, gps: GlobalparameterService) {
    super(translateService, gps);

    effect(() => {
      this.algoStrategyParamCall();
      untracked(() => {
        const call = this.algoStrategyParamCall();
        if (call && !call.isComplexStrategy) this.displaySimpleStrategy();
      });
    });
  }

  private displaySimpleStrategy(): void {
    this.createDynamicOutputFields(this.algoStrategyParamCall().fieldDescriptorShow);
    this.translateHeadersAndColumns();
    this.createTranslatedValueStore([this.dynamicModel()]);
  }

  private createDynamicOutputFields(fieldDescriptorInputAndShows: FieldDescriptorInputAndShow[]): void {
    this.fields = [];
    fieldDescriptorInputAndShows.forEach((fDIAS) => {
      const optinalParams: OptionalParams = {};
      if (DataType[fDIAS.dataType] === DataType.None) {
        optinalParams.translateValues = TranslateValue.NORMAL;
      }
      if (
        fDIAS.dynamicFormPropertyHelps &&
        (<string[]>fDIAS.dynamicFormPropertyHelps).indexOf(
          DynamicFormPropertyHelps[DynamicFormPropertyHelps.PERCENTAGE]
        ) >= 0
      ) {
        optinalParams.headerSuffix = '%';
      }
      this.addFieldProperty(
        DataType[fDIAS.dataType],
        fDIAS.fieldName,
        AppSettings.PREFIX_ALGO_FIELD + AppHelper.toUpperCaseWithUnderscore(fDIAS.fieldName),
        optinalParams
      );
    });
  }
}
