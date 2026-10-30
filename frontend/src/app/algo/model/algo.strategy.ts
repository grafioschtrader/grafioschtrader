import { Exclude } from 'class-transformer';
import { AlgoTreeName } from '../../entities/view/algo.tree.name';
import { AlgoStrategyImplementationType } from '../../shared/types/algo.strategy.implementation.type';
import { AlgoRuleStrategy } from './algo.rule.strategy';

export class AlgoStrategy extends AlgoRuleStrategy implements AlgoTreeName {
  /** Display translation added by the table translation helper. */
  algoStrategyImplementations$?: string;
  algoStrategyImplementations: AlgoStrategyImplementationType | string | null = null;
  activatable: boolean = true;
  /** Live notifications in the assigned monitoring hierarchy; ignored by replay. */
  alertEnabled = true;
  strategyConfig: string | null = null;

  @Exclude()
  getNameByLanguage(language: string): string | undefined {
    return this['algoStrategyImplementations$'];
  }
}
