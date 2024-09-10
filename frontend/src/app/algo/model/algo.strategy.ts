import {Exclude} from 'class-transformer';
import {AlgoTreeName} from '../../entities/view/algo.tree.name';
import {AlgoStrategyImplementationType} from '../../shared/types/algo.strategy.implementation.type';
import {AlgoRuleStrategy} from './algo.rule.strategy';


export class AlgoStrategy extends AlgoRuleStrategy implements AlgoTreeName {

  algoStrategyImplementations: AlgoStrategyImplementationType | string = null;

  @Exclude()
  getNameByLanguage(language: string): string {
    return this['algoStrategyImplementations$'];
  }

}
