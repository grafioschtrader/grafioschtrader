import {AlgoTop} from '../../entities/algo.top';
import {AlgoAssetclass} from '../../entities/algo.assetclass';
import {AlgoSecurity} from '../../entities/algo.security';
import {AlgoStrategyImplementations} from '../../shared/types/algo.strategy.implementations';
import {InputAndShowDefinitionStrategy} from './input.and.show.definition.strategy';
import {AlgoStrategy} from '../../entities/algo.strategy';
import {FieldDescriptorInputAndShow} from '../../shared/dynamicfield/field.descriptor.input.and.show';

export enum AlgoDialogVisible {
  ALGO_ASSETCLASS = 1,
  ALGO_SECURITY = 2,
  ALGO_STRATEGY = 3
}

export class AlgoStrategyDefinitionForm {
  unusedAlgoStrategyMap: Map<number, AlgoStrategyImplementations[]> = new Map();
  inputAndShowDefinitionMap: Map<AlgoStrategyImplementations, InputAndShowDefinitionStrategy> = new Map();
}

export class AlgoCallParam {
  constructor(public parentObject: AlgoTop | AlgoAssetclass | AlgoSecurity,
              public thisObject: AlgoTop | AlgoAssetclass | AlgoSecurity | AlgoStrategy,
              public algoStrategyDefinitionForm?: AlgoStrategyDefinitionForm) {
  }
}

export class AlgoStrategyParamCall {
  algoStrategy: AlgoStrategy;
  fieldDescriptorShow: FieldDescriptorInputAndShow[];
}
