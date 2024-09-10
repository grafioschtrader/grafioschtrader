import {AlgoTop} from './algo.top';
import {AlgoAssetclass} from './algo.assetclass';
import {AlgoSecurity} from './algo.security';
import {AlgoStrategyImplementationType} from '../../shared/types/algo.strategy.implementation.type';
import {InputAndShowDefinitionStrategy} from './input.and.show.definition.strategy';
import {AlgoStrategy} from './algo.strategy';
import {FieldDescriptorInputAndShow} from '../../shared/dynamicfield/field.descriptor.input.and.show';
import {Securitycurrency} from '../../entities/securitycurrency';
import {Observable} from 'rxjs';

export enum AlgoDialogVisible {
  ALGO_ASSETCLASS = 1,
  ALGO_SECURITY = 2,
  ALGO_STRATEGY = 3
}

export class AlgoStrategyDefinitionForm {
  // Contains the ID of the corresponding level and the strategies still available.
  unusedAlgoStrategyMap: Map<number, AlgoStrategyImplementationType[]> = new Map();
  inputAndShowDefinitionMap: Map<AlgoStrategyImplementationType, InputAndShowDefinitionStrategy> = new Map();
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
