import {FieldConfig} from '../../dynamic-form/models/field.config';
import {FieldDescriptorInputAndShow} from '../../shared/dynamicfield/field.descriptor.input.and.show';
import {MenuItem} from 'primeng/api';

export class IndicatorDefinitions {
  defMap = new Map<TaIndicators, IndicatorDefinition>();
  idSecuritycurrency: number;

  constructor() {
    this.defMap.set(TaIndicators.SMA, new IndicatorDefinition());
    this.defMap.set(TaIndicators.EMA, new IndicatorDefinition());
  }
}

/**
 * A indicator may have one or more traces
 */
export class IndicatorDefinition {
  shown: boolean;
  menuItem: MenuItem;
  taTraceIndicatorDataList: TaTraceIndicatorData[] = [];
}

export enum TaIndicators {
  SMA,
  EMA
}

export class TaEditReturn {
  constructor(public taIndicators: string, public taDynamicDataModel: any) {
  }
}

export class TaEditParam extends TaEditReturn {
  constructor(taIndicators: string, taDynamicDataModel: any,
              public fieldConfig: FieldConfig[]) {
    super(taIndicators, taDynamicDataModel);
  }
}

export interface TaIndicatorData {
  date: Date;
  value: number;
}

export interface TaTraceIndicatorData {
  taIndicator: TaIndicators;
  traceName: string;
  period: number;
  taIndicatorData: TaIndicatorData[];
  traceIndex: number;
}

export interface TaFormDefinition {
  taFormList: FieldDescriptorInputAndShow[];
  defaultDataModel: any;
}
