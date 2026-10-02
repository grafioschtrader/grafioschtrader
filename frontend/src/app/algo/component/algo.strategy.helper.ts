import {
  ClassDescriptorInputAndShow,
  FieldDescriptorInputAndShow
} from '../../lib/dynamicfield/field.descriptor.input.and.show';
import { AlgoTopAssetSecurity } from '../model/algo.top.asset.security';
import { InputAndShowDefinitionStrategy } from '../model/input.and.show.definition.strategy';
import { AlgoTop } from '../model/algo.top';
import { AlgoAssetclass } from '../model/algo.assetclass';

/**
 * Project: Grafioschtrader
 */
export class AlgoStrategyHelper {
  public static readonly FIELD_STRATEGY_IMPL = 'algoStrategyImplementations';

  /**
   * Selects the form definition of the level the strategy is attached to.
   *
   * @param algoTopAssetSecurity the node of the hierarchy that holds the strategy
   * @param inputAndShowDefinition the form definitions of all levels
   * @returns the fields and cross-field constraints of that level
   */
  public static getFormDefinitionByLevel<T extends AlgoTopAssetSecurity>(
    algoTopAssetSecurity: T,
    inputAndShowDefinition: InputAndShowDefinitionStrategy
  ): ClassDescriptorInputAndShow {
    if (algoTopAssetSecurity instanceof AlgoTop) {
      return inputAndShowDefinition.topFormDefinition;
    } else if (algoTopAssetSecurity instanceof AlgoAssetclass) {
      return inputAndShowDefinition.assetclassFormDefinition;
    } else {
      return inputAndShowDefinition.securityFormDefinition;
    }
  }

  public static getFieldDescriptorInputAndShowByLevel<T extends AlgoTopAssetSecurity>(
    algoTopAssetSecurity: T,
    inputAndShowDefinition: InputAndShowDefinitionStrategy
  ): FieldDescriptorInputAndShow[] {
    return this.getFormDefinitionByLevel(algoTopAssetSecurity, inputAndShowDefinition).fieldDescriptorInputAndShows;
  }
}
