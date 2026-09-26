import { AlgoTop } from './algo.top';
import { AlgoAssetclass } from './algo.assetclass';
import { AlgoSecurity } from './algo.security';
import { AlgoStrategyImplementationType } from '../../shared/types/algo.strategy.implementation.type';
import { InputAndShowDefinitionStrategy } from './input.and.show.definition.strategy';
import { AlgoStrategy } from './algo.strategy';
import {
  ClassDescriptorInputAndShow,
  FieldDescriptorInputAndShow
} from '../../lib/dynamicfield/field.descriptor.input.and.show';

export enum AlgoDialogVisible {
  ALGO_ASSETCLASS = 1,
  ALGO_SECURITY = 2,
  ALGO_STRATEGY = 3,
  ALGO_ADD_INSTRUMENT = 4
}

export class AlgoStrategyDefinitionForm {
  // Contains the ID of the corresponding level and the strategies still available.
  unusedAlgoStrategyMap: Map<number, AlgoStrategyImplementationType[]> = new Map();
  inputAndShowDefinitionMap: Map<AlgoStrategyImplementationType, InputAndShowDefinitionStrategy> = new Map();
}

export class AlgoCallParam {
  formDefinition?: ClassDescriptorInputAndShow;
  /**
   * Set by the asset class dialog when the user asked to add instruments to a custom category through the security
   * search. The flag is not persisted; it only tells the hierarchy view to open the search dialog after the save.
   */
  addInstrumentsBySearch?: boolean;
  constructor(
    public parentObject: AlgoTop | AlgoAssetclass | AlgoSecurity,
    public thisObject: AlgoTop | AlgoAssetclass | AlgoSecurity | AlgoStrategy,
    public algoStrategyDefinitionForm?: AlgoStrategyDefinitionForm,
    public idWatchlist?: number,
    public referenceDate?: Date | string
  ) {}
}

export class AlgoStrategyParamCall {
  algoStrategy: AlgoStrategy;
  fieldDescriptorShow: FieldDescriptorInputAndShow[];
  isComplexStrategy: boolean;
}
