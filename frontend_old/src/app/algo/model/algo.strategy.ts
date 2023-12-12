import {Exclude} from 'class-transformer';
import {AlgoTreeName} from '../../entities/view/algo.tree.name';
import {AlgoStrategyImplementations} from '../../shared/types/algo.strategy.implementations';
import {AlgoRuleStrategy} from './algo.rule.strategy';


export class AlgoStrategy extends AlgoRuleStrategy implements AlgoTreeName {

  algoStrategyImplementations: AlgoStrategyImplementations | string = null;

  @Exclude()
  getNameByLanguage(language: string): string {
    return this['algoStrategyImplementations$'];
  }

}
