import {FieldDescriptorInputAndShow} from '../../shared/dynamicfield/field.descriptor.input.and.show';
import {AlgoTopAssetSecurity} from '../model/algo.top.asset.security';
import {InputAndShowDefinitionStrategy} from '../model/input.and.show.definition.strategy';
import {AlgoTop} from '../model/algo.top';
import {AlgoAssetclass} from '../model/algo.assetclass';

export class AlgoStrategyHelper {

  public static readonly FIELD_STRATEGY_IMPL = 'algoStrategyImplementations';


  public static getFieldDescriptorInputAndShowByLevel<T extends AlgoTopAssetSecurity>(algoTopAssetSecurity: T,
                                                                                      inputAndShowDefinition: InputAndShowDefinitionStrategy): FieldDescriptorInputAndShow[] {
    if (algoTopAssetSecurity instanceof AlgoTop) {
      return inputAndShowDefinition.topFormDefintionList;
    } else if (algoTopAssetSecurity instanceof AlgoAssetclass) {
      return inputAndShowDefinition.assetclassFormDefintionList;
    } else {
      return inputAndShowDefinition.securityFormDefinitionList;
    }
  }
}
