import {AlgoStrategy} from '../../entities/algo.strategy';
import {FieldDescriptorInputAndShow} from '../../shared/dynamicfield/field.descriptor.input.and.show';
import {DataType} from '../../dynamic-form/models/data.type';
import {AlgoTopAssetSecurity} from '../../entities/algo.top.asset.security';
import {InputAndShowDefinitionStrategy} from '../model/input.and.show.definition.strategy';
import {AlgoTop} from '../../entities/algo.top';
import {AlgoAssetclass} from '../../entities/algo.assetclass';

export class AlgoStrategyHelper {

  public static readonly FIELD_STRATEGY_IMPL = 'algoStrategyImplementations';

  public static createAndSetValuesInDynamicModel(algoStrategy: AlgoStrategy,
    fieldDescriptorInputAndShows: FieldDescriptorInputAndShow[],
    addStrategyImplField = false): any {
    const dynamicModel: any = {};
    if (addStrategyImplField) {
      dynamicModel[this.FIELD_STRATEGY_IMPL] = algoStrategy.algoStrategyImplementations;
    }

    fieldDescriptorInputAndShows.forEach(fieldDescriptorInputAndShow => {
      console.log('param', algoStrategy.algoRuleStrategyParamMap[fieldDescriptorInputAndShow.fieldName].paramValue);
      let value = algoStrategy.algoRuleStrategyParamMap[fieldDescriptorInputAndShow.fieldName].paramValue;
      switch (DataType[fieldDescriptorInputAndShow.dataType]) {
        case DataType.Numeric:
        case DataType.NumericInteger:
          value = Number(value);
          break;
        default:
        // Nothing
      }
      dynamicModel[fieldDescriptorInputAndShow.fieldName] = value;

    });
    console.log('dynamicModel:', dynamicModel);
    return dynamicModel;
  }

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
