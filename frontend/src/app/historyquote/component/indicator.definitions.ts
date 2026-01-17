import {FieldConfig} from '../../lib/dynamic-form/models/field.config';
import {FieldDescriptorInputAndShow} from '../../lib/dynamicfield/field.descriptor.input.and.show';
import {MenuItem} from 'primeng/api';

/**
 * Registry of indicator definitions for the chart.
 * Each indicator type has a definition that includes whether it's shown, menu items, and trace data.
 */
export class IndicatorDefinitions {
  defMap = new Map<TaIndicators, IndicatorDefinition>();
  idSecuritycurrency: number;

  constructor() {
    this.defMap.set(TaIndicators.SMA, new IndicatorDefinition(false));
    this.defMap.set(TaIndicators.EMA, new IndicatorDefinition(false));
    this.defMap.set(TaIndicators.RSI, new IndicatorDefinition(true));
  }

  /**
   * Check if any oscillator indicator is currently shown.
   * Oscillator indicators (like RSI) require a separate subplot below the main chart.
   */
  hasShownOscillator(): boolean {
    for (const [taIndicator, iDef] of this.defMap) {
      if (iDef.shown && iDef.isOscillator) {
        return true;
      }
    }
    return false;
  }
}

/**
 * A indicator may have one or more traces
 */
export class IndicatorDefinition {
  shown: boolean;
  menuItem: MenuItem;
  taTraceIndicatorDataList: TaTraceIndicatorData[] = [];

  /**
   * Creates an indicator definition.
   *
   * @param isOscillator - If true, this indicator is an oscillator type (like RSI) that should be
   *                       displayed in a separate subplot below the price chart with its own y-axis (0-100).
   *                       If false, it's an overlay indicator (like SMA/EMA) displayed on the price chart.
   */
  constructor(public isOscillator: boolean) {
  }
}

export enum TaIndicators {
  SMA,
  EMA,
  RSI
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
